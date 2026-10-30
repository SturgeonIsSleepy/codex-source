"""Exercise evidence classification; these mocks do not assert Android playback works."""
from contextlib import redirect_stdout
from io import StringIO
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[1] / 'run_eplayer_device_checks.py'


class DeviceEvidenceTests(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.previous = Path.cwd()
        os.chdir(self.folder.name)
        self.addCleanup(self.folder.cleanup)
        self.addCleanup(os.chdir, self.previous)
        apk = Path('device-apks')
        apk.mkdir()
        for name in ['app-standard-x86_64-fastRelease.apk', 'app-standard-fastRelease-androidTest.apk']:
            (apk / name).write_bytes(b'unit-test fixture; not an APK')
        spec = importlib.util.spec_from_file_location('device_evidence_fixture', SCRIPT)
        self.script = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.script)
        self.result = 'OK (1 tests)\nINSTRUMENTATION_CODE: -1\n'
        self.calls = []

    def run_adb(self, arguments, **kwargs):
        self.assertEqual('adb', arguments[0])
        self.calls.append(arguments)
        output = ''
        if 'instrument' in arguments:
            if isinstance(self.result, BaseException):
                raise self.result
            output = self.result
        elif 'getprop' in arguments:
            output = 'fixture-device\n'
        return subprocess.CompletedProcess(arguments, 0, output, '')

    def run_checks(self, release='0'):
        with patch.dict(os.environ, {'EPLAYER_CHECK_RELEASE': release, 'GITHUB_SHA': 'harness-unit-fixture'}), patch.object(self.script.subprocess, 'run', self.run_adb), redirect_stdout(StringIO()):
            self.script.main()

    def metadata(self):
        return json.loads(Path('device-evidence/build.json').read_text())

    def test_branch_validation_marks_r8_as_not_requested(self):
        self.run_checks()
        self.assertEqual({'instrumentation': 'passed', 'release_start': 'not_requested'}, self.metadata()['checks'])
        self.assertIsNone(self.metadata()['release'])
        self.assertTrue(Path('device-evidence/release-not-run.txt').exists())
        self.assertTrue(Path('device-evidence/system-media.txt').exists())
        self.assertTrue(any('MediaCarouselCtlrLog' in call and 'MediaTimeout' in call and 'MediaLog' in call for call in self.calls))

    def test_instrumentation_failure_with_zero_shell_exit_stays_failed(self):
        self.result = 'FAILURES!!!\nTests run: 1, Failures: 1\nINSTRUMENTATION_CODE: -1\n'
        with self.assertRaises(RuntimeError):
            self.run_checks()
        self.assertEqual('failed', self.metadata()['checks']['instrumentation'])
        self.assertTrue(Path('device-evidence/runtime.txt').exists())
        self.assertTrue(Path('device-evidence/system-media.txt').exists())
        self.assertTrue(Path('device-evidence/system-sessions.txt').exists())
        self.assertTrue(any('logcat' in call and 'MediaDataProcessor' in call for call in self.calls))

    def test_timeout_retains_partial_output_and_status(self):
        self.result = subprocess.TimeoutExpired(['adb'], 480, output=b'partial instrument output')
        with self.assertRaises(subprocess.TimeoutExpired):
            self.run_checks()
        self.assertEqual('timed_out', self.metadata()['checks']['instrumentation'])
        self.assertIn('partial instrument output', Path('device-evidence/instrumentation.txt').read_text())

    def test_required_release_cannot_silently_be_skipped(self):
        with self.assertRaisesRegex(RuntimeError, 'Expected one'):
            self.run_checks(release='1')
        self.assertFalse(any('install' in call for call in self.calls))

    def test_manual_interruption_is_incomplete(self):
        self.result = KeyboardInterrupt()
        with self.assertRaises(KeyboardInterrupt):
            self.run_checks()
        self.assertEqual('incomplete', self.metadata()['checks']['instrumentation'])

    def test_release_failure_preserves_functional_result(self):
        Path('device-apks/app-standard-x86_64-release.apk').write_bytes(b'fixture')
        with self.assertRaisesRegex(RuntimeError, 'did not launch'):
            self.run_checks(release='1')
        self.assertEqual({'instrumentation': 'passed', 'release_start': 'failed'}, self.metadata()['checks'])
