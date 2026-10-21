import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('layout', Path(__file__).parents[1] / 'check_layout.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class LayoutTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.files = {'README.md', 'AGENTS.md', '.gitignore', 'catalog.json',
                      'tools/demo/README.md', 'tools/demo/VERSION', 'tools/demo/run.py'}
        for name in self.files:
            p = self.root / name
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text('')
        (self.root / 'tools/demo/VERSION').write_text('1.0.0\n')
        self.catalog = {'schema_version': 1, 'entries': [
            {'id': 'demo', 'path': 'tools/demo', 'version': '1.0.0',
             'description': 'Demo', 'entrypoint': 'run.py'}]}
        self.write_catalog()

    def write_catalog(self):
        (self.root / 'catalog.json').write_text(json.dumps(self.catalog))

    def test_valid_layout(self):
        self.assertEqual(m.validate(self.root, self.files), [])

    def test_reject_loose_root_file(self):
        self.assertTrue(any('root files' in x for x in m.validate(self.root, self.files | {'upload.txt'})))

    def test_reject_unregistered_tool(self):
        self.assertTrue(any('unregistered' in x for x in m.validate(self.root, self.files | {'tools/another/run.py'})))

    def test_reject_forced_generated_artifacts(self):
        for name in ['tools/demo/cache.qcow2', 'tools/demo/bundle.zip', 'tools/demo/sdk/bin/tool', 'tools/demo/.env']:
            with self.subTest(name=name):
                self.assertTrue(any('generated artifact' in x for x in m.validate(self.root, self.files | {name})))

    def test_reject_mismatched_version(self):
        self.catalog['entries'][0]['version'] = '1.0.1'
        self.write_catalog()
        self.assertTrue(any('differs' in x for x in m.validate(self.root, self.files)))

    def test_reject_duplicate_entries(self):
        self.catalog['entries'].append(dict(self.catalog['entries'][0]))
        self.write_catalog()
        self.assertTrue(any('duplicate' in x for x in m.validate(self.root, self.files)))

    def test_reject_missing_entrypoint(self):
        self.assertTrue(any('entrypoint' in x for x in m.validate(self.root, self.files - {'tools/demo/run.py'})))

    def test_reject_oversized_file(self):
        p = self.root / 'tools/demo/asset.png'
        with p.open('wb') as f:
            f.truncate(6 * 1024**2)
        self.assertTrue(any('5 MiB' in x for x in m.validate(self.root, self.files | {'tools/demo/asset.png'})))


if __name__ == '__main__':
    unittest.main()
