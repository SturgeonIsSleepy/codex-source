#!/usr/bin/env python3
"""Verify non-debuggable playback; explicitly record optional R8 candidate checks."""
from pathlib import Path
import hashlib
import json
import os
import subprocess
import time

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


def verify_release_start(apk):
    # The test runtime shares Kotlin/Compose classes with the target. Exercise the
    # non-debuggable fast build, then launch the separately shrunk release without
    # instrumentation; do not change production shrinking rules to retain test APIs.
    adb('shell', 'am', 'force-stop', 'app.eplayer.music')
    adb('install', '-r', apk)
    adb('logcat', '-c')
    launch = adb('shell', 'am', 'start', '-W', '-n', 'app.eplayer.music/cp.player.MainActivity', capture_output=True, text=True)
    (output / 'release-launch.txt').write_text(launch.stdout + launch.stderr)
    if 'Status: ok' not in launch.stdout:
        raise RuntimeError('Shrunk release did not launch')
    time.sleep(3)
    pid = adb('shell', 'pidof', 'app.eplayer.music', capture_output=True, text=True).stdout.strip()
    if not pid:
        raise RuntimeError('Shrunk release exited after launch')
    with (output / 'release-home.png').open('wb') as screenshot:
        adb('exec-out', 'screencap', '-p', stdout=screenshot)


def main():
    adb('shell', 'wm', 'size', '480x800')
    adb('shell', 'wm', 'density', '160')
    app = unique('*standard*x86_64*fastRelease.apk')
    release_mode = os.environ.get('EPLAYER_CHECK_RELEASE', '1')
    if release_mode not in {'0', '1'}:
        raise RuntimeError('EPLAYER_CHECK_RELEASE must be 0 or 1')
    release = unique('*standard*x86_64*release.apk') if release_mode == '1' else None
    tests = unique('*standard*fastRelease*androidTest*.apk')
    metadata = {'source_commit': os.environ.get('GITHUB_SHA'), 'kind': 'Android emulator; fastRelease instrumentation, not physical-device acceptance',
                'checks': {'instrumentation': 'not_run', 'release_start': 'not_run' if release else 'not_requested'},
                'app': {'name': app.name, 'sha256': hashlib.sha256(app.read_bytes()).hexdigest()},
                'release': {'name': release.name, 'sha256': hashlib.sha256(release.read_bytes()).hexdigest()} if release else None,
                'tests': {'name': tests.name, 'sha256': hashlib.sha256(tests.read_bytes()).hexdigest()},
                'device': {key: adb('shell', 'getprop', key, capture_output=True, text=True).stdout.strip()
                           for key in ['ro.product.model', 'ro.build.version.sdk', 'ro.product.cpu.abi']}}
    (output / 'build.json').write_text(json.dumps(metadata, indent=2))
    try:
        adb('install', '-r', app)
        adb('install', '-r', '-t', tests)
        metadata['checks']['instrumentation'] = 'running'
        result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'timeout_msec', '120000',
                     'app.eplayer.music.test/androidx.test.runner.AndroidJUnitRunner',
                     capture_output=True, text=True, timeout=480)
        (output / 'instrumentation.txt').write_text(result.stdout + result.stderr)
        print(result.stdout)
        if 'FAILURES!!!' in result.stdout or 'INSTRUMENTATION_CODE: -1' not in result.stdout or 'OK (' not in result.stdout:
            metadata['checks']['instrumentation'] = 'failed'
            raise RuntimeError('Instrumentation did not report a complete passing run')
        metadata['checks']['instrumentation'] = 'passed'
        runtime = subprocess.run(['adb', 'logcat', '-d', '-s', 'AndroidRuntime', 'EPlayerTest', 'EPlayerSession', 'FlickPlayer', 'RustEngine', 'RustAudioEngine', 'CPPlayerDebug', 'AudioTrack', 'AAudio', 'AudioManager'], capture_output=True, text=True, check=False)
        (output / 'instrumentation-runtime.txt').write_text(runtime.stdout)
        if release:
            metadata['checks']['release_start'] = 'running'
            verify_release_start(release)
            metadata['checks']['release_start'] = 'passed'
        else:
            (output / 'release-not-run.txt').write_text('R8 build/start not requested for this development branch.\n')
    except subprocess.TimeoutExpired as error:
        metadata['checks']['instrumentation'] = 'timed_out'
        partial = error.stdout or b''
        if isinstance(partial, bytes):
            partial = partial.decode('utf-8', errors='replace')
        (output / 'instrumentation.txt').write_text(partial + '\nDevice checks timed out; no passing result.\n')
        raise
    except Exception:
        for check, status in metadata['checks'].items():
            if status == 'running':
                metadata['checks'][check] = 'failed'
        raise
    finally:
        for check, status in metadata['checks'].items():
            if status == 'running':
                metadata['checks'][check] = 'incomplete'
        (output / 'build.json').write_text(json.dumps(metadata, indent=2))
        for folder in ['visual-check', 'art-check', 'layout-check']:
            subprocess.run(['adb', 'pull', f'/sdcard/Android/data/app.eplayer.music/files/{folder}', str(output / folder)], check=False)
        logs = subprocess.run(['adb', 'logcat', '-d', '-s', 'AndroidRuntime', 'EPlayerTest', 'EPlayerSession', 'FlickPlayer', 'RustEngine', 'RustAudioEngine', 'CPPlayerDebug', 'AudioTrack', 'AAudio', 'AudioManager'], capture_output=True, text=True, check=False)
        (output / 'runtime.txt').write_text(logs.stdout)
        subprocess.run(['adb', 'shell', 'wm', 'size', 'reset'], check=False)
        subprocess.run(['adb', 'shell', 'wm', 'density', 'reset'], check=False)


if __name__ == '__main__':
    main()
