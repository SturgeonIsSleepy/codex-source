#!/usr/bin/env python3
"""Validate the tracked repository layout without external dependencies."""
import json
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys

ROOT_FILES = {'README.md', 'AGENTS.md', '.gitignore', 'catalog.json'}
PREFIXES = {'tools', 'projects', 'assets'}
NAME = re.compile(r'^[a-z0-9]+(?:-[a-z0-9]+)*$')
VERSION = re.compile(r'^\d+\.\d+\.\d+(?:-[a-zA-Z0-9.-]+)?$')
FORBIDDEN_DIRS = {'__pycache__', 'node_modules', '.venv', '.gradle', '.cache', 'sdk', 'avd'}
FORBIDDEN_ENDINGS = ('.apk', '.aab', '.img', '.qcow2', '.zip', '.tar', '.tar.gz', '.tgz', '.log', '.pyc', '.pid', '.part')


def validate(root, files):
    errors = []
    try:
        catalog = json.loads((root / 'catalog.json').read_text())
        if catalog.get('schema_version') != 1 or not isinstance(catalog.get('entries'), list):
            raise ValueError('expected schema_version=1 and entries list')
    except (OSError, ValueError) as exc:
        return [f'catalog.json: {exc}']
    registered, ids = set(), set()
    for item in catalog['entries']:
        if not isinstance(item, dict):
            errors.append('catalog entry must be an object')
            continue
        ident, path, version = (item.get(k, '') for k in ('id', 'path', 'version'))
        if not all(isinstance(value, str) for value in (ident, path, version)):
            errors.append('catalog id, path and version must be strings')
            continue
        parts = PurePosixPath(path).parts
        if not NAME.fullmatch(ident) or ident in ids:
            errors.append(f'invalid or duplicate id: {ident}')
        ids.add(ident)
        if len(parts) != 2 or parts[0] not in PREFIXES or not NAME.fullmatch(parts[-1]) or path in registered:
            errors.append(f'invalid or duplicate entry path: {path}')
            continue
        registered.add(path)
        if not VERSION.fullmatch(version):
            errors.append(f'{path}: invalid semantic version')
        for required in ('README.md', 'VERSION'):
            if f'{path}/{required}' not in files:
                errors.append(f'{path}: missing tracked {required}')
        version_file = root / path / 'VERSION'
        if version_file.is_file() and version_file.read_text().strip() != version:
            errors.append(f'{path}: VERSION differs from catalog')
        entrypoint = item.get('entrypoint', '')
        if not isinstance(entrypoint, str) or not entrypoint or PurePosixPath(entrypoint).is_absolute() or '..' in PurePosixPath(entrypoint).parts:
            errors.append(f'{path}: invalid entrypoint')
        elif f'{path}/{entrypoint}' not in files:
            errors.append(f'{path}: entrypoint is not tracked')
        if not isinstance(item.get('description'), str) or not item['description'].strip():
            errors.append(f'{path}: missing description')
    for file in sorted(files):
        p = PurePosixPath(file)
        parts = p.parts
        if len(parts) == 1 and file not in ROOT_FILES:
            errors.append(f'{file}: new root files are not allowed')
        elif len(parts) > 1 and parts[0] not in PREFIXES | {'docs', '.github'}:
            errors.append(f'{file}: unknown root directory')
        if parts[0] in PREFIXES and (len(parts) < 3 or '/'.join(parts[:2]) not in registered):
            errors.append(f'{file}: unregistered entry')
        if any(part in FORBIDDEN_DIRS or part.endswith('.avd') for part in parts[:-1]) or file.lower().endswith(FORBIDDEN_ENDINGS) or p.name in {'state.json', '.env'} or (p.name.startswith('.env.') and p.name != '.env.example'):
            errors.append(f'{file}: runtime, local configuration or generated artifact')
        local = root / file
        if local.is_symlink():
            errors.append(f'{file}: symlinks are not allowed')
        elif local.is_file() and local.stat().st_size > 5 * 1024**2:
            errors.append(f'{file}: exceeds 5 MiB; use a Release')
    return errors


def main():
    root = Path(__file__).resolve().parents[2]
    raw = subprocess.check_output(['git', '-C', str(root), 'ls-files', '-z'])
    files = set(raw.decode().rstrip('\0').split('\0')) - {''}
    errors = validate(root, files)
    if errors:
        print('\n'.join(errors), file=sys.stderr)
        return 1
    print(f'Layout OK: {len(files)} tracked files')
    return 0


if __name__ == '__main__':
    sys.exit(main())
