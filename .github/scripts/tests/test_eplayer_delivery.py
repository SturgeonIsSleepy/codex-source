import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('delivery', Path(__file__).parents[1] / 'publish_eplayer_preview.py')
delivery = importlib.util.module_from_spec(spec)
spec.loader.exec_module(delivery)


class DeliveryChecks(unittest.TestCase):
    def load(self, value):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'manifest.json'
            path.write_text(json.dumps(value))
            return delivery.load_manifest(path)

    def test_rejects_path_or_argument_injection_and_missing_integrity(self):
        for key, value in [('version', '../0.3.1'), ('source', 'main'), ('apk_sha256', ''),
                           ('certificate_sha256', 'unknown'), ('run_id', '--repo'), ('run_id', True)]:
            manifest = dict(delivery.CONFIG, **{key: value})
            with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                self.load(manifest)

    def test_requires_evidence_and_an_explicit_validation_scope(self):
        for changes in [{'evidence': []}, {'evidence': [True]}, {'validation_summary': ''}, {'release_id': -1}]:
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                self.load(dict(delivery.CONFIG, **changes))

    def test_new_versions_do_not_require_an_existing_draft_id(self):
        manifest = dict(delivery.CONFIG, version='0.3.1', release_id=None, blackbox_artifact=123, evidence=[123])
        self.assertEqual('0.3.1', self.load(manifest)['version'])

    def test_success_from_the_wrong_source_or_branch_cannot_publish(self):
        good = dict(head_sha=delivery.SOURCE, conclusion='success', head_branch='main', path='.github/workflows/eplayer-preview.yml')
        for changes in [{'head_sha': '0' * 40}, {'conclusion': 'cancelled'}, {'head_branch': 'work'}, {'path': 'other.yml'}]:
            with self.subTest(changes=changes), patch.object(delivery, 'gh', return_value=json.dumps(dict(good, **changes))), self.assertRaises(RuntimeError):
                delivery.verified_source()

    def test_published_release_is_checked_without_mutating_or_rebuilding(self):
        names = [delivery.APK, f'{delivery.TAG}-native-libs.tar.gz', f'{delivery.TAG}-validation.zip', f'{delivery.TAG}-notes.md', 'SHA256SUMS']
        release = dict(draft=False, assets=[dict(name=name, digest='sha256:' + delivery.SHA) for name in names])
        with tempfile.TemporaryDirectory() as folder, patch.dict(os.environ, GITHUB_REPOSITORY=delivery.REPO, GITHUB_OUTPUT=str(Path(folder) / 'output')), patch.object(delivery, 'verified_source'), patch.object(delivery, 'existing_release', return_value=release):
            delivery.inspect()
            self.assertIn('published=true', (Path(folder) / 'output').read_text())
            release['assets'][0]['digest'] = 'sha256:' + '0' * 64
            with self.assertRaises(RuntimeError):
                delivery.inspect()

    def test_new_delivery_requires_indexed_exact_r8_proof(self):
        base = dict(delivery.CONFIG, version='0.3.0')
        base.pop('blackbox_artifact', None)
        for changes in [{'version': '0.3.1'}, {'blackbox_artifact': -1}, {'blackbox_artifact': True}]:
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                self.load(dict(base, **changes))

    def test_old_or_failed_r8_reports_cannot_approve_the_candidate(self):
        report = dict(status='passed', source=delivery.SOURCE, source_run=delivery.RUN, apk_sha256=delivery.SHA)
        delivery.verify_blackbox_report(report)
        for changes in [{'status': 'failed'}, {'source': '0' * 40}, {'source_run': 1}, {'apk_sha256': '0' * 64}]:
            with self.subTest(changes=changes), self.assertRaises(RuntimeError):
                delivery.verify_blackbox_report(dict(report, **changes))

    def test_existing_draft_target_is_never_replaced(self):
        wrong = dict(tag_name=delivery.TAG, target_commitish='0' * 40)
        with patch.dict(delivery.CONFIG, release_id=123), patch.object(delivery, 'gh', return_value=json.dumps(wrong)), self.assertRaises(RuntimeError):
            delivery.existing_release()
