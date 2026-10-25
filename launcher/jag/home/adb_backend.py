"""Explicit-transport, bounded ADB operations. No connections, root, package cleanup or profile switching."""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys
from protocol import Blocked, Setting, component, STABLE, OLD, NEW, DEV, DUMB, AMAZON, KEYS


class Adb:
    def __init__(self, executable, serial, sdk=None, java=None):
        self.executable, self.serial = executable, serial
        self.prefix = [sys.executable, executable] if executable.endswith('.py') else [executable]
        self.sdk = Path(sdk or os.environ.get('ANDROID_HOME', ''))
        self.java = java or str(Path(os.environ.get('JAVA_HOME', '')) / 'bin' / ('java.exe' if os.name == 'nt' else 'java'))

    def raw(self, args, timeout=12):
        try:
            done = subprocess.run(self.prefix + ['-s', self.serial] + list(args), capture_output=True,
                                  text=True, encoding='utf-8', errors='replace', timeout=timeout, check=False)
        except (OSError, subprocess.TimeoutExpired) as error:
            raise Blocked('ADB unavailable or timed out; recovery needs the selected transport') from error
        if done.returncode:
            raise Blocked('ADB operation failed; no credential or device output is printed')
        output = done.stdout.replace('\r\n', '\n')
        if re.search(r'^(?:Error:|Exception|Security exception)', output, re.M):
            raise Blocked('Android rejected the requested operation')
        return output

    def shell(self, *args):
        # One POSIX-shell-quoted argument string works through Windows adb.exe as well.
        return self.raw(['shell', shlex.join([str(arg) for arg in args])])

    def identity(self):
        if self.raw(['get-state']).strip() != 'device':
            raise Blocked('Authorize the selected ADB transport before continuing')
        user = int(self.shell('am', 'get-current-user').strip())
        users = [int(value) for value in re.findall(r'UserInfo\{(\d+):', self.shell('pm', 'list', 'users'))]
        if user != 0 or users != [0]:
            raise Blocked('Only the sole Android user 0 is supported; global settings and API 28 list/delete cannot be isolated across profiles')
        hardware = self.shell('getprop', 'ro.serialno').strip()
        fingerprint = self.shell('getprop', 'ro.build.fingerprint').strip()
        if not hardware or not fingerprint:
            raise Blocked('Reliable device identity is unavailable')
        return {'user': user, 'device': hashlib.sha256((hardware + '\0' + fingerprint).encode()).hexdigest()}

    def assert_identity(self, captured):
        if self.identity() != captured:
            raise Blocked('Device or Android user changed; refusing to mutate another profile')

    def platform(self):
        return {key: self.shell('getprop', prop).strip() for key, prop in (
            ('sdk', 'ro.build.version.sdk'), ('manufacturer', 'ro.product.manufacturer'),
            ('model', 'ro.product.model'), ('build', 'ro.build.display.id'))}

    def read(self, user, namespace, key):
        if user != 0: raise Blocked('API 28 setting enumeration is restricted to verified sole user 0')
        # Android 9 SettingsCmd rejects --user for list/delete. Default is user 0,
        # so the tool supports only that verified sole profile rather than silently changing scope.
        lines = self.shell('settings', 'list', namespace).splitlines()
        matches = [line[len(key) + 1:] for line in lines if line.startswith(key + '=')]
        if len(matches) > 1:
            raise Blocked('Duplicate settings output')
        exact = self.shell('settings', '--user', user, 'get', namespace, key)
        exact = exact[:-1] if exact.endswith('\n') else exact
        if '\n' in exact or '\r' in exact:
            raise Blocked('Multiline HOME setting is unsupported; refusing to truncate its value')
        if (matches and exact != matches[0]) or (not matches and exact != 'null'):
            raise Blocked('Setting changed while being read')
        return Setting(bool(matches), exact if matches else '')

    def write(self, identity, namespace, key, value, expected):
        if (namespace, key) not in KEYS: raise Blocked('Setting is outside the HOME transaction allowlist')
        self.assert_identity(identity)
        if self.read(identity['user'], namespace, key) != expected:
            raise Blocked('Concurrent settings change detected before write')
        if value.present:
            self.shell('settings', '--user', identity['user'], 'put', namespace, key, value.value)
        else:
            self.shell('settings', 'delete', namespace, key)
        if self.read(identity['user'], namespace, key) != value:
            raise Blocked('Settings read-back disagrees; rollback is required')

    def packages(self, user):
        def names(flags):
            return sorted(line[8:] for line in self.shell('pm', 'list', 'packages', '--user', user, *flags).splitlines() if line.startswith('package:'))
        return {'installed': names([]), 'disabled': names(['-d'])}

    def resolve(self, user, package=None, home=False):
        args = ['cmd', 'package', 'resolve-activity', '--brief', '--user', str(user), '-a', 'android.intent.action.MAIN',
                '-c', 'android.intent.category.HOME' if home else 'android.intent.category.LEANBACK_LAUNCHER']
        if package:
            args += ['-p', package]
        results = [component(line.strip()) for line in self.shell(*args).splitlines() if re.fullmatch(r'[\w.]+/[\w.$]+', line.strip())]
        if len(results) != 1 or (package and not results[0].startswith(package + '/')):
            raise Blocked('A unique actual launch activity could not be resolved')
        return results[0]

    def start(self, identity, activity):
        self.assert_identity(identity)
        if not activity.startswith((DUMB + '/', STABLE + '/')):
            raise Blocked('Recovery probes may launch only the two expected launchers')
        output = self.shell('am', 'start', '-W', '--user', identity['user'], '-n', activity)
        if 'Status: ok' not in output or self.front() != activity.split('/')[0]:
            raise Blocked('Recovery activity did not become foreground')

    def front(self):
        output = self.shell('dumpsys', 'activity', 'activities')
        found = re.findall(r'(?:mResumedActivity|ResumedActivity):[^\n]*\bu\d+ ([\w.]+)/', output)
        if not found:
            raise Blocked('Foreground activity is unknown')
        return found[0]

    def accessibility(self):
        return self.shell('dumpsys', 'accessibility')

    def control(self, identity, operation, **fields):
        self.assert_identity(identity)
        args = ['am', 'broadcast', '--user', str(identity['user']), '--receiver-foreground', '-n', 'com.jag.home/com.jag.home.integration.HomeControl',
                '--es', 'operation', operation]
        for key, value in fields.items():
            args += ['--el' if isinstance(value, int) else '--es', key, str(value)]
        result = self.shell(*args)
        match = re.search(r'JAG_HOME:([A-Za-z0-9+/=]+)', result)
        if not match:
            raise Blocked('Stable control receiver is unavailable or permission was denied')
        try:
            data = json.loads(base64.b64decode(match[1]).decode())
        except (ValueError, UnicodeError) as error:
            raise Blocked('Invalid stable control response') from error
        if 'error' in data or data.get('protocol') != 1 or data.get('package') != STABLE:
            raise Blocked('Stable HOME control rejected the operation')
        return data

    def press_home(self, identity):
        self.assert_identity(identity); self.shell('input', 'keyevent', 'KEYCODE_HOME')

    def device_time(self):
        return int(self.shell('date', '+%s').strip())

    def boot_count(self):
        value = self.shell('settings', '--user', 0, 'get', 'global', 'boot_count').strip()
        if not value.isdigit(): raise Blocked('Reliable reboot generation is unavailable')
        return int(value)

    def stop_stable(self, identity):
        self.assert_identity(identity)
        self.shell('am', 'force-stop', '--user', identity['user'], STABLE)  # Only our failed stable app, never dumbtv/VPN/system apps.

    def crash_state(self):
        crash = self.shell('logcat', '-d', '-b', 'crash', '-v', 'epoch', '-t', '200')
        lines = [line for line in crash.splitlines() if re.search(r'Process: com\.jag\.home,', line)]
        anr = self.shell('dumpsys', 'activity', 'lastanr')
        return {'crashes': lines, 'anr': hashlib.sha256(anr.encode()).hexdigest() if re.search(r'\bcom\.jag\.home(?:/|\s|$)', anr) else ''}

    def inspect_apk(self, apk, certificate):
        if not re.fullmatch(r'[a-f0-9]{64}', certificate):
            raise Blocked('Provide the public owner certificate SHA-256 digest')
        bt = self.sdk / 'build-tools' / '34.0.0'
        aapt = bt / ('aapt2.exe' if os.name == 'nt' else 'aapt2')
        try:
            verify = subprocess.run([self.java, '-jar', str(bt / 'lib/apksigner.jar'), 'verify', '--print-certs', str(apk)],
                                    check=True, capture_output=True, text=True, timeout=30).stdout
            badging = subprocess.run([str(aapt), 'dump', 'badging', str(apk)], check=True, capture_output=True, text=True, timeout=15).stdout
        except (OSError, subprocess.SubprocessError) as error:
            raise Blocked('APK verification failed; configure owner JDK/SDK tools') from error
        version = re.search(r"versionCode='(\d+)'", badging)
        if ("package: name='com.jag.home'" not in badging or "sdkVersion:'28'" not in badging
                or "targetSdkVersion:'28'" not in badging or version is None or int(version[1]) < 3):
            raise Blocked('Only the stable API 28 JAG package may be installed')
        if certificate == '3d61b2c1d0e1ae94dd0dd1fe76c7508ea8c9a8897257be81fbe4d893c0f18939' or 'Signer #1 certificate SHA-256 digest: ' + certificate not in verify:
            raise Blocked('Expected a dedicated owner stable certificate')
        return hashlib.sha256(Path(apk).read_bytes()).hexdigest()

    def installed_apk(self, user, destination):
        paths = [line[8:] for line in self.shell('pm', 'path', '--user', user, STABLE).splitlines() if line.startswith('package:')]
        if len(paths) != 1 or not paths[0].startswith('/data/app/'):
            raise Blocked('Unexpected stable package layout')
        self.raw(['pull', paths[0], str(destination)])

    def install(self, identity, apk):
        self.assert_identity(identity)
        if 'Success' not in self.raw(['install', '--user', str(identity['user']), '-r', str(apk)], timeout=60):
            raise Blocked('Stable installation failed; no uninstall will be attempted')

    def stage_appearance(self, identity, source, wallpaper=None):
        self.assert_identity(identity)
        folder = '/sdcard/Android/data/com.jag.home/files/migration'
        self.shell('mkdir', '-p', folder)
        self.raw(['push', str(source), folder + '/appearance.properties'])
        if wallpaper:
            self.raw(['push', str(wallpaper), folder + '/wallpaper.png'])
