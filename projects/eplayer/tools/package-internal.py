#!/usr/bin/env python3
"""Embed a user-supplied source into an internal APK, then align it for signing.

The module and signing key are never uploaded by this tool.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--module', type=Path, required=True)
    parser.add_argument('--build-tools', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    suffix = '.exe' if os.name == 'nt' else ''
    badging = subprocess.check_output([str(args.build_tools / ('aapt2' + suffix)), 'dump', 'badging', str(args.apk)], text=True, encoding='utf-8')
    if "package: name='app.eplayer.music.internal'" not in badging:
        raise ValueError('Only the internal application may contain a bundled source')
    if args.output.resolve() == args.apk.resolve():
        raise ValueError('Use a separate output file')
    with zipfile.ZipFile(args.module) as module:
        manifest = json.loads(module.read('manifest.json'))
        if (manifest.get('id'), manifest.get('version'), manifest.get('type')) != ('netease-cloudmusic-rs', '1.1.0', 'jni'):
            raise ValueError('This build expects the selected NCM 1.1.0 JNI module')
        for abi in ['arm64-v8a', 'armeabi-v7a', 'x86_64']:
            library = module.read(f'lib/{abi}/{manifest["entryPoint"]}')
            if not library.startswith(b'\x7fELF') or b'Java_cp_player_core_provider_JniProvider_nativeCallApi' not in library:
                raise ValueError('Missing or incompatible module ABI: ' + abi)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=args.output.parent) as temporary:
        unsigned = Path(temporary) / 'internal.apk'
        with zipfile.ZipFile(args.apk) as source, zipfile.ZipFile(unsigned, 'w') as target:
            for entry in source.infolist():
                name = entry.filename
                if name == 'assets/bundled-provider.zip' or (name.startswith('META-INF/') and name.upper().endswith(('.RSA', '.DSA', '.EC', '.SF', 'MANIFEST.MF'))):
                    continue
                target.writestr(entry, source.read(entry))
            target.write(args.module, 'assets/bundled-provider.zip', compress_type=zipfile.ZIP_STORED)
        subprocess.run([str(args.build_tools / ('zipalign' + suffix)), '-f', '-P', '16', '4', str(unsigned), str(args.output)], check=True)
    print(json.dumps({'output': str(args.output), 'module_sha256': hashlib.sha256(args.module.read_bytes()).hexdigest(), 'signed': False}))


if __name__ == '__main__':
    main()
