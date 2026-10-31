#!/usr/bin/env python3
"""Reject mismatched native candidates and record raw build provenance outside Git."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import struct
import subprocess

ABI_HEADERS = {'arm64-v8a': (2, 183), 'armeabi-v7a': (1, 40), 'x86_64': (2, 62)}
REQUIRED_EXPORT = 'Java_cp_player_engine_RustEngine_nativeShutdown'


def collect(native_dir, nm):
    records = []
    common_exports = None
    for abi, expected in ABI_HEADERS.items():
        library = native_dir / abi / 'librust_lib_flick_player.so'
        data = library.read_bytes()
        if len(data) < 20 or data[:4] != b'\x7fELF' or data[5] != 1:
            raise ValueError(f'{abi}: invalid little-endian ELF')
        if (data[4], struct.unpack_from('<H', data, 18)[0]) != expected:
            raise ValueError(f'{abi}: ELF architecture does not match its directory')
        output = subprocess.check_output([str(nm), '--dynamic', '--defined-only', str(library)], text=True)
        exports = sorted(line.split()[-1] for line in output.splitlines() if line.split() and
                         (line.split()[-1].startswith('Java_cp_player_engine_RustEngine_') or
                          line.split()[-1] == 'JNI_OnLoad'))
        if REQUIRED_EXPORT not in exports or 'JNI_OnLoad' not in exports:
            raise ValueError(f'{abi}: missing native output shutdown API; legacy libraries cannot validate this build')
        if common_exports is not None and exports != common_exports:
            raise ValueError(f'{abi}: RustEngine JNI exports differ across ABIs')
        common_exports = exports
        records.append({'abi': abi, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest(),
                        'rust_engine_exports': exports})
    return records


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--native-dir', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    ndk = Path(os.environ['ANDROID_NDK_HOME'])
    nm = ndk / 'toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-nm'
    native_path = 'projects/eplayer/app/src/main/rust'
    subprocess.run(['git', 'diff', '--quiet', 'HEAD', '--', f':(top){native_path}'], check=True)
    records = collect(args.native_dir, nm)
    metadata = {
        'source_commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
        'rust_tree': subprocess.check_output(['git', 'rev-parse', f'HEAD:{native_path}'], text=True).strip(),
        'rustc': subprocess.check_output(['rustc', '--version'], text=True).strip(),
        'profile': 'release', 'android_api': 28, 'representation': 'raw-before-AGP-stripping',
        'libraries': records,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(metadata, indent=2) + '\n')
    print(f'Verified {len(records)} source-built ABIs with the output shutdown API')


if __name__ == '__main__':
    main()
