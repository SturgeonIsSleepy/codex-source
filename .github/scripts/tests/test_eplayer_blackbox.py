import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('blackbox', Path(__file__).parents[1] / 'run_eplayer_blackbox.py')
checks = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checks)


class BlackboxChecks(unittest.TestCase):
    def test_rejects_unpinned_or_invalid_candidate_values(self):
        good = dict(source='a' * 40, apk_sha256='b' * 64, run_id=1)
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'config.json'
            for changes in [{'source': 'main'}, {'source': None}, {'apk_sha256': '../app.apk'}, {'run_id': True}, {'run_id': 0}]:
                with self.subTest(changes=changes), self.assertRaises(ValueError):
                    path.write_text(json.dumps(dict(good, **changes)))
                    checks.read_config(path)

    def test_requires_the_successful_matching_main_build(self):
        config = dict(source='a' * 40)
        run = dict(head_sha=config['source'], conclusion='success', head_branch='main', path='.github/workflows/eplayer-preview.yml')
        checks.successful_run(config, run)
        for changes in [{'head_sha': 'b' * 40}, {'conclusion': 'failure'}, {'head_branch': 'topic'}, {'path': 'other.yml'}]:
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                checks.successful_run(config, dict(run, **changes))

    def test_physical_or_unknown_devices_cannot_reach_data_clearing(self):
        checks.disposable_emulator('1', 'sdk_gphone64_x86_64')
        for qemu, model in [('0', 'Pixel 9'), ('', 'sdk_gphone64_x86_64'), ('1', 'Pixel 9')]:
            with self.subTest(model=model), self.assertRaises(RuntimeError):
                checks.disposable_emulator(qemu, model)

    def test_partial_crashed_or_failed_runs_never_pass(self):
        passed = 'OK (2 tests)\nINSTRUMENTATION_CODE: -1'
        self.assertTrue(checks.instrumentation_passed(passed))
        for text in ['OK (1 tests)\nINSTRUMENTATION_CODE: -1', 'OK (2 tests)', passed + '\nFAILURES!!!', passed + '\nProcess crashed']:
            self.assertFalse(checks.instrumentation_passed(text))
