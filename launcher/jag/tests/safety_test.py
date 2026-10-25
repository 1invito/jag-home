"""Regression barriers for the separate-package artifact and device safety."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'


class DevelopmentSafety(unittest.TestCase):
    def test_manifest_cannot_take_home_or_start_services(self):
        manifest = ET.parse(ROOT / 'AndroidManifest.xml').getroot()
        self.assertEqual(manifest.attrib['package'], 'com.jag.home.dev')
        self.assertEqual(manifest.findall('uses-permission'), [])
        app = manifest.find('application')
        for tag in ('service', 'receiver', 'provider'):
            self.assertEqual(app.findall(tag), [])
        activities = app.findall('activity')
        self.assertEqual([a.attrib[ANDROID + 'name'] for a in activities], ['.Home', '.Preferences'])
        self.assertEqual(activities[1].attrib[ANDROID + 'exported'], 'false')
        categories = [e.attrib[ANDROID + 'name'] for e in app.iter('category')]
        self.assertNotIn('android.intent.category.HOME', categories)
        self.assertIn('android.intent.category.LEANBACK_LAUNCHER', categories)

    def test_no_process_or_system_mutation_apis(self):
        # Detect calls, including through imported types; comments/documentation are allowed.
        forbidden = re.compile(
            r'\b(killBackgroundProcesses|forceStopPackage|clearApplicationUserData|'
            r'deleteApplicationCacheFiles|setApplicationEnabledSetting|exec|'
            r'getRunningAppProcesses|getRunningTasks|startService|startForegroundService)\s*\('
            r'|\bProcessBuilder\b|\bRuntime\.getRuntime\b|Settings\.(Global|Secure)\.put'
        )
        for source in (ROOT / 'src').rglob('*.java'):
            self.assertIsNone(forbidden.search(source.read_text()), source.name)

    def test_build_has_own_output_and_signing_identity(self):
        build = (ROOT / 'build.sh').read_text()
        self.assertIn('build/jag-home-dev.apk', build)
        self.assertIn('jag-home-dev.keystore', build)
        self.assertNotIn('release.keystore', build)
        self.assertNotRegex(build, r'\badb\b|install\.sh|debloat\.sh|restore\.sh|all\.sh')


if __name__ == '__main__':
    unittest.main(verbosity=2)
