#!/usr/bin/env python3
"""Publish verified handoff payloads from temporary Git blobs to Releases.

Payload blobs are transport objects, never entries in a source commit/tree.
Only the manifest, checksums and this uploader enter source history.
"""
import base64
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import os
from pathlib import Path
import re
import tempfile
import urllib.error
import urllib.parse
import urllib.request

REPO = os.environ['GITHUB_REPOSITORY']
if REPO != 'SturgeonIsSleepy/codex-source':
    raise SystemExit('Unexpected repository')
TOKEN = os.environ['GH_TOKEN']


def api(path, data=None, method=None, content_type='application/json', uploads=False):
    host = 'uploads.github.com' if uploads else 'api.github.com'
    body = json.dumps(data).encode() if isinstance(data, dict) else data
    request = urllib.request.Request('https://' + host + '/repos/' + REPO + path,
        data=body, method=method,
        headers={'Authorization': 'Bearer ' + TOKEN, 'Content-Type': content_type,
                 'Accept': 'application/vnd.github+json', 'User-Agent': 'eplayer-handoff'})
    with urllib.request.urlopen(request, timeout=300) as response:
        return json.load(response)


def assemble(entry, folder):
    name = entry['name']
    if Path(name).name != name or name in {'.', '..'}:
        raise ValueError('Invalid asset name')
    destination = folder / name
    total = hashlib.sha256()
    with destination.open('wb') as stream:
        for sha in entry['chunks']:
            if not re.fullmatch('[0-9a-f]{40}', sha):
                raise ValueError('Invalid transport object')
            blob = api('/git/blobs/' + sha)
            if blob.get('encoding') != 'base64':
                raise ValueError('Unexpected blob encoding')
            payload = base64.b64decode(blob['content'])
            actual = hashlib.sha1(b'blob ' + str(len(payload)).encode() + b'\0' + payload).hexdigest()
            if actual != sha:
                raise ValueError('Transport integrity check failed')
            stream.write(payload)
            total.update(payload)
    if destination.stat().st_size != entry['size'] or total.hexdigest() != entry['sha256']:
        raise ValueError('Assembled asset integrity check failed: ' + name)
    return destination


def main():
    manifest = json.loads(Path('.github/handoff-assets.json').read_text())
    for release in manifest['releases']:
        if release['tag'] not in {'eplayer-v0.1.0', 'witch-player-v1.8.0'}:
            raise ValueError('Unexpected release tag')
        existing = next((item for item in api('/releases?per_page=100')
                         if item['tag_name'] == release['tag']), None)
        if existing is None:
            existing = api('/releases', dict(tag_name=release['tag'],
                target_commitish=os.environ['GITHUB_SHA'], name=release['title'],
                body=release['notes'], draft=True, prerelease=release['prerelease']), 'POST')
        release_id = existing['id']
        uploaded = {item['name']: item for item in api(f'/releases/{release_id}/assets?per_page=100')}
        with tempfile.TemporaryDirectory() as temporary:
            folder = Path(temporary)
            with ThreadPoolExecutor(max_workers=3) as executor:
                for entry, path in zip(release['assets'], executor.map(lambda item: assemble(item, folder), release['assets'])):
                    old = uploaded.get(entry['name'])
                    if old:
                        if old.get('digest') != 'sha256:' + entry['sha256']:
                            raise ValueError('Existing asset differs: ' + entry['name'])
                    else:
                        encoded = urllib.parse.urlencode({'name': entry['name']})
                        result = api(f'/releases/{release_id}/assets?{encoded}', path.read_bytes(),
                                     'POST', 'application/octet-stream', uploads=True)
                        if result.get('size') != entry['size']:
                            raise ValueError('Upload size mismatch')
                    path.unlink()
                    print('Verified and published:', release['tag'], entry['name'], flush=True)
        api(f'/releases/{release_id}', dict(draft=False, prerelease=release['prerelease'],
            body=release['notes'], make_latest='false'), 'PATCH')
        print('Release complete:', release['tag'], flush=True)


if __name__ == '__main__':
    main()
