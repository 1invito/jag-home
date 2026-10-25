import json
import os
from pathlib import Path
import shlex
import subprocess
import sys
import tempfile
import time
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from adb_backend import Adb
from controller import Controller, private_json, validate_measurements, appearance_text
from protocol import (Blocked, Setting, OLD, NEW, STABLE, DUMB, component, services, stage_services,
                      handover_services, restore_services, restore_alarm, immediate_alarm, bound)
from fake_device import FakeDevice


class ProtocolTest(unittest.TestCase):
    def test_component_aliases_are_exact(self):
        self.assertEqual(component('tv.dumbtv.home/.Guard'), OLD)
        for value in ('bad', 'tv.dumbtv.home/.Guard;echo hacked', '', 'x/../../z'):
            with self.assertRaises(Blocked): component(value)

    def test_staging_preserves_original_bytes_and_is_disjoint_from_activation(self):
        original = Setting(True, 'reader.access/.Reader:tv.dumbtv.home/.Guard')
        staged = stage_services(original)
        self.assertEqual(staged.value, original.value + ':' + NEW)
        handed = handover_services(staged)
        self.assertEqual(handed.value, 'reader.access/.Reader:' + NEW)
        self.assertNotIn(OLD, services(handed))
        with self.assertRaises(Blocked): stage_services(staged)

    def test_restore_preserves_unrelated_concurrent_services(self):
        original = Setting(True, 'reader.access/.Reader:tv.dumbtv.home/.Guard')
        expected = handover_services(stage_services(original))
        current = Setting(True, expected.value + ':new.reader/.Service')
        restored, conflict = restore_services(original, expected, current)
        self.assertTrue(conflict)
        self.assertEqual(restored.value, 'reader.access/.Reader:new.reader/.Service:tv.dumbtv.home/.Guard')

    def test_exact_unset_empty_literal_null_roundtrip(self):
        for original in (Setting(False), Setting(True, ''), Setting(True, 'null'), Setting(True, "a='quoted value',min_futurity=2")):
            expected = Setting(True, 'min_futurity=0')
            restored, conflict = restore_alarm(original, expected, expected)
            self.assertEqual(restored, original)
            self.assertFalse(conflict)
            self.assertEqual(Setting.load(original.json()), original)

    def test_alarm_concurrency_merges_only_owned_key(self):
        original = Setting(True, 'min_futurity=50,other=3')
        expected = immediate_alarm(original)
        current = Setting(True, 'other=8,min_futurity=0,added=6')
        restored, conflict = restore_alarm(original, expected, current)
        self.assertTrue(conflict)
        self.assertEqual(restored.value, 'other=8,added=6,min_futurity=50')
        external = Setting(True, 'min_futurity=90,other=8')
        self.assertEqual(restore_alarm(original, expected, external)[0], external)

    def test_unknown_alarm_syntax_blocks_changes(self):
        for value in ('null', 'invalid', 'a=1,a=2'):
            with self.assertRaises(Blocked): immediate_alarm(Setting(True, value))

    def test_bound_parser_scopes_user_and_section(self):
        dump = ('User state[\nattributes:{id=10, currentUser=false}\nservices:{Service[label=JAG Home redirector, synthetic]}\n]\n'
                'User state[\nattributes:{id=0, currentUser=true}\nservices:{}\nEnabled services:{Service[label=JAG Home redirector, synthetic]}\n]')
        self.assertFalse(bound(dump, 0, 'JAG Home redirector', 'com.jag.home.integration.HomeGuard'))
        self.assertTrue(bound(dump, 10, 'JAG Home redirector', 'com.jag.home.integration.HomeGuard'))
        with self.assertRaises(Blocked): bound('unknown', 0, '', '')
        with self.assertRaises(Blocked): bound('User state[attributes:{id=0,}\nservices:{', 0, '', '')

    def test_appearance_rejects_private_data_and_unapproved_fields(self):
        self.assertEqual(appearance_text('theme=Graphite\nclock=Hidden\n', True), 'theme=Graphite\nclock=Hidden\nwallpaper.import=true\n')
        for value in ('account.email=person', 'wallpaper=/private/file', 'recent=anything', 'theme=Other', 'order=unknown', 'theme=Cinematic\ntheme=Graphite'):
            with self.assertRaises(Blocked): appearance_text(value)

    def test_acceptance_requires_actual_finite_performance_and_checks(self):
        data = acceptance()
        validate_measurements(data)
        for update in ({'warm_trials': 0}, {'vpn_ok': False}, {'jag_median_ms': 130},
                       {'jag_p95_ms': float('nan')}, {'additional_visible_pss_mib': 16}, {'remote_ok': 'yes'}):
            with self.subTest(update=update), self.assertRaises(Blocked): validate_measurements(dict(data, **update))

    def test_owner_performance_waiver_is_explicit_and_never_fakes_numbers(self):
        physical = dict(remote_ok=True, playback_ok=True, vpn_ok=True,
                        settings_ok=True, long_home_ok=True, reboot_ok=True,
                        performance_waived=True)
        validate_measurements(physical, waive_performance=True)
        with self.assertRaises(Blocked):
            validate_measurements(physical)  # no accidental bypass in normal mode
        for invalid in (
            dict(physical, remote_ok=False),
            dict(physical, performance_waived=False),
            dict(physical, warm_trials=10),
            {'performance_waived': True},
        ):
            with self.subTest(invalid=invalid), self.assertRaises(Blocked):
                validate_measurements(invalid, waive_performance=True)


def acceptance():
    return dict(remote_ok=True, playback_ok=True, vpn_ok=True, settings_ok=True, long_home_ok=True,
                reboot_ok=True, comparable_conditions=True, warm_trials=10, dumbtv_median_ms=100,
                jag_median_ms=105, dumbtv_p95_ms=140, jag_p95_ms=145, additional_visible_pss_mib=8)


class TransactionTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.folder = Path(self.temp.name)
        self.device = FakeDevice.create(self.folder)
        self.tool = Controller(self.device, self.folder)
        self.original = self.device.load()
        self.apk = self.folder / 'owner.apk'; self.apk.write_bytes(b'stable-fixture')
        self.tool.backup(); self.tool.install(self.apk, '1' * 64)

    def prepare(self):
        self.tool.prepare(60, lambda text: None)
        journal = self.tool.journal()
        private_json(self.folder / 'watchdog-ready.json', {'nonce': journal['nonce'], 'time': time.time(), 'pid': os.getpid()})

    def active(self): self.prepare(); self.tool.handover()

    def assert_protected(self):
        data = self.device.load()
        self.assertEqual(data['disabled'], self.original['disabled'])
        self.assertEqual(set(data['installed']) - {STABLE}, set(self.original['installed']))
        self.assertTrue(all(command[0] == 'setting' or command == ['force-stop', STABLE] for command in data['commands']))

    def assert_restored(self):
        self.assertEqual(self.device.load()['keys'], self.original['keys'])
        self.assertFalse(self.device.load()['armed'])
        self.assertEqual(self.device.front(), DUMB)
        self.assert_protected()

    def test_install_does_not_change_home_settings_or_arm_guard(self):
        self.assertEqual(self.device.load()['keys'], self.original['keys'])
        self.assertFalse(self.device.load()['armed'])
        self.assertEqual(self.tool.journal()['phase'], 'installed')
        self.assert_protected()

    def test_preflight_and_backup_are_device_read_only(self):
        before = self.device.load()['commands']
        self.tool.preflight()
        self.assertEqual(self.device.load()['commands'], before)
        self.assertEqual(self.device.load()['keys'], self.original['keys'])

    def test_existing_immediate_alarm_is_never_rewritten(self):
        data = self.device.load()
        data['keys']['global.alarm_manager_constants'] = Setting(True, 'other=77,min_futurity=0').json()
        self.device.change(keys=data['keys'])
        # A new backup is necessary after an intentional baseline change.
        new_folder = self.folder / 'fresh-baseline'; new_folder.mkdir()
        tool = Controller(self.device, new_folder)
        tool.backup(); tool.install(self.apk, '1' * 64)
        tool.prepare(60, lambda text: None)
        journal = tool.journal(); private_json(new_folder / 'watchdog-ready.json', {'nonce': journal['nonce'], 'time': time.time(), 'pid': os.getpid()})
        tool.handover()
        self.assertFalse(any(command[0] == 'setting' and command[1] == 'global.alarm_manager_constants' for command in self.device.load()['commands']))
        tool.rollback()

    def test_private_immutable_backup(self):
        with self.assertRaises(Blocked): self.tool.backup()
        if os.name != 'nt': self.assertEqual((self.folder / 'snapshot.json').stat().st_mode & 0o777, 0o600)
        self.assertIn('not ADB-readable', self.tool.snapshot()['appearance'])

    def test_handover_is_exclusive_and_preserves_unrelated_entries(self):
        self.active()
        enabled = services(self.device.read(0, 'secure', 'enabled_accessibility_services'))
        self.assertIn(NEW, enabled); self.assertNotIn(OLD, enabled)
        self.assertIn('reader.access/reader.access.Reader', enabled)
        self.assertTrue(self.tool.verify()['ready']); self.assert_protected()

    def test_rollback_restores_every_original_value(self):
        self.active(); self.tool.rollback(); self.assert_restored()
        self.tool.rollback(); self.assert_restored()  # repeatable even after a partially completed recovery

    def test_new_trial_requires_fresh_reboot_evidence_after_rollback(self):
        self.active()
        previous_nonce = self.tool.journal()['nonce']
        journal = self.tool.journal()
        journal.update(reboot_verified=True, reboot_grace_until=time.time() + 90, reboot_start=0)
        self.tool.save(journal)
        self.tool.rollback()
        self.active()
        fresh = self.tool.journal()
        self.assertNotEqual(fresh['nonce'], previous_nonce)
        self.assertFalse(fresh['reboot_verified'])
        self.assertEqual(fresh['reboot_grace_until'], 0)
        self.assertNotIn('reboot_start', fresh)
        with self.assertRaises(Blocked): self.tool.accept(acceptance())
        self.assertFalse(self.device.load()['committed'])
        self.tool.rollback(); self.assert_restored()

    def test_no_watchdog_no_handover(self):
        self.tool.prepare(60, lambda text: None)
        with self.assertRaises(Blocked): self.tool.handover()
        self.assertEqual(self.device.load()['keys'], self.original['keys'])

    def test_stale_watchdog_blocks_before_mutation(self):
        self.prepare(); stamp = self.tool.read_json('watchdog-ready.json'); stamp['time'] -= 20
        private_json(self.folder / 'watchdog-ready.json', stamp)
        with self.assertRaises(Blocked): self.tool.handover()
        self.assertEqual(self.device.load()['keys'], self.original['keys'])

    def test_failed_disarmed_binding_rolls_back_before_old_guard_is_removed(self):
        self.prepare()
        with mock.patch.object(self.tool, 'wait_bound', side_effect=Blocked('Rejected binding')):
            with self.assertRaises(Blocked): self.tool.handover()
        self.assert_restored()
        for command in self.device.load()['commands']:
            if command[0] == 'setting' and command[1].endswith('enabled_accessibility_services'):
                self.assertIn(OLD, services(Setting.load(command[2])))

    def test_receiver_arm_failure_rolls_back(self):
        self.prepare(); self.device.change(control_fail='arm')
        with self.assertRaises(Blocked): self.tool.handover()
        self.assert_restored()

    def test_process_death_after_write_uses_pending_journal(self):
        self.prepare(); self.device.change(die_after_write=True)
        with self.assertRaises(SystemExit): self.tool.handover()
        self.assertIn('pending', self.tool.journal())
        self.tool.rollback(); self.assert_restored()

    def test_watchdog_timeout_restores_original(self):
        self.active(); journal = self.tool.journal(); journal['deadline'] = time.time() - 1; self.tool.save(journal)
        self.tool.watchdog(interval=.01); self.assert_restored()

    def test_watchdog_crash_restores_original(self):
        self.active(); self.device.change(crashes=['new stable crash'])
        self.tool.watchdog(interval=.01); self.assert_restored()

    def test_watchdog_anr_restores_original(self):
        self.active(); self.device.change(anr='new-stable-anr')
        self.tool.watchdog(interval=.01); self.assert_restored()

    def test_failed_first_draw_times_out(self):
        self.device.change(launch_fail=True); self.active()
        journal = self.tool.journal(); journal['ready_deadline'] = time.time() - 1; self.tool.save(journal)
        self.tool.watchdog(interval=.01); self.assert_restored()

    def test_competing_guard_detected_and_recovered(self):
        self.active()
        data = self.device.load(); data['keys']['secure.enabled_accessibility_services']['value'] += ':' + OLD; self.device.change(keys=data['keys'])
        self.tool.watchdog(interval=.01)
        self.assertFalse(self.device.load()['armed']); self.assertNotIn(NEW, services(self.device.read(0, 'secure', 'enabled_accessibility_services')))
        self.assert_protected()

    def test_manual_concurrency_preserves_new_services_and_alarm_options(self):
        self.active(); data = self.device.load()
        data['keys']['secure.enabled_accessibility_services']['value'] += ':another.reader/.Extra'
        data['keys']['global.alarm_manager_constants']['value'] += ',new_option=55'
        self.device.change(keys=data['keys'])
        result = self.tool.rollback()
        self.assertTrue(result['concurrent_changes_preserved'])
        self.assertIn('another.reader/.Extra', self.device.read(0, 'secure', 'enabled_accessibility_services').value)
        self.assertIn('new_option=55', self.device.read(0, 'global', 'alarm_manager_constants').value)
        self.assertNotIn(NEW, services(self.device.read(0, 'secure', 'enabled_accessibility_services')))
        self.assert_protected()

    def test_profile_change_blocks_without_any_writes(self):
        self.prepare(); data = self.device.load(); data['identity']['user'] = 10; self.device.change(identity=data['identity'])
        before = self.device.load()['commands']
        with self.assertRaises(Blocked): self.tool.handover()
        with self.assertRaises(Blocked): self.tool.rollback()
        self.assertEqual(self.device.load()['commands'], before)

    def test_dumbtv_recovery_failure_blocks_before_mutating_settings(self):
        self.device.change(recovery_fail=True)
        with self.assertRaises(Blocked): self.prepare()
        self.assertEqual(self.device.load()['keys'], self.original['keys'])

    def test_waived_acceptance_keeps_reboot_gate_and_records_unmeasured_state(self):
        self.active()
        physical = dict(remote_ok=True, playback_ok=True, vpn_ok=True,
                        settings_ok=True, long_home_ok=True, reboot_ok=True,
                        performance_waived=True)
        with self.assertRaises(Blocked):
            self.tool.accept(physical, waive_performance=True)
        self.assertFalse(self.device.load()['committed'])
        journal = self.tool.journal(); journal['reboot_verified'] = True; self.tool.save(journal)
        self.tool.accept(physical, waive_performance=True)
        self.assertEqual(self.tool.journal()['phase'], 'accepted')
        self.assertTrue(self.tool.journal()['unmeasured_performance_waiver'])
        self.assertNotIn('warm_trials', self.tool.journal()['measurements'])
        self.tool.rollback(); self.assert_restored()

    def test_explicit_acceptance_is_required_and_persists_only_after_verification(self):
        self.active()
        with self.assertRaises(Blocked): self.tool.accept({})
        with self.assertRaises(Blocked): self.tool.accept(acceptance())
        journal = self.tool.journal(); journal['reboot_verified'] = True; self.tool.save(journal)
        self.tool.accept(acceptance())
        self.assertEqual(self.tool.journal()['phase'], 'accepted')
        self.assertTrue(self.device.load()['committed'])
        self.tool.watchdog(interval=.01)  # accepted trial stops the independent watchdog
        self.tool.rollback(); self.assert_restored()

    def test_reboot_grace_is_bounded_and_requires_a_real_generation_change(self):
        self.active(); journal = self.tool.journal(); journal['deadline'] = time.time() + 200; self.tool.save(journal)
        self.tool.reboot_check()
        journal = self.tool.journal()
        self.assertEqual(journal['reboot_start'], 0)
        self.assertLessEqual(journal['reboot_grace_until'], time.time() + 90)
        self.assertFalse(journal['reboot_verified'])
        self.tool.rollback(); self.assert_restored()

    def test_reboot_grace_expiration_still_rolls_back_failed_binding(self):
        self.active(); journal = self.tool.journal()
        journal['reboot_grace_until'] = time.time() - 1; self.tool.save(journal)
        self.device.change(bind_fail=True)
        self.tool.watchdog(interval=.01); self.assert_restored()

    def test_watchdog_observes_reboot_and_ready_before_owner_acceptance(self):
        self.active(); journal = self.tool.journal(); journal['deadline'] = time.time() + 200; self.tool.save(journal)
        self.tool.reboot_check(); self.device.change(boot_count=1)
        fixture = Path(__file__).with_name('watchdog_fixture.py')
        child = subprocess.Popen([sys.executable, str(fixture), 'child', str(self.folder)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            deadline = time.monotonic() + 5
            while time.monotonic() < deadline and not self.tool.journal().get('reboot_verified'): time.sleep(.05)
            self.assertTrue(self.tool.journal()['reboot_verified'])
            self.tool.accept(acceptance())
            self.assertEqual(child.wait(timeout=5), 0)
        finally:
            if child.poll() is None: child.terminate(); child.wait(timeout=5)
        self.tool.rollback(); self.assert_restored()

    def test_stable_crash_uses_own_package_only_fallback(self):
        self.active(); self.device.change(receiver_failed=True)
        self.tool.rollback(); self.assert_restored()
        self.assertIn(['force-stop', STABLE], self.device.load()['commands'])

    def test_independent_process_recovers_after_parent_exits(self):
        fixture = Path(__file__).with_name('watchdog_fixture.py')
        result = subprocess.run([sys.executable, str(fixture), 'parent', str(self.folder)], capture_output=True, text=True, timeout=10)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue((self.folder / 'parent-finished').is_file())
        deadline = time.monotonic() + 8
        while time.monotonic() < deadline and not self.tool.journal()['phase'].startswith('rolled_back'): time.sleep(.1)
        self.assertEqual(self.tool.journal()['phase'], 'rolled_back')
        self.assert_restored()


class BackendTest(unittest.TestCase):
    def test_candidate_apk_identity_and_api_contract_are_checked(self):
        adb = Adb('adb', 'fixture')
        certificate = '1' * 64
        verify = subprocess.CompletedProcess([], 0, stdout='Signer #1 certificate SHA-256 digest: ' + certificate, stderr='')
        valid = "package: name='com.jag.home' versionCode='3'\nsdkVersion:'28'\ntargetSdkVersion:'28'"
        with tempfile.TemporaryDirectory() as temporary:
            apk = Path(temporary) / 'candidate.apk'; apk.write_bytes(b'fixture')
            for data in (valid.replace("com.jag.home'", "com.jag.home.dev'"), valid.replace("sdkVersion:'28'", "sdkVersion:'25'"),
                         valid.replace("targetSdkVersion:'28'", "targetSdkVersion:'31'"), valid.replace("versionCode='3'", "versionCode='1'")):
                badging = subprocess.CompletedProcess([], 0, stdout=data, stderr='')
                with mock.patch('adb_backend.subprocess.run', side_effect=[verify, badging]), self.assertRaises(Blocked): adb.inspect_apk(apk, certificate)
            with mock.patch('adb_backend.subprocess.run', side_effect=[verify, subprocess.CompletedProcess([], 0, stdout=valid, stderr='')]):
                self.assertEqual(len(adb.inspect_apk(apk, certificate)), 64)

    def test_shell_quoting_preserves_empty_values_and_metacharacters(self):
        adb = Adb('adb.exe', 'synthetic-selected-transport')
        result = subprocess.CompletedProcess([], 0, stdout='ok\n', stderr='')
        for value in ('', "a='quote',x=$(literal)", 'literal null'):
            with mock.patch('adb_backend.subprocess.run', return_value=result) as run:
                adb.shell('settings', '--user', 0, 'put', 'global', 'alarm_manager_constants', value)
                args = run.call_args.args[0]
                self.assertEqual(args[:3], ['adb.exe', '-s', 'synthetic-selected-transport'])
                self.assertEqual(shlex.split(args[-1])[-1], value)
                self.assertNotIn('shell', run.call_args.kwargs)

    def test_setting_presence_does_not_confuse_empty_null_or_absent(self):
        adb = Adb('adb', 'fixture')
        for listing, exact, expected in (('', 'null\n', Setting(False)), ('key=\n', '\n', Setting(True, '')),
                                         ('key=null\n', 'null\n', Setting(True, 'null'))):
            with mock.patch.object(adb, 'shell', side_effect=[listing, exact]): self.assertEqual(adb.read(0, 'global', 'key'), expected)
        with mock.patch.object(adb, 'shell', side_effect=['key=first\nother=last\n', 'first\nlast\n']), self.assertRaises(Blocked):
            adb.read(0, 'global', 'key')

    def test_mutation_allowlist_rejects_other_settings(self):
        with self.assertRaises(Blocked): Adb('adb', 'fixture').write({}, 'global', 'unrelated_key', Setting(True, '1'), Setting(False))

    def test_multiple_android_profiles_block(self):
        adb = Adb('adb', 'fixture')
        with mock.patch.object(adb, 'raw', return_value='device'), mock.patch.object(adb, 'shell', side_effect=['0', 'UserInfo{0:Owner:13}\nUserInfo{10:Other:10}']), self.assertRaises(Blocked):
            adb.identity()

    def test_nonzero_sole_profile_also_blocks_legacy_unscoped_commands(self):
        adb = Adb('adb', 'fixture')
        with mock.patch.object(adb, 'raw', return_value='device'), mock.patch.object(adb, 'shell', side_effect=['10', 'UserInfo{10:Other:10}']), self.assertRaises(Blocked):
            adb.identity()
        with self.assertRaises(Blocked): adb.read(10, 'secure', 'accessibility_enabled')

    def test_android9_list_and_delete_use_verified_default_user0(self):
        adb = Adb('adb', 'fixture')
        with mock.patch.object(adb, 'shell', side_effect=['key=\n', '\n']) as shell:
            self.assertEqual(adb.read(0, 'secure', 'key'), Setting(True, ''))
            self.assertEqual(shell.call_args_list[0].args, ('settings', 'list', 'secure'))
            self.assertEqual(shell.call_args_list[1].args, ('settings', '--user', 0, 'get', 'secure', 'key'))
        with mock.patch.object(adb, 'assert_identity'), mock.patch.object(adb, 'read', side_effect=[Setting(True, '0'), Setting(False)]), mock.patch.object(adb, 'shell') as shell:
            adb.write({'user': 0}, 'secure', 'accessibility_enabled', Setting(False), Setting(True, '0'))
            self.assertEqual(shell.call_args.args, ('settings', 'delete', 'secure', 'accessibility_enabled'))


if __name__ == '__main__': unittest.main(verbosity=2)
