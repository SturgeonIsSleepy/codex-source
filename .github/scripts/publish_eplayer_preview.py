#!/usr/bin/env python3
"""Deliver the immutable, already tested 0.3.0 CI APK; never transport keys/blobs."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import zipfile

REPO = 'SturgeonIsSleepy/codex-source'
SOURCE = 'a62e0fb60a1aa3215cbb924f5204698153cc738f'
RUN = 40222561908
TAG = 'eplayer-v0.3.0'
APK = 'ePlayer-v0.3.0-preview.apk'
SHA = '9106fbbee4154a4c2a4c4b877a4976d364d9e65ba514ff110b6272d69bb49d40'
CERT = '8f89883030d7d64aecb6358b43eddeb8e9cd5ac323f8ab9916af4070186f08dc'
OUT = Path('delivery')
# Keep failed runs, rather than replacing their evidence with the passing run.
EVIDENCE = [12571144547, 12571508933, 12572094416, 12572746847,
            12572803104, 12572734586, 12608478206, 12608754845, 12610258290]


def gh(*args):
    return subprocess.check_output(['gh', *map(str, args)], text=True)


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def verify_apk():
    if digest(OUT / APK) != SHA:
        raise RuntimeError('Exact CI delivery APK checksum mismatch')


def prepare():
    OUT.mkdir(exist_ok=True)
    candidate = Path('delivery-input/standard/release/app-standard-universal-release.apk')
    (OUT / APK).write_bytes(candidate.read_bytes())
    verify_apk()
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        with zipfile.ZipFile(OUT / APK) as archive:
            names = [n for n in archive.namelist() if n.startswith('lib/') and n.endswith('.so')]
            if {n.split('/')[1] for n in names} != {'arm64-v8a', 'armeabi-v7a', 'x86_64'}:
                raise RuntimeError('Unexpected delivery ABI set')
            for name in names:
                destination = root / 'app/src/main/jniLibs' / Path(name).relative_to('lib')
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_bytes(archive.read(name))
        with tarfile.open(OUT / f'{TAG}-native-libs.tar.gz', 'w:gz') as archive:
            archive.add(root / 'app', arcname='app')


def smoke():
    verify_apk()
    spec = importlib.util.spec_from_file_location('checks', '.github/scripts/run_eplayer_device_checks.py')
    checks = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(checks)
    report = {'source': SOURCE, 'ci_run': RUN, 'apk': APK, 'sha256': SHA,
              'certificate_sha256': CERT, 'status': 'incomplete',
              'scope': 'Exact universal APK install and cold-start on API 36 x86_64; not full R8 music or physical-device acceptance'}
    try:
        checks.verify_release_start(OUT / APK)
        report['status'] = 'passed'
    except Exception:
        report['status'] = 'failed'
        raise
    finally:
        checks.output.mkdir(exist_ok=True)
        (checks.output / 'delivery.json').write_text(json.dumps(report, indent=2) + '\n')


def publish():
    if os.environ.get('GITHUB_REPOSITORY') != REPO:
        raise RuntimeError('Unexpected repository')
    verify_apk()
    report = json.loads(Path('device-evidence/delivery.json').read_text())
    if report['status'] != 'passed' or report['sha256'] != SHA:
        raise RuntimeError('Exact delivery check has not passed')
    run = json.loads(gh('api', f'repos/{REPO}/actions/runs/{RUN}'))
    if run['head_sha'] != SOURCE or run['conclusion'] != 'success':
        raise RuntimeError('Source workflow is not the verified passing run')
    index = []
    with zipfile.ZipFile(OUT / f'{TAG}-validation.zip', 'w', zipfile.ZIP_STORED) as archive:
        with tempfile.TemporaryDirectory() as directory:
            for artifact_id in EVIDENCE:
                metadata = json.loads(gh('api', f'repos/{REPO}/actions/artifacts/{artifact_id}'))
                if metadata['expired']:
                    raise RuntimeError(f'Required evidence expired: {artifact_id}')
                path = Path(directory) / f'{artifact_id}.zip'
                with path.open('wb') as stream:
                    subprocess.run(['gh', 'api', f'repos/{REPO}/actions/artifacts/{artifact_id}/zip'], stdout=stream, check=True)
                actual = digest(path)
                expected = metadata.get('digest')
                if expected and expected != 'sha256:' + actual:
                    raise RuntimeError('Evidence archive checksum mismatch')
                archive.write(path, f'history/{path.name}')
                index.append({'artifact_id': artifact_id, 'name': metadata['name'], 'sha256': actual})
        for path in sorted(Path('device-evidence').rglob('*')):
            if path.is_file():
                archive.write(path, path.as_posix())
        archive.writestr('index.json', json.dumps(index, indent=2) + '\n')
    notes = f'''ePlayer 0.3.0 preview · versionCode 40 · Android 9+ / target 36

Source: https://github.com/{REPO}/commit/{SOURCE}
Verified workflow: https://github.com/{REPO}/actions/runs/{RUN}

Dual-engine playback, native duration/seek/end/replay/error recovery, real mute/unmute PCM, background pause/resume and actual notification return passed in ordinary fastRelease instrumentation: 25 passed, 1 credential-dependent login test skipped. Ordinary/internal R8 builds and ordinary R8 cold-start passed. This delivery additionally passed exact universal R8 APK installation/cold-start on API 36 x86_64. Full R8 music acceptance, physical-device audio routing and live authenticated sources remain unverified.

APK is unchanged from the verified CI artifact (three ABIs); no recordings or signing keys are bundled. Certificate SHA256: {CERT}. This temporary CI signing identity differs from 0.1.1 and future CI builds may use another identity. Back up local imports/settings before a required uninstall; seamless upgrades are not promised. Do not overwrite an existing installation with a different certificate.

Native libraries, raw validation logs/screenshots including failed runs, and SHA256SUMS accompany this preview. USB exclusive output is removed; normal Android audio routing remains. Live EQ work on the sound-controls branch is not part of this release.
'''
    notes_path = OUT / f'{TAG}-notes.md'
    notes_path.write_text(notes)
    files = sorted(path for path in OUT.iterdir() if path.is_file() and path.name != 'SHA256SUMS')
    (OUT / 'SHA256SUMS').write_text(''.join(f'{digest(path)}  {path.name}\n' for path in files))
    files.append(OUT / 'SHA256SUMS')
    release = json.loads(gh('api', f'repos/{REPO}/releases/423603768'))
    if release['tag_name'] != TAG or release['target_commitish'] != SOURCE:
        raise RuntimeError('Existing release targets different source')
    existing = {asset['name']: asset for asset in release['assets']}
    if set(existing) - {path.name for path in files}:
        raise RuntimeError('Unexpected assets; preserve existing release')
    for path in files:
        if path.name in existing:
            if existing[path.name].get('digest') != 'sha256:' + digest(path):
                raise RuntimeError('Existing asset differs; never overwrite')
        else:
            if not release['draft']:
                raise RuntimeError('Do not append to an already published release')
            gh('release', 'upload', TAG, '--repo', REPO, str(path))
    gh('release', 'edit', TAG, '--repo', REPO, '--draft=false', '--prerelease', '--notes-file', notes_path)
    print(f'https://github.com/{REPO}/releases/tag/{TAG}')


if __name__ == '__main__':
    {'prepare': prepare, 'smoke': smoke, 'publish': publish}[sys.argv[1]]()
