"""Actual disarmed service binding test. Refuses non-emulator transports before any write."""
import json
import re
import sys
import time
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'home'))
from adb_backend import Adb
from protocol import Setting, NEW, bound, Blocked

serial, executable, port = sys.argv[1:4]
if not re.fullmatch(r'emulator-\d+', serial): raise SystemExit('Disposable emulator required')
# A private wrapper adds the isolated ADB port while all normal commands retain -s.
class EmulatorAdb(Adb):
    def __init__(self):
        super().__init__(executable, serial)
        self.prefix += ['-P', port]

adb = EmulatorAdb()
if adb.shell('getprop', 'ro.hardware').strip() not in ('ranchu', 'goldfish') or adb.shell('getprop', 'ro.build.version.sdk').strip() != '28':
    raise SystemExit('Disposable Android 9 emulator required')
identity = adb.identity(); user = identity['user']
activity = adb.resolve(user, 'com.jag.home')
adb.start(identity, activity)
assert adb.front() == 'com.jag.home'
old_services = adb.read(user, 'secure', 'enabled_accessibility_services')
old_enabled = adb.read(user, 'secure', 'accessibility_enabled')
staged = Setting(True, (old_services.value + ':' if old_services.present and old_services.value else '') + NEW)
try:
    adb.write(identity, 'secure', 'enabled_accessibility_services', staged, old_services)
    # AccessibilityManager itself can turn this flag on after registration. Read it again.
    current_enabled = adb.read(user, 'secure', 'accessibility_enabled')
    if current_enabled != Setting(True, '1'):
        adb.write(identity, 'secure', 'accessibility_enabled', Setting(True, '1'), current_enabled)
    deadline = time.monotonic() + 15
    while time.monotonic() < deadline:
        status = adb.control(identity, 'status')
        if status['bound']: break
        time.sleep(.25)
    assert status['bound'] and not status['armed'], status
    assert bound(adb.accessibility(), user, 'JAG Home redirector', 'com.jag.home.integration.HomeGuard')
    assert not status['compatible']  # AOSP cannot masquerade as the supported Amazon firmware.
    try:
        adb.control(identity, 'arm', nonce='a' * 32, amazon_class='com.amazon.tv.launcher.ui.HomeActivity_vNext', expiry=int(time.time() * 1000) + 60000)
        raise AssertionError('Non-Fire OS arming was allowed')
    except Blocked: pass
    assert not adb.control(identity, 'status')['armed']
    print('JAG_BINDING_TESTS_PASSED: real activity resolution/foreground, disarmed binding, no-content registration and unsupported-platform refusal')
finally:
    current = adb.read(user, 'secure', 'enabled_accessibility_services')
    adb.write(identity, 'secure', 'enabled_accessibility_services', old_services, current)
    current = adb.read(user, 'secure', 'accessibility_enabled')
    adb.write(identity, 'secure', 'accessibility_enabled', old_enabled, current)
