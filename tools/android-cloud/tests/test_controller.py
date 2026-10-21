import contextlib
import importlib.machinery
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch, MagicMock
from types import SimpleNamespace

loader = importlib.machinery.SourceFileLoader('controller', str(Path(__file__).parents[1] / 'android-cloud'))
spec = importlib.util.spec_from_loader(loader.name, loader)
m = importlib.util.module_from_spec(spec)
loader.exec_module(m)


class ControllerTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        root = Path(self.tmp.name)
        for key, value in {'ROOT': root, 'SDK': root / 'sdk', 'AVDS': root / 'avd',
                           'STATE': root / 'state.json', 'LOG': root / 'log',
                           'ADB': str(root / 'sdk/platform-tools/adb')}.items():
            p = patch.object(m, key, value)
            p.start()
            self.addCleanup(p.stop)

    def fake_adb_file(self):
        p = Path(m.ADB)
        p.parent.mkdir(parents=True, exist_ok=True)
        p.touch()

    def test_fresh_status_is_stopped(self):
        self.assertEqual(m.inspect()['status'], 'stopped')

    def test_ready_requires_completed_boot_and_api36(self):
        self.fake_adb_file()
        saved = {'serial': 'emulator-5554', 'mode': 'software'}
        for api, boot, expected in [('36', '1', 'ready'), ('36', '', 'booting'),
                                    ('35', '1', 'wrong_android_version')]:
            with patch.object(m, 'adb', side_effect=['device', api, boot, m.AVD + '\nOK']):
                self.assertEqual(m.inspect(saved)['status'], expected)

    def test_offline_is_not_ready(self):
        self.fake_adb_file()
        with patch.object(m, 'adb', return_value='error: device offline'):
            self.assertEqual(m.inspect({'serial': 'emulator-5554', 'mode': 'software'})['status'], 'offline')

    def test_up_reuses_ready_device_without_setup(self):
        m.save({'serial': 'emulator-5554', 'mode': 'software'})
        with patch.object(m, 'inspect', return_value={'status': 'ready'}) as inspect, \
                patch.object(m, 'setup') as setup:
            self.assertEqual(m.up(object())['status'], 'ready')
            setup.assert_not_called()
            inspect.assert_called_once()

    def test_new_up_returns_without_waiting_for_boot(self):
        args = SimpleNamespace(port=5554, memory=2048, software=True, wait=False)
        child = MagicMock(pid=4321)
        with patch.object(m, 'setup'), patch.object(m, 'run'), \
                patch.object(m.socket, 'socket'), \
                patch.object(m.subprocess, 'Popen', return_value=child) as popen, \
                patch.object(m, 'process_birth', return_value='123'), \
                patch.object(m, 'wait_ready') as wait, patch.dict(m.os.environ, {}, clear=True):
            self.assertEqual(m.up(args)['status'], 'starting')
            wait.assert_not_called()
            self.assertTrue(popen.call_args.kwargs['start_new_session'])
            self.assertEqual(m.state()['pid'], 4321)

    def test_ongoing_up_does_not_wait_or_spawn_again(self):
        m.save({'serial': 'emulator-5554', 'mode': 'software'})
        args = SimpleNamespace(wait=False)
        with patch.object(m, 'inspect', return_value={'status': 'starting'}), \
                patch.object(m, 'adb', return_value=''), \
                patch.object(m, 'owned_running', return_value=True), \
                patch.object(m, 'wait_ready') as wait, patch.object(m, 'setup') as setup:
            self.assertEqual(m.up(args)['status'], 'starting')
            wait.assert_not_called()
            setup.assert_not_called()

    def test_no_kvm_automatically_selects_software(self):
        with patch('sys.argv', ['android-cloud', 'up', '--accept-licenses']), \
                patch.object(m, 'kvm', return_value=False), \
                patch.object(m, 'up', return_value={'status': 'ready'}) as up, \
                contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(m.main(), 0)
            args = up.call_args.args[0]
            self.assertTrue(args.software)
            self.assertFalse(args.wait)
            self.assertEqual(args.timeout, 900)

    def test_kvm_automatically_selects_acceleration(self):
        with patch('sys.argv', ['android-cloud', 'up']), \
                patch.object(m, 'kvm', return_value=True), \
                patch.object(m, 'up', return_value={'status': 'ready'}) as up, \
                contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(m.main(), 0)
            self.assertFalse(up.call_args.args[0].software)
            self.assertEqual(up.call_args.args[0].timeout, 240)

    def test_setup_skips_installed_packages_and_avd(self):
        for p in [m.SDK / 'emulator/emulator', Path(m.ADB),
                  m.SDK / 'system-images/android-36/default/x86_64/system.img',
                  m.AVDS / (m.AVD + '.ini')]:
            p.parent.mkdir(parents=True, exist_ok=True)
            p.touch()
        args = type('Args', (), {'software': True, 'accept_licenses': False})()
        with patch.object(m, 'preflight'), patch.object(m, 'run') as run:
            self.assertEqual(m.setup(args)['status'], 'installed')
            run.assert_not_called()

    def test_missing_license_does_not_download(self):
        args = type('Args', (), {'software': True, 'accept_licenses': False})()
        with patch.object(m, 'preflight'), patch.object(m, 'bootstrap') as bootstrap:
            with self.assertRaises(m.Failure) as cm:
                m.setup(args)
            self.assertEqual(cm.exception.code, 'licenses_required')
            bootstrap.assert_not_called()

    def test_concurrent_deployment_fails_fast(self):
        with m.locked():
            with self.assertRaises(m.Failure) as cm:
                with m.locked():
                    pass
            self.assertEqual(cm.exception.code, 'busy')

    def test_stop_refuses_unrelated_device(self):
        m.save({'serial': 'emulator-5554', 'mode': 'software'})
        with patch.object(m, 'adb', side_effect=['device', 'another_avd\nOK']):
            with self.assertRaises(m.Failure) as cm:
                m.down()
            self.assertEqual(cm.exception.code, 'device_mismatch')
            self.assertTrue(m.STATE.exists())

    def test_local_ready_rejects_port_reused_by_another_avd(self):
        self.fake_adb_file()
        with patch.object(m, 'adb', side_effect=['device', '36', '1', 'unrelated\nOK']):
            result = m.inspect({'serial': 'emulator-5554', 'mode': 'software'})
            self.assertEqual(result['status'], 'device_mismatch')

    def test_transient_adb_timeout_does_not_abort_boot_check(self):
        with patch.object(m, 'run', side_effect=m.Failure('command_timeout', 'timeout')):
            self.assertEqual(m.adb('emulator-5554', 'get-state'), '')
            with self.assertRaises(m.Failure):
                m.adb('emulator-5554', 'emu', 'kill', check=True)

    def test_recycled_pid_is_not_owned(self):
        with patch.object(m, 'process_birth', return_value='987'):
            self.assertFalse(m.owned_running({'pid': 123, 'birth': '456'}))
            self.assertTrue(m.owned_running({'pid': 123, 'birth': '987'}))

    def test_remote_down_disconnects_without_killing(self):
        m.save({'serial': 'private-host:5555', 'mode': 'remote'})
        with patch.object(m, 'run') as run:
            self.assertEqual(m.down()['status'], 'stopped')
            self.assertEqual(run.call_args.args[0], [m.ADB, 'disconnect', 'private-host:5555'])
            self.assertFalse(m.STATE.exists())

    def test_boot_failure_is_reported_without_deleting_state(self):
        saved = {'serial': 'emulator-5554', 'mode': 'software'}
        m.save(saved)
        with patch.object(m, 'inspect', return_value={'status': 'offline'}):
            with self.assertRaises(m.Failure) as cm:
                m.wait_ready(saved, 0)
            self.assertEqual(cm.exception.code, 'boot_timeout')
            self.assertTrue(m.STATE.exists())


if __name__ == '__main__':
    unittest.main()
