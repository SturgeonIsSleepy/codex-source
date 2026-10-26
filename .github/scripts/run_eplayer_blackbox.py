#!/usr/bin/env python3
"""Verify an untouched CI R8 APK via a separate, self-targeted platform QA app."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

REPO = 'SturgeonIsSleepy/codex-source'
TARGET = 'app.eplayer.music'
QA = 'app.eplayer.qa'
CONFIG = Path(__file__).resolve().parents[1] / 'eplayer-blackbox.json'


def read_config(path=CONFIG):
    value = json.loads(Path(path).read_text())
    if not isinstance(value.get('source'), str) or not isinstance(value.get('apk_sha256'), str) or not re.fullmatch('[0-9a-f]{40}', value['source']) or not re.fullmatch('[0-9a-f]{64}', value['apk_sha256']):
        raise ValueError('Full source SHA and APK digest are required')
    if type(value.get('run_id')) is not int or value['run_id'] <= 0:
        raise ValueError('A positive CI run ID is required')
    return value


def successful_run(config, run):
    if run.get('head_sha') != config['source'] or run.get('conclusion') != 'success' or run.get('head_branch') != 'main' or run.get('path') != '.github/workflows/eplayer-preview.yml':
        raise ValueError('Candidate must come from the successful main preview workflow')


def candidate():
    config = read_config()
    if os.environ.get('GITHUB_REPOSITORY') != REPO:
        raise ValueError('Unexpected repository')
    run = json.loads(subprocess.check_output(['gh', 'api', f'repos/{REPO}/actions/runs/{config["run_id"]}'], text=True))
    successful_run(config, run)
    with Path(os.environ['GITHUB_OUTPUT']).open('a') as stream:
        stream.write(f'run_id={config["run_id"]}\nartifact=eplayer-preview-{config["source"]}\n')


def disposable_emulator(qemu, model):
    if qemu.strip() != '1' or not model.strip().startswith('sdk_'):
        raise RuntimeError('Fresh emulator required; never clear data on a physical device')


def instrumentation_passed(text):
    return 'OK (2 tests)' in text and 'INSTRUMENTATION_CODE: -1' in text and not any(
        marker in text for marker in ['FAILURES!!!', 'INSTRUMENTATION_FAILED', 'Process crashed'])


def device(app, qa, output):
    config = read_config()
    with app.open('rb') as stream: actual = hashlib.file_digest(stream, 'sha256').hexdigest()
    with qa.open('rb') as stream: qa_digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    if actual != config['apk_sha256']:
        raise RuntimeError('R8 APK checksum mismatch; no install or clearing performed')
    output.mkdir(parents=True, exist_ok=True)
    def adb(*args, **kwargs):
        return subprocess.run(['adb', *map(str, args)], check=True, timeout=kwargs.pop('timeout', 30), **kwargs)
    def shell(*args):
        return adb('shell', *args, capture_output=True, text=True).stdout.strip()
    disposable_emulator(shell('getprop', 'ro.kernel.qemu'), shell('getprop', 'ro.product.model'))
    report = dict(source=config['source'], source_run=config['run_id'], apk_sha256=actual,
        qa_sha256=qa_digest, status='incomplete',
        scope='Untouched universal R8 APK; cross-app UI import and framework MediaSession. System/native selected in UI. Framework position uses its reported update time; no raw PCM or physical audio-quality measurement.',
        device={'model': shell('getprop', 'ro.product.model'), 'sdk': shell('getprop', 'ro.build.version.sdk')})
    try:
        adb('install', '-r', app)
        # Only a freshly identified SDK emulator reaches this operation. Preserve fast-test evidence separately.
        if shell('pm', 'clear', TARGET) != 'Success':
            raise RuntimeError('Could not prepare fresh app data on disposable emulator')
        adb('shell', 'pm', 'grant', TARGET, 'android.permission.POST_NOTIFICATIONS')
        adb('install', '-r', '-t', qa)
        adb('logcat', '-c')
        report['status'] = 'running'
        result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', 'app.eplayer.qa.R8MusicCheck',
            QA + '/android.test.InstrumentationTestRunner', capture_output=True, text=True, timeout=300)
        (output / 'instrumentation.txt').write_text(result.stdout + result.stderr)
        print(result.stdout)
        if not instrumentation_passed(result.stdout):
            report['status'] = 'failed'
            raise RuntimeError('R8 cross-app checks did not report two complete passing tests')
        report['status'] = 'passed'
    except subprocess.TimeoutExpired as error:
        report['status'] = 'timed_out'
        partial = error.stdout or b''
        if isinstance(partial, bytes): partial = partial.decode('utf-8', errors='replace')
        (output / 'instrumentation.txt').write_text(partial + '\nTimed out; no complete passing result.\n')
        raise
    except Exception:
        if report['status'] in {'running', 'incomplete'}: report['status'] = 'failed'
        raise
    finally:
        (output / 'build.json').write_text(json.dumps(report, indent=2) + '\n')
        subprocess.run(['adb', 'pull', f'/sdcard/Android/data/{QA}/files/r8-check', str(output / 'r8-check')], timeout=30, check=False)
        runtime = subprocess.run(['adb', 'logcat', '-d', '-s', 'AndroidRuntime'], capture_output=True, text=True, timeout=30)
        (output / 'runtime.txt').write_text(runtime.stdout)
        for package in [QA, TARGET]: subprocess.run(['adb', 'shell', 'am', 'force-stop', package], timeout=30, check=False)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['candidate', 'device'])
    parser.add_argument('--app', type=Path)
    parser.add_argument('--qa', type=Path)
    parser.add_argument('--output', type=Path, default=Path('r8-evidence'))
    args = parser.parse_args()
    if args.action == 'candidate': candidate()
    elif args.app is None or args.qa is None: parser.error('--app and --qa are required')
    else: device(args.app, args.qa, args.output)
