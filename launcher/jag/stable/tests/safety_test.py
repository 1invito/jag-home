"""Stable manifest and owner-tool capability boundaries."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
A = '{http://schemas.android.com/apk/res/android}'


class StableSafety(unittest.TestCase):
    def test_separate_application_and_minimal_components(self):
        manifest = ET.parse(ROOT / 'stable/AndroidManifest.xml').getroot()
        self.assertEqual(manifest.get('package'), 'com.jag.home')
        self.assertEqual([item.get(A + 'name') for item in manifest.findall('uses-permission')], ['android.permission.RECEIVE_BOOT_COMPLETED'])
        self.assertNotIn('android.intent.category.HOME', [item.get(A + 'name') for item in manifest.iter('category')])
        service = manifest.find('application/service')
        self.assertEqual(service.get(A + 'permission'), 'android.permission.BIND_ACCESSIBILITY_SERVICE')
        receivers = manifest.findall('application/receiver')
        control = next(item for item in receivers if item.get(A + 'name').endswith('HomeControl'))
        self.assertEqual(control.get(A + 'permission'), 'android.permission.DUMP')
        self.assertEqual(len(manifest.findall('application/service')), 1)

    def test_no_screen_content_or_key_filtering(self):
        xml = ET.parse(ROOT / 'stable/res/xml/jag_guard.xml').getroot()
        self.assertEqual(xml.get(A + 'canRetrieveWindowContent'), 'false')
        self.assertEqual(xml.get(A + 'accessibilityEventTypes'), 'typeWindowStateChanged')
        self.assertNotIn('flagRequestFilterKeyEvents', str(xml.attrib))
        for source in (ROOT / 'stable/src').rglob('*.java'):
            self.assertNotRegex(source.read_text(), r'getRootInActiveWindow|getWindows\(|onKeyEvent|UsageStats|Settings\.(?:Secure|Global)\.put|Runtime\.getRuntime|ProcessBuilder')
            self.assertNotIn('TYPE_ACCESSIBILITY_OVERLAY', source.read_text())

    def test_only_explicit_stable_package_mutation(self):
        backend = (ROOT / 'home/adb_backend.py').read_text()
        self.assertIn("self.shell('am', 'force-stop', '--user', identity['user'], STABLE)", backend)
        self.assertNotRegex(backend, r"['\"](?:uninstall|disable-user|clear|remount|root|set-home-activity)['\"]")
        self.assertNotIn('adb connect', backend)
        self.assertIn("['install', '--user', str(identity['user']), '-r', str(apk)]", backend)

    def test_stable_signing_is_owner_supplied_and_never_auto_generated(self):
        build = (ROOT / 'build-stable.sh').read_text()
        self.assertIn('JAG_STABLE_CERT_SHA256', build)
        self.assertIn('--ks-pass env:JAG_STABLE_STORE_PASSWORD', build)
        self.assertNotIn('-genkeypair', build)
        self.assertNotIn('jag-home-dev.keystore', build)
        self.assertIn('UNSIGNED stable validation artifact', build)

    def test_preview_keeps_noop_integration_and_no_manifest_change(self):
        source = (ROOT / 'src/com/jag/home/dev/HomeIntegration.java').read_text()
        self.assertNotIn('HomeGuard', source)
        self.assertIn('static void arrived(Activity activity) {}', source)
        manifest = ET.parse(ROOT / 'AndroidManifest.xml').getroot()
        self.assertEqual(manifest.get('package'), 'com.jag.home.dev')
        self.assertFalse(manifest.findall('.//service'))


if __name__ == '__main__': unittest.main(verbosity=2)
