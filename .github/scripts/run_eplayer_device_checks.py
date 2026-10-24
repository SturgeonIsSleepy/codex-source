#!/usr/bin/env python3
"""Verify the standard release on an ephemeral Android emulator; retain test evidence."""
from pathlib import Path
import hashlib
import json
import os
import subprocess

root = Path('device-apks')
output = Path('device-evidence')
output.mkdir(exist_ok=True)


def unique(pattern):
    matches = list(root.rglob(pattern))
    if len(matches) != 1:
        raise RuntimeError(f'Expected one {pattern}, found {len(matches)}')
    return matches[0]


def adb(*arguments, **kwargs):
    return subprocess.run(['adb', *map(str, arguments)], check=True, **kwargs)


def main():
    adb('shell', 'wm', 'size', '480x800')
    adb('shell', 'wm', 'density', '160')
    app = unique('*standard*x86_64*release.apk')
    tests = unique('*standard*release*androidTest*.apk')
    metadata = {'source_commit': os.environ.get('GITHUB_SHA'), 'kind': 'Android emulator, not physical-device acceptance',
                'app': {'name': app.name, 'sha256': hashlib.sha256(app.read_bytes()).hexdigest()},
                'tests': {'name': tests.name, 'sha256': hashlib.sha256(tests.read_bytes()).hexdigest()},
                'device': {key: adb('shell', 'getprop', key, capture_output=True, text=True).stdout.strip()
                           for key in ['ro.product.model', 'ro.build.version.sdk', 'ro.product.cpu.abi']}}
    (output / 'build.json').write_text(json.dumps(metadata, indent=2))
    adb('install', '-r', app)
    adb('install', '-r', tests)
    try:
        result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'timeout_msec', '120000',
                     'app.eplayer.music.test/androidx.test.runner.AndroidJUnitRunner',
                     capture_output=True, text=True, timeout=480)
        (output / 'instrumentation.txt').write_text(result.stdout + result.stderr)
        print(result.stdout)
        if 'FAILURES!!!' in result.stdout or 'INSTRUMENTATION_CODE: -1' not in result.stdout or 'OK (' not in result.stdout:
            raise RuntimeError('Instrumentation did not report a complete passing run')
    except subprocess.TimeoutExpired as error:
        partial = error.stdout or b''
        if isinstance(partial, bytes):
            partial = partial.decode('utf-8', errors='replace')
        (output / 'instrumentation.txt').write_text(partial + '\nDevice checks timed out; no passing result.\n')
        raise
    finally:
        for folder in ['visual-check', 'art-check', 'layout-check']:
            subprocess.run(['adb', 'pull', f'/sdcard/Android/data/app.eplayer.music/files/{folder}', str(output / folder)], check=False)
        logs = subprocess.run(['adb', 'logcat', '-d', '-s', 'AndroidRuntime', 'EPlayerTest'], capture_output=True, text=True, check=False)
        (output / 'runtime.txt').write_text(logs.stdout)
        subprocess.run(['adb', 'shell', 'wm', 'size', 'reset'], check=False)
        subprocess.run(['adb', 'shell', 'wm', 'density', 'reset'], check=False)


if __name__ == '__main__':
    main()
