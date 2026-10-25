"""Local model of the three settings and explicit stable-only commands. No ADB/network access."""
import hashlib
import json
from pathlib import Path
import time
from controller import private_json, locked
from protocol import Setting, Blocked, OLD, NEW, STABLE, DEV, DUMB, AMAZON, services


class FakeDevice:
    def __init__(self, path): self.path = Path(path)

    @classmethod
    def create(cls, folder):
        path = Path(folder) / 'device' / 'state.json'; path.parent.mkdir(parents=True)
        private_json(path, {
            'identity': {'user': 0, 'device': 'synthetic-device-hash'},
            'platform': {'sdk': '28', 'manufacturer': 'Amazon', 'model': 'AFTSSS', 'build': 'fixture'},
            'keys': {'secure.enabled_accessibility_services': Setting(True, 'reader.access/.Reader:tv.dumbtv.home/.Guard').json(),
                     'secure.accessibility_enabled': Setting(True, '1').json(),
                     'global.alarm_manager_constants': Setting(True, 'other=77,min_futurity=5000').json()},
            'installed': sorted([DUMB, DEV, 'com.amazon.tv.launcher', 'com.expressvpn.vpn', 'dnsfilter.android', 'com.android.systemui']),
            'disabled': ['com.amazon.mp3'], 'home': AMAZON, 'front': DUMB,
            'armed': False, 'nonce': '', 'committed': False, 'ready': False,
            'crashes': [], 'anr': '', 'commands': []})
        return cls(path)

    def load(self): return json.loads(self.path.read_text())
    def change(self, **fields):
        with locked(self.path.parent):
            state = self.load(); state.update(fields); private_json(self.path, state)
    def identity(self):
        data = self.load()
        if data.get('offline'): raise Blocked('Synthetic offline transport')
        return data['identity']
    def assert_identity(self, captured):
        if self.identity() != captured: raise Blocked('Device/profile changed')
    def platform(self): return self.load()['platform']
    def read(self, user, namespace, key): return Setting.load(self.load()['keys'][namespace + '.' + key])
    def write(self, identity, namespace, key, value, expected):
        self.assert_identity(identity)
        with locked(self.path.parent):
            data = self.load(); name = namespace + '.' + key
            if Setting.load(data['keys'][name]) != expected: raise Blocked('Concurrent edit')
            data['keys'][name] = value.json(); data['commands'].append(['setting', name, value.json()])
            die = data.get('die_after_write') and not data.get('died')
            data['died'] = data.get('died', False) or die
            private_json(self.path, data)
            if die: raise SystemExit('Synthetic parent death after applied write')
    def packages(self, user):
        data = self.load(); return {'installed': data['installed'], 'disabled': data['disabled']}
    def resolve(self, user, package=None, home=False):
        data = self.load()
        if home: return AMAZON if package == 'com.amazon.tv.launcher' else data['home']
        if package not in data['installed']: raise Blocked('Missing package')
        return package + '/fixture.Recovery'
    def start(self, identity, activity):
        self.assert_identity(identity)
        if self.load().get('recovery_fail') and activity.startswith(DUMB): raise Blocked('Recovery activity failed')
        self.change(front=activity.split('/')[0])
    def front(self): return self.load()['front']
    def accessibility(self):
        data = self.load(); items = services(Setting.load(data['keys']['secure.enabled_accessibility_services']))
        labels = []
        if OLD in items: labels.append('Service[label=dumbtv home button, synthetic]')
        if NEW in items and not data.get('bind_fail'): labels.append('Service[label=JAG Home redirector, synthetic]')
        return 'User state[\n  attributes:{id=0, currentUser=true}\n  services:{' + ', '.join(labels) + '}\n]'
    def control(self, identity, operation, **fields):
        self.assert_identity(identity)
        data = self.load()
        if data.get('receiver_failed') or data.get('control_fail') == operation: raise Blocked('Synthetic receiver failure')
        registered = NEW in services(Setting.load(data['keys']['secure.enabled_accessibility_services']))
        if operation == 'arm':
            if OLD in services(Setting.load(data['keys']['secure.enabled_accessibility_services'])) or not registered: raise Blocked('Two redirectors')
            self.change(armed=True, nonce=fields['nonce'], ready=False)
        elif operation == 'disarm': self.change(armed=False, ready=False, committed=False)
        elif operation == 'commit': self.change(committed=True)
        data = self.load()
        return {'protocol': 1, 'package': STABLE, 'compatible': True, 'armed': data['armed'],
                'bound': registered and not data.get('bind_fail'), 'ready': data['ready'],
                'nonce': data['nonce'], 'committed': data['committed'],
                'old_guard_enabled': OLD in services(Setting.load(data['keys']['secure.enabled_accessibility_services']))}
    def press_home(self, identity):
        self.assert_identity(identity)
        if not self.load().get('launch_fail'): self.change(front=STABLE, ready=True)
    def stop_stable(self, identity):
        self.assert_identity(identity)
        data = self.load(); data['commands'].append(['force-stop', STABLE]); data.update(armed=False, ready=False); private_json(self.path, data)
    def crash_state(self):
        data = self.load(); return {'crashes': data['crashes'], 'anr': data['anr']}
    def device_time(self): return int(time.time())
    def boot_count(self): return self.load().get('boot_count', 0)
    def inspect_apk(self, apk, certificate):
        if certificate != '1' * 64: raise Blocked('Wrong certificate')
        return hashlib.sha256(Path(apk).read_bytes()).hexdigest()
    def installed_apk(self, user, destination): Path(destination).write_bytes(b'stable-fixture')
    def install(self, identity, apk):
        self.assert_identity(identity)
        self.change(installed=sorted(set(self.load()['installed'] + [STABLE])))
    def stage_appearance(self, identity, source, wallpaper=None): self.assert_identity(identity)
