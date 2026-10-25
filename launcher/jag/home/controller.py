"""Durable owner-side transaction. The Android app never writes secure/global settings."""
from contextlib import contextmanager
import json
import os
from pathlib import Path
import secrets
import math
import subprocess
import sys
import time

from protocol import (Blocked, Setting, KEYS, OLD, NEW, STABLE, DEV, DUMB, AMAZON, services,
                      stage_services, handover_services, immediate_alarm, restore_services, restore_alarm, bound)


def private_json(path, value):
    path = Path(path)
    temporary = path.with_name(path.name + '.tmp-' + secrets.token_hex(4))
    fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(fd, 'w', encoding='utf-8') as out:
            json.dump(value, out, sort_keys=True, indent=2); out.flush(); os.fsync(out.fileno())
        os.replace(temporary, path)
    finally:
        if temporary.exists(): temporary.unlink()


@contextmanager
def locked(folder, timeout=30):
    """OS advisory lock, automatically released if a parent crashes (also on Windows)."""
    folder = Path(folder); folder.mkdir(parents=True, exist_ok=True, mode=0o700)
    with open(folder / 'transaction.lock', 'a+b') as handle:
        if handle.tell() == 0: handle.write(b'0'); handle.flush()
        deadline = time.monotonic() + timeout
        while True:
            try:
                handle.seek(0)
                if os.name == 'nt':
                    import msvcrt
                    msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
                else:
                    import fcntl
                    fcntl.flock(handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
                break
            except (OSError, BlockingIOError):
                if time.monotonic() >= deadline: raise Blocked('Another migration operation is active')
                time.sleep(.1)
        try: yield
        finally:
            handle.seek(0)
            if os.name == 'nt':
                import msvcrt
                msvcrt.locking(handle.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                import fcntl
                fcntl.flock(handle.fileno(), fcntl.LOCK_UN)


class Controller:
    def __init__(self, backend, folder):
        self.adb = backend; self.folder = Path(folder).resolve()

    def read_json(self, name):
        try:
            value = json.loads((self.folder / name).read_text(encoding='utf-8'))
        except (OSError, ValueError) as error:
            raise Blocked('A valid private backup/journal is required') from error
        if not isinstance(value, dict): raise Blocked('Invalid migration journal')
        return value

    def journal(self): return self.read_json('journal.json')
    def save(self, journal): private_json(self.folder / 'journal.json', journal)
    def snapshot(self): return self.read_json('snapshot.json')

    def settings(self, identity):
        return {namespace + '.' + key: self.adb.read(identity['user'], namespace, key) for namespace, key in KEYS}

    def preflight(self):
        identity = self.adb.identity(); platform = self.adb.platform()
        if platform['sdk'] != '28' or not platform['manufacturer'].startswith('Amazon') or platform['model'] != 'AFTSSS':
            raise Blocked('Supported target is Amazon AFTSSS, Fire OS 7 / API 28 only')
        packages = self.adb.packages(identity['user'])
        if any(package not in packages['installed'] or package in packages['disabled'] for package in (DUMB, DEV)):
            raise Blocked('Both working dumbtv and JAG development packages must remain installed and enabled')
        values = self.settings(identity)
        if OLD not in services(values['secure.enabled_accessibility_services']) or NEW in services(values['secure.enabled_accessibility_services']):
            raise Blocked('Expected the original dumbtv Guard alone; use rollback before another migration')
        if values['secure.accessibility_enabled'] != Setting(True, '1'):
            raise Blocked('Original accessibility is not enabled')
        if not bound(self.adb.accessibility(), identity['user'], 'dumbtv home button', 'tv.dumbtv.home.Guard'):
            raise Blocked('Original dumbtv Guard is not bound')
        home = self.adb.resolve(identity['user'], home=True)
        amazon = self.adb.resolve(identity['user'], 'com.amazon.tv.launcher', home=True)
        if amazon != AMAZON or not home.startswith((DUMB + '/', 'com.amazon.tv.launcher/')):
            raise Blocked('Verified Amazon HOME class/default resolver is incompatible')
        recovery = self.adb.resolve(identity['user'], DUMB)
        immediate_alarm(values['global.alarm_manager_constants'])  # Parse without mutating.
        self.adb.assert_identity(identity)
        if self.settings(identity) != values:
            raise Blocked('Settings changed during preflight; stop other writers and retry')
        return {'identity': identity, 'platform': platform, 'packages': packages,
                'keys': {key: value.json() for key, value in values.items()},
                'home': home, 'amazon': amazon, 'recovery': recovery}

    def backup(self, appearance=None, wallpaper=None):
        with locked(self.folder):
            if (self.folder / 'snapshot.json').exists():
                raise Blocked('Original backup is immutable; select a new private state directory')
            snapshot = self.preflight()
            # No run-as/root/backup of another package's private application data.
            snapshot['appearance'] = 'manual transfer not supplied; v2 private settings are not ADB-readable'
            if appearance:
                source = Path(appearance)
                if source.stat().st_size > 16384: raise Blocked('Appearance file exceeds 16 KB')
                sanitized = appearance_text(source.read_text(encoding='utf-8-sig'), wallpaper is not None)
                # Only a local, explicitly selected appearance file is retained.
                destination = self.folder / 'appearance.properties'
                fd = os.open(destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
                with os.fdopen(fd, 'w', encoding='utf-8') as out: out.write(sanitized)
                snapshot['appearance'] = 'owner-selected appearance.properties'
            if wallpaper:
                if not appearance: raise Blocked('Wallpaper transfer requires a reviewed appearance file')
                source = Path(wallpaper)
                if source.stat().st_size > 32 * 1024 * 1024: raise Blocked('Wallpaper exceeds 32 MB')
                fd = os.open(self.folder / 'wallpaper.png', os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
                with os.fdopen(fd, 'wb') as out: out.write(source.read_bytes())
            snapshot['schema'] = 1
            private_json(self.folder / 'snapshot.json', snapshot)
            self.save({'phase': 'backed_up', 'expected': snapshot['keys'], 'conflicts': [], 'changed': []})
            return snapshot

    def check_identity(self):
        snapshot = self.snapshot(); self.adb.assert_identity(snapshot['identity']); return snapshot

    def unchanged(self, snapshot, expected):
        self.adb.assert_identity(snapshot['identity'])
        if self.settings(snapshot['identity']) != {key: Setting.load(value) for key, value in expected.items()}:
            raise Blocked('Concurrent settings change; no blind overwrite is permitted')
        if self.adb.resolve(snapshot['identity']['user'], home=True) != snapshot['home']:
            raise Blocked('HOME resolver changed; this tool never changes the default activity')

    def install(self, apk, certificate):
        with locked(self.folder):
            snapshot = self.check_identity(); journal = self.journal()
            if journal['phase'] not in ('backed_up', 'installed', 'rolled_back', 'rolled_back_conflict'):
                raise Blocked('Rollback the active handover before updating the stable APK')
            self.unchanged(snapshot, snapshot['keys'])
            checksum = self.adb.inspect_apk(apk, certificate)
            existing = self.adb.packages(snapshot['identity']['user'])
            if STABLE in existing['installed']:
                if self.adb.control(snapshot['identity'], 'status')['armed']:
                    raise Blocked('Installed stable Guard is armed; rollback before updating')
                old_apk = self.folder / 'stable-before-update.apk'
                self.adb.installed_apk(snapshot['identity']['user'], old_apk)
                self.adb.inspect_apk(old_apk, certificate)  # Never uninstall on a signing mismatch.
            self.adb.install(snapshot['identity'], apk)
            installed = self.folder / 'stable-installed.apk'
            self.adb.installed_apk(snapshot['identity']['user'], installed)
            if self.adb.inspect_apk(installed, certificate) != checksum:
                raise Blocked('Installed artifact differs from the verified owner APK')
            self.unchanged(snapshot, snapshot['keys'])
            if self.adb.control(snapshot['identity'], 'status')['armed']:
                raise Blocked('Installation unexpectedly armed HOME; use rollback')
            journal.update(phase='installed', apk_sha256=checksum, certificate=certificate, changed=[])
            self.save(journal)
            if (self.folder / 'appearance.properties').is_file():
                self.adb.stage_appearance(snapshot['identity'], self.folder / 'appearance.properties',
                    self.folder / 'wallpaper.png' if (self.folder / 'wallpaper.png').is_file() else None)

    def verify_installed(self, snapshot, journal):
        if not journal.get('certificate') or not journal.get('apk_sha256'):
            raise Blocked('Install the owner-verified stable APK through --install first')
        destination = self.folder / 'stable-trial-check.apk'
        self.adb.installed_apk(snapshot['identity']['user'], destination)
        if self.adb.inspect_apk(destination, journal['certificate']) != journal['apk_sha256']:
            raise Blocked('Stable package changed after installation')

    def write(self, snapshot, journal, name, value):
        self.ensure_lease(journal)
        self.heartbeat(journal)
        namespace, key = name.split('.', 1)
        previous = Setting.load(journal['expected'][name])
        if value == previous:
            self.adb.assert_identity(snapshot['identity'])
            if self.adb.read(snapshot['identity']['user'], namespace, key) != previous:
                raise Blocked('Concurrent settings change before no-op write')
            return
        # Journal the intent BEFORE issuing the write, so a crash after an applied write is recoverable.
        journal['pending'] = {'key': name, 'before': previous.json(), 'after': value.json()}
        self.save(journal)
        self.adb.write(snapshot['identity'], namespace, key, value, previous)
        journal['expected'][name] = value.json()
        if name not in journal['changed']: journal['changed'].append(name)
        journal.pop('pending', None); self.save(journal)

    @staticmethod
    def ensure_lease(journal):
        if journal.get('phase') not in ('preparing', 'trial') or time.time() >= journal['deadline']:
            raise Blocked('Trial lease expired or was canceled')

    def heartbeat(self, journal):
        stamp = self.read_json('watchdog-ready.json')
        if stamp.get('nonce') != journal['nonce'] or time.time() - stamp.get('time', 0) > 8:
            raise Blocked('Independent watchdog heartbeat missing')

    def prepare(self, seconds, recovery_confirm):
        with locked(self.folder):
            snapshot = self.check_identity(); journal = self.journal()
            if journal['phase'] not in ('installed', 'rolled_back'):
                raise Blocked('Install a verified stable package or finish rollback first')
            if not 60 <= seconds <= 900: raise Blocked('Trial must be between 60 and 900 seconds')
            self.unchanged(snapshot, snapshot['keys']); self.preflight(); self.verify_installed(snapshot, journal)
            self.adb.start(snapshot['identity'], snapshot['recovery'])
            recovery_confirm('DUMBTV WORKS')
            stable_activity = self.adb.resolve(snapshot['identity']['user'], STABLE)
            self.adb.start(snapshot['identity'], stable_activity)
            recovery_confirm('JAG WORKS')
            journal.update(phase='preparing', nonce=secrets.token_hex(16), deadline=time.time() + seconds,
                           expected=snapshot['keys'].copy(), changed=[], conflicts=[], ready=False,
                           reboot_verified=False, reboot_grace_until=0,
                           crash_baseline=self.adb.crash_state())
            journal.pop('reboot_start', None)
            journal.pop('pending', None); self.save(journal)
            return journal['nonce']

    def launch_watchdog(self, cli_path):
        journal = self.journal()
        # A detached process, not a thread owned by the trial CLI or launcher process.
        log_fd = os.open(self.folder / 'watchdog.log', os.O_WRONLY | os.O_CREAT | os.O_APPEND, 0o600)
        args = [sys.executable, str(cli_path), '--watchdog', '--serial', self.adb.serial,
                '--adb', self.adb.executable, '--state-dir', str(self.folder)]
        options = {'stdin': subprocess.DEVNULL, 'stdout': log_fd, 'stderr': log_fd, 'close_fds': True}
        if os.name == 'nt': options['creationflags'] = subprocess.DETACHED_PROCESS | subprocess.CREATE_NEW_PROCESS_GROUP
        else: options['start_new_session'] = True
        try: process = subprocess.Popen(args, **options)
        finally: os.close(log_fd)
        deadline = time.monotonic() + 8
        while time.monotonic() < deadline:
            try:
                stamp = self.read_json('watchdog-ready.json')
                if stamp.get('nonce') == journal['nonce'] and stamp.get('pid') == process.pid and process.poll() is None:
                    return process.pid
            except Blocked: pass
            if process.poll() is not None: break
            time.sleep(.1)
        raise Blocked('Independent watchdog did not become ready; no handover is allowed')

    def handover(self):
        with locked(self.folder):
            snapshot = self.check_identity(); journal = self.journal(); self.ensure_lease(journal)
            stamp = self.read_json('watchdog-ready.json')
            if stamp.get('nonce') != journal['nonce'] or time.time() - stamp['time'] > 8:
                raise Blocked('Independent watchdog heartbeat missing; no settings changed')
            self.unchanged(snapshot, journal['expected'])
            status = self.adb.control(snapshot['identity'], 'status')
            if status['armed'] or not status['compatible']: raise Blocked('Stable Guard must be compatible and disarmed')
            try:
                self.write(snapshot, journal, 'secure.enabled_accessibility_services',
                    stage_services(Setting.load(snapshot['keys']['secure.enabled_accessibility_services'])))
                self.wait_bound(snapshot, True, 8, journal)  # Prove authorization with old Guard still active.
                if self.adb.control(snapshot['identity'], 'status')['armed']: raise Blocked('Disarmed staging unexpectedly armed')
                self.write(snapshot, journal, 'global.alarm_manager_constants',
                    immediate_alarm(Setting.load(snapshot['keys']['global.alarm_manager_constants'])))
                self.write(snapshot, journal, 'secure.enabled_accessibility_services',
                    handover_services(Setting.load(journal['expected']['secure.enabled_accessibility_services'])))
                self.wait_bound(snapshot, False, 8, journal)
                self.unchanged(snapshot, journal['expected'])
                self.heartbeat(journal)
                status = self.adb.control(snapshot['identity'], 'arm', nonce=journal['nonce'],
                    amazon_class=snapshot['amazon'].split('/')[1],
                    expiry=int(self.adb.device_time() * 1000 + (journal['deadline'] - time.time()) * 1000))
                if not status['armed'] or status['old_guard_enabled']: raise Blocked('Exclusive arming failed')
                journal['phase'] = 'trial'; journal['ready_deadline'] = min(time.time() + 15, journal['deadline']); self.save(journal)
                self.adb.press_home(snapshot['identity'])
            except Exception:
                self.rollback_locked(snapshot, journal, 'handover failed')
                raise

    def wait_bound(self, snapshot, old_expected, seconds, journal):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            self.ensure_lease(journal); self.adb.assert_identity(snapshot['identity'])
            dump = self.adb.accessibility()
            old = bound(dump, snapshot['identity']['user'], 'dumbtv home button', 'tv.dumbtv.home.Guard')
            new = bound(dump, snapshot['identity']['user'], 'JAG Home redirector', 'com.jag.home.integration.HomeGuard')
            status = self.adb.control(snapshot['identity'], 'status')
            if new and status['bound'] and old == old_expected: return
            time.sleep(.25)
        raise Blocked('Accessibility binding/authorization failed; rollback required')

    def health(self, snapshot, journal, foreground=False):
        self.adb.assert_identity(snapshot['identity']); self.unchanged(snapshot, journal['expected'])
        status = self.adb.control(snapshot['identity'], 'status')
        if not status['armed'] or not status['bound'] or status['old_guard_enabled'] or status['nonce'] != journal['nonce']:
            raise Blocked('JAG binding/lease is unhealthy or redirectors compete')
        dump = self.adb.accessibility()
        if not bound(dump, snapshot['identity']['user'], 'JAG Home redirector', 'com.jag.home.integration.HomeGuard') or bound(dump, snapshot['identity']['user'], 'dumbtv home button', 'tv.dumbtv.home.Guard'):
            raise Blocked('Expected one active JAG redirector')
        packages = self.adb.packages(snapshot['identity']['user'])
        original = snapshot['packages']
        if packages['disabled'] != original['disabled'] or set(packages['installed']) - {STABLE} != set(original['installed']) - {STABLE}:
            raise Blocked('Unrelated package state changed; no automatic package repairs will be made')
        crash = self.adb.crash_state(); before = journal['crash_baseline']
        if set(crash['crashes']) - set(before['crashes']) or (crash['anr'] and crash['anr'] != before['anr']):
            raise Blocked('New stable JAG crash or ANR detected')
        if foreground and (not status['ready'] or self.adb.front() != STABLE):
            raise Blocked('Press physical Home first; JAG first-draw/foreground evidence is missing')
        return status

    def verify(self):
        with locked(self.folder):
            snapshot = self.check_identity(); journal = self.journal()
            if journal['phase'] not in ('trial', 'accepted'): raise Blocked('There is no active JAG handover to verify')
            if journal['phase'] == 'trial': self.heartbeat(journal)
            return self.health(snapshot, journal, foreground=True)

    def accept(self, measurements):
        with locked(self.folder):
            snapshot = self.check_identity(); journal = self.journal(); self.ensure_lease(journal)
            self.heartbeat(journal)
            validate_measurements(measurements)
            if not journal.get('reboot_verified'): raise Blocked('Use --reboot-check and complete the owner-operated reboot before acceptance')
            self.verify_installed(snapshot, journal)
            self.health(snapshot, journal, foreground=True)
            status = self.adb.control(snapshot['identity'], 'commit', nonce=journal['nonce'])
            if not status['committed'] or not status['armed']: raise Blocked('Stable commit not confirmed')
            journal['phase'] = 'accepted'; journal['measurements'] = measurements; self.save(journal)

    def reboot_check(self):
        with locked(self.folder):
            snapshot = self.check_identity(); journal = self.journal(); self.ensure_lease(journal)
            self.heartbeat(journal); self.health(snapshot, journal, foreground=True)
            if journal['deadline'] - time.time() < 100: raise Blocked('Start a longer trial to allow a safe reboot check')
            journal.update(reboot_start=self.adb.boot_count(), reboot_verified=False,
                           reboot_grace_until=min(time.time() + 90, journal['deadline']))
            self.save(journal)

    def rollback(self, reason='owner requested'):
        with locked(self.folder):
            return self.rollback_locked(self.check_identity(), self.journal(), reason)

    def rollback_locked(self, snapshot, journal, reason):
        self.adb.assert_identity(snapshot['identity'])
        try: self.adb.control(snapshot['identity'], 'disarm')
        except Blocked: self.adb.stop_stable(snapshot['identity'])
        expected = dict(journal['expected'])
        if journal.get('pending'):
            pending = journal['pending']; current = self.settings(snapshot['identity'])[pending['key']]
            if current == Setting.load(pending['after']): expected[pending['key']] = pending['after']
        conflicts = []
        # Remove the new service and restore old membership before changing any alarm state.
        for name in ('secure.enabled_accessibility_services', 'secure.accessibility_enabled', 'global.alarm_manager_constants'):
            namespace, key = name.split('.', 1)
            original = Setting.load(snapshot['keys'][name]); anticipated = Setting.load(expected[name])
            current = self.adb.read(snapshot['identity']['user'], namespace, key)
            if name.endswith('enabled_accessibility_services'):
                desired, conflict = restore_services(original, anticipated, current)
            elif namespace == 'global': desired, conflict = restore_alarm(original, anticipated, current)
            elif current == anticipated or current == original: desired, conflict = original, False
            else: desired, conflict = current, True
            if desired != current:
                self.adb.write(snapshot['identity'], namespace, key, desired, current)
            if conflict: conflicts.append(name)
        if self.adb.resolve(snapshot['identity']['user'], home=True) != snapshot['home']: conflicts.append('home resolver changed outside this tool')
        self.adb.start(snapshot['identity'], self.adb.resolve(snapshot['identity']['user'], DUMB))
        binding_deadline = time.monotonic() + 8
        original_bound = False
        while time.monotonic() < binding_deadline:
            original_bound = bound(self.adb.accessibility(), snapshot['identity']['user'], 'dumbtv home button', 'tv.dumbtv.home.Guard')
            if original_bound: break
            time.sleep(.25)
        if not original_bound: raise Blocked('Settings restored but original Guard binding needs owner recovery verification')
        journal.update(phase='rolled_back_conflict' if conflicts else 'rolled_back', reason=reason,
                       conflicts=conflicts, expected={key: value.json() for key, value in self.settings(snapshot['identity']).items()}, changed=[])
        journal.pop('pending', None); self.save(journal)
        return {'original_guard_bound': True, 'dumbtv_foreground': True,
                'exact_original_settings': not bool(conflicts), 'concurrent_changes_preserved': bool(conflicts)}

    def watchdog(self, interval=2):
        journal = self.journal()
        nonce = journal['nonce']
        while True:
            private_json(self.folder / 'watchdog-ready.json', {'nonce': nonce, 'pid': os.getpid(), 'time': time.time()})
            try:
                with locked(self.folder, timeout=1):
                    snapshot = self.check_identity(); journal = self.journal()
                    if journal.get('nonce') != nonce or journal['phase'] in ('accepted', 'rolled_back', 'rolled_back_conflict'): return
                    if time.time() >= journal['deadline']:
                        self.rollback_locked(snapshot, journal, 'trial timeout'); return
                    if journal['phase'] == 'trial':
                        try:
                            status = self.health(snapshot, journal)
                            if journal.get('reboot_grace_until') and self.adb.boot_count() != journal['reboot_start'] and status['ready']:
                                journal.update(reboot_verified=True, reboot_grace_until=0); self.save(journal)
                            if not journal['ready'] and status['ready'] and self.adb.front() == STABLE:
                                journal['ready'] = True; self.save(journal)
                            if not journal['ready'] and time.time() >= journal['ready_deadline']:
                                raise Blocked('First-draw timeout')
                        except Blocked:
                            recover_now = True
                            if journal.get('reboot_grace_until', 0) > time.time():
                                crash = self.adb.crash_state(); before = journal['crash_baseline']
                                if not (set(crash['crashes']) - set(before['crashes']) or (crash['anr'] and crash['anr'] != before['anr'])):
                                    recover_now = False
                            if recover_now:
                                self.rollback_locked(snapshot, journal, 'watchdog health failure'); return
            except Blocked:
                # No network/device/profile guarantee: persist the failure and retry while the host runs.
                private_json(self.folder / 'watchdog-error.json', {'time': time.time(), 'message': 'ADB, identity, lock or recovery unavailable; automatic rollback not guaranteed'})
            time.sleep(interval)


def validate_measurements(data):
    required = ('remote_ok', 'playback_ok', 'vpn_ok', 'settings_ok', 'long_home_ok', 'reboot_ok', 'comparable_conditions')
    allowed = set(required) | {'warm_trials', 'dumbtv_median_ms', 'jag_median_ms', 'dumbtv_p95_ms', 'jag_p95_ms', 'additional_visible_pss_mib'}
    if not isinstance(data, dict) or set(data) - allowed: raise Blocked('Use only the reviewed measurements template fields')
    if any(data.get(key) is not True for key in required) or type(data.get('warm_trials')) is not int or data['warm_trials'] < 10:
        raise Blocked('Owner acceptance needs real remote/reboot/playback/VPN checks and at least 10 comparable warm trials')
    for suffix in ('median_ms', 'p95_ms'):
        baseline, jag = data.get('dumbtv_' + suffix), data.get('jag_' + suffix)
        if type(baseline) not in (int, float) or type(jag) not in (int, float) or not math.isfinite(baseline) or not math.isfinite(jag) or baseline <= 0 or jag < 0 or jag > baseline * 1.1:
            raise Blocked('Measured Home latency exceeds the 10% budget or has not been supplied')
    extra = data.get('additional_visible_pss_mib')
    if type(extra) not in (int, float) or not math.isfinite(extra) or extra > 15 or extra < -1024:
        raise Blocked('Measured visible PSS budget is missing or exceeds 15 MiB')
    if data['dumbtv_p95_ms'] < data['dumbtv_median_ms'] or data['jag_p95_ms'] < data['jag_median_ms']:
        raise Blocked('Inconsistent median/p95 observations')


def appearance_text(text, wallpaper=False):
    """A deliberately small, non-escaped interchange format; unrelated/private keys are rejected."""
    choices = {
        'theme': ('Cinematic', 'Midnight', 'Graphite', 'Ultra Minimal'), 'dim': ('0', '20', '40', '60', '80'),
        'accent': ('Theme', 'Ice', 'Blue', 'Teal', 'Violet', 'White'), 'tiles': ('Small', 'Medium', 'Large'),
        'labels': ('Shown', 'Hidden'), 'clock': ('Shown', 'Hidden'), 'time': ('24-hour', '12-hour'),
        'animation': ('On', 'Off'), 'reduce.motion': ('On', 'Off')}
    known = {'com.netflix.ninja', 'com.onemainstream.sonyliv.android', 'in.startv.hotstar',
             'com.amazon.firetv.youtube', 'com.fancode.tv', 'com.expressvpn.vpn'}
    values = {}
    for line in text.splitlines():
        if not line.strip() or line.lstrip().startswith('#'): continue
        if '=' not in line: raise Blocked('Use the reviewed appearance.properties template')
        key, value = (item.strip() for item in line.split('=', 1))
        if key in values: raise Blocked('Duplicate appearance key')
        if key == 'order':
            if set(value.split(',')) != known or len(value.split(',')) != 6: raise Blocked('Favorite order must contain exactly the six known apps')
        elif key == 'wallpaper.import':
            if value not in ('true', 'false') or (value == 'true' and not wallpaper): raise Blocked('Staged wallpaper file is required')
        elif key not in choices or value not in choices[key]:
            raise Blocked('Unapproved appearance key/value; no history, private paths or account data may be copied')
        values[key] = value
    if wallpaper: values['wallpaper.import'] = 'true'
    return ''.join(key + '=' + value + '\n' for key, value in values.items())
