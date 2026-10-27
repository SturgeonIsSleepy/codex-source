import hashlib
import io
import zipfile
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

    def test_verified_signer_labels_vary_but_every_certificate_must_match(self):
        for prefix in ['Signer #1', 'V2 Signer']:
            delivery.verify_certificate(f'{prefix}: certificate SHA-256 digest: {delivery.CERT}\n')
        for output in ['', f'V2 Signer: certificate SHA-256 digest: {"0" * 64}\n',
                       f'Signer #1 certificate SHA-256 digest: {delivery.CERT}\nSigner #2 certificate SHA-256 digest: {"0" * 64}\n']:
            with self.subTest(output=output), self.assertRaises(RuntimeError):
                delivery.verify_certificate(output)


class JobEvidenceChecks(unittest.TestCase):
    def test_failed_setup_log_is_archived_with_original_result_and_source(self):
        job = dict(id=123, run_id=456, run_attempt=1, status='completed', conclusion='failure')
        run = dict(id=456, head_sha='a' * 40, conclusion='failure')
        content = b'\x1b[31mSDK install failed; no device tests executed\x1b[0m\n'
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, 'w') as archive, patch.object(delivery, 'gh', side_effect=[json.dumps(job), json.dumps(run)]), patch.object(delivery.subprocess, 'check_output', return_value=content):
            index = delivery.archive_job_logs(archive, [123])
        with zipfile.ZipFile(buffer) as archive:
            self.assertEqual(content, archive.read(index[0]['path']))
            self.assertEqual(hashlib.sha256(content).hexdigest(), index[0]['sha256'])
            metadata = json.loads(archive.read('workflow-jobs/123/metadata.json'))
            self.assertEqual('failure', metadata['job']['conclusion'])
            self.assertEqual(1, index[0]['run_attempt'])
            self.assertEqual('a' * 40, index[0]['source'])

    def test_incomplete_or_mismatched_job_cannot_supply_release_evidence(self):
        for job in [dict(id=123, status='in_progress', run_id=456), dict(id=789, status='completed', run_id=456)]:
            with self.subTest(job=job), zipfile.ZipFile(io.BytesIO(), 'w') as archive, patch.object(delivery, 'gh', return_value=json.dumps(job)), patch.object(delivery.subprocess, 'check_output') as download, self.assertRaises(RuntimeError):
                delivery.archive_job_logs(archive, [123])
            download.assert_not_called()

    def test_job_ids_are_validated_before_any_release_operation(self):
        for jobs in [[True], [0], [-1], [123, 123], '--repo']:
            with self.subTest(jobs=jobs), tempfile.TemporaryDirectory() as folder:
                path = Path(folder) / 'manifest.json'
                path.write_text(json.dumps(dict(delivery.CONFIG, evidence_jobs=jobs)))
                with self.assertRaises(ValueError):
                    delivery.load_manifest(path)
