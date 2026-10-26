#!/usr/bin/env python3
"""Publish an immutable verified CI APK from a reviewed delivery manifest; no keys/blobs."""
import hashlib
import importlib.util
import json
import os
import re
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import zipfile

REPO = 'SturgeonIsSleepy/codex-source'
def load_manifest(path):
    value = json.loads(Path(path).read_text())
    for name, pattern in [('version', r'\d+\.\d+\.\d+'), ('source', r'[0-9a-f]{40}'),
                          ('apk_sha256', r'[0-9a-f]{64}'), ('certificate_sha256', r'[0-9a-f]{64}')]:
        if not isinstance(value.get(name), str) or not re.fullmatch(pattern, value[name]):
            raise ValueError('Invalid delivery field: ' + name)
    for name in ['run_id', 'version_code']:
        if type(value.get(name)) is not int or value[name] <= 0:
            raise ValueError('Invalid delivery field: ' + name)
    if value.get('release_id') is not None and (type(value['release_id']) is not int or value['release_id'] <= 0):
        raise ValueError('Invalid release ID')
    if not isinstance(value.get('evidence'), list) or not value['evidence'] or any(type(n) is not int or n <= 0 for n in value['evidence']):
        raise ValueError('Evidence artifact IDs are required')
    if not isinstance(value.get('validation_summary'), str) or not value['validation_summary'].strip():
        raise ValueError('Validation scope is required')
    proof = value.get('blackbox_artifact')
    if proof is not None and (type(proof) is not int or proof <= 0 or proof not in value['evidence']):
        raise ValueError('R8 playback proof must be an indexed artifact')
    if value['version'] != '0.3.0' and proof is None:
        raise ValueError('New deliveries require exact-APK R8 playback proof')
    return value


CONFIG = load_manifest(Path(__file__).resolve().parents[1] / 'eplayer-delivery.json')
SOURCE, RUN = CONFIG['source'], CONFIG['run_id']
TAG = 'eplayer-v' + CONFIG['version']
APK = 'ePlayer-v' + CONFIG['version'] + '-preview.apk'
SHA, CERT = CONFIG['apk_sha256'], CONFIG['certificate_sha256']
OUT = Path('delivery')
EVIDENCE = CONFIG['evidence']


def gh(*args):
    return subprocess.check_output(['gh', *map(str, args)], text=True)


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def verify_apk():
    if digest(OUT / APK) != SHA:
        raise RuntimeError('Exact CI delivery APK checksum mismatch')


def verified_source():
    run = json.loads(gh('api', f'repos/{REPO}/actions/runs/{RUN}'))
    if run['head_sha'] != SOURCE or run['conclusion'] != 'success' or run['head_branch'] != 'main' or run['path'] != '.github/workflows/eplayer-preview.yml':
        raise RuntimeError('Source must be a successful main preview workflow')


def existing_release():
    if CONFIG.get('release_id'):
        release = json.loads(gh('api', f'repos/{REPO}/releases/{CONFIG["release_id"]}'))
    else:
        release = next((item for item in json.loads(gh('api', f'repos/{REPO}/releases?per_page=100')) if item['tag_name'] == TAG), None)
    if release is not None and (release['tag_name'] != TAG or release['target_commitish'] != SOURCE):
        raise RuntimeError('Existing release targets different source; preserve it')
    return release


def inspect():
    if os.environ.get('GITHUB_REPOSITORY') != REPO:
        raise RuntimeError('Unexpected repository')
    verified_source()
    release = existing_release()
    published = release is not None and not release['draft']
    if published:
        expected = {APK, f'{TAG}-native-libs.tar.gz', f'{TAG}-validation.zip', f'{TAG}-notes.md', 'SHA256SUMS'}
        assets = {item['name']: item for item in release['assets']}
        if set(assets) != expected or assets[APK].get('digest') != 'sha256:' + SHA:
            raise RuntimeError('Published delivery differs; never overwrite')
        print('Published delivery matches; no downloads, emulator or writes requested')
    with Path(os.environ['GITHUB_OUTPUT']).open('a') as output:
        output.write(f'published={str(published).lower()}\nartifact=eplayer-preview-{SOURCE}\nrun_id={RUN}\ntag={TAG}\n')


def verify_blackbox_report(report):
    if report.get('status') != 'passed' or report.get('source') != SOURCE or report.get('source_run') != RUN or report.get('apk_sha256') != SHA:
        raise RuntimeError('R8 playback proof does not match this exact candidate')


def prepare():
    OUT.mkdir(exist_ok=True)
    candidate = Path('delivery-input/standard/release/app-standard-universal-release.apk')
    (OUT / APK).write_bytes(candidate.read_bytes())
    verify_apk()
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT'])
    def sdk_tool(name):
        tools = sorted(sdk.glob('build-tools/*/' + name))
        if not tools:
            raise RuntimeError('Missing SDK verifier: ' + name)
        return tools[-1]
    signature = subprocess.check_output([sdk_tool('apksigner'), 'verify', '--print-certs', OUT / APK], text=True)
    Path('device-evidence').mkdir(exist_ok=True)
    Path('device-evidence/apksigner.txt').write_text(signature)
    if f'Signer #1 certificate SHA-256 digest: {CERT}' not in signature:
        raise RuntimeError('Delivery certificate mismatch; public verifier output: ' + signature)
    badging = subprocess.check_output([sdk_tool('aapt'), 'dump', 'badging', OUT / APK], text=True)
    def field(name):
        match = re.search(r"\b" + name + r"='([^']*)'", badging)
        if not match:
            raise RuntimeError('Missing APK metadata: ' + name)
        return match.group(1)
    if field('name') != 'app.eplayer.music' or field('versionName') != CONFIG['version'] or int(field('versionCode')) != CONFIG['version_code']:
        raise RuntimeError('APK identity/version mismatch')
    metadata = {'version': field('versionName'), 'version_code': int(field('versionCode')),
                'certificate_sha256': CERT, 'sha256': SHA}
    for key in ['sdkVersion', 'targetSdkVersion']:
        match = re.search(key + r":'([^']*)'", badging)
        metadata[key] = match.group(1) if match else 'unknown'
    Path('device-evidence').mkdir(exist_ok=True)
    Path('device-evidence/apk.json').write_text(json.dumps(metadata, indent=2) + '\n')
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
    verified_source()
    apk_metadata = json.loads(Path('device-evidence/apk.json').read_text())
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
                if artifact_id == CONFIG.get('blackbox_artifact'):
                    qa_run_id = metadata.get('workflow_run', {}).get('id')
                    if not qa_run_id:
                        raise RuntimeError('Missing R8 playback provenance')
                    qa_run = json.loads(gh('api', f'repos/{REPO}/actions/runs/{qa_run_id}'))
                    if qa_run['conclusion'] != 'success' or qa_run['path'] != '.github/workflows/eplayer-r8-blackbox.yml':
                        raise RuntimeError('R8 playback workflow did not pass')
                    with zipfile.ZipFile(path) as proof:
                        verify_blackbox_report(json.loads(proof.read('build.json')))
                archive.write(path, f'history/{path.name}')
                index.append({'artifact_id': artifact_id, 'name': metadata['name'], 'sha256': actual})
        for path in sorted(Path('device-evidence').rglob('*')):
            if path.is_file():
                archive.write(path, path.as_posix())
        archive.writestr('index.json', json.dumps(index, indent=2) + '\n')
    notes = f'''ePlayer {CONFIG['version']} preview · versionCode {CONFIG['version_code']} · minSdk {apk_metadata['sdkVersion']} / target {apk_metadata['targetSdkVersion']}

Source: https://github.com/{REPO}/commit/{SOURCE}
Verified workflow: https://github.com/{REPO}/actions/runs/{RUN}

{CONFIG['validation_summary']} This delivery additionally passed exact universal R8 APK installation/cold-start on API 36 x86_64. R8 music coverage is limited to the explicitly indexed checks; physical-device audio routing and live authenticated sources remain unverified.

APK is unchanged from the verified CI artifact (three ABIs); no recordings or signing keys are bundled. Certificate SHA256: {CERT}. This temporary CI signing identity may differ from installed previews and future CI builds may use another identity. Back up local imports/settings before a required uninstall; seamless upgrades are not promised. Do not overwrite an existing installation with a different certificate.

Native libraries, raw validation logs/screenshots including failed runs, and SHA256SUMS accompany this preview. USB exclusive output is removed; normal Android audio routing remains.
'''
    notes_path = OUT / f'{TAG}-notes.md'
    notes_path.write_text(notes)
    files = sorted(path for path in OUT.iterdir() if path.is_file() and path.name != 'SHA256SUMS')
    (OUT / 'SHA256SUMS').write_text(''.join(f'{digest(path)}  {path.name}\n' for path in files))
    files.append(OUT / 'SHA256SUMS')
    release = existing_release()
    if release is None:
        gh('release', 'create', TAG, '--repo', REPO, '--target', SOURCE, '--draft', '--prerelease', '--title', 'ePlayer ' + CONFIG['version'] + ' preview', '--notes-file', notes_path)
        release = existing_release()
    if release is None:
        raise RuntimeError('Draft release was not created')
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
    {'inspect': inspect, 'prepare': prepare, 'smoke': smoke, 'publish': publish}[sys.argv[1]]()
