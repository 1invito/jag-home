"""restore.sh against a pretend Fire TV whose adb session drops when the DNS
filter's VPN stops, as it does on a real TV over Wi-Fi.

Run: python3 -m unittest discover -s tests -v
"""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class RestoreTest(unittest.TestCase):
    def run_restore(self, ignores_reboot=False):
        packages = {
            "dnsfilter.android": {"system": False, "enabled": True},
            "com.amazon.tv.acr": {"system": True, "enabled": False},
        }
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            state = tmp / "state.json"
            hooked = {
                "secure/enabled_accessibility_services": "tv.dumbtv.home/tv.dumbtv.home.Guard",
                "secure/accessibility_enabled": "1",
                "secure/always_on_vpn_app": "dnsfilter.android",
                "global/alarm_manager_constants": "min_futurity=0",
            }
            state.write_text(json.dumps({"packages": packages, "settings": hooked,
                                         "ignores_reboot": ignores_reboot}))
            log = tmp / "commands.log"
            log.touch()
            (tmp / "dumbtv.conf").write_text("")
            env = dict(os.environ, ADB=str(ROOT / "tests" / "fake_tv.py"), ADB_SERIAL="test-tv:5555",
                       FAKE_TV_STATE=str(state), FAKE_TV_LOG=str(log),
                       CONF=str(tmp / "dumbtv.conf"), REPORT=str(tmp / "report"), BOOT_SECONDS="0")
            result = subprocess.run(["bash", str(ROOT / "scripts" / "restore.sh"), "-y"],
                                    env=env, capture_output=True, text=True, timeout=120)
            return result, log.read_text().splitlines(), json.loads(state.read_text())

    def test_every_change_reaches_the_tv_before_the_reboot(self):
        result, commands, state = self.run_restore()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        stop_filter = commands.index("pm disable-user --user 0 dnsfilter.android")
        settings = [i for i, c in enumerate(commands) if c.startswith("settings put")]
        self.assertTrue(settings)
        self.assertLess(max(settings), stop_filter)
        self.assertIn("svc power reboot", commands[stop_filter:])
        self.assertNotIn("reboot", commands)
        self.assertEqual(state["boots"], 1)
        self.assertTrue(state["packages"]["com.amazon.tv.acr"]["enabled"])
        self.assertEqual(state.get("bad", []), [])

    def test_home_and_dns_are_handed_back(self):
        result, _, state = self.run_restore()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        settings = state["settings"]
        self.assertNotIn("tv.dumbtv.home", settings.get("secure/enabled_accessibility_services", ""))
        self.assertEqual(settings.get("secure/accessibility_enabled"), "0")
        self.assertNotIn("secure/always_on_vpn_app", settings)
        self.assertNotIn("global/alarm_manager_constants", settings)
        self.assertFalse(state["packages"]["dnsfilter.android"]["enabled"])

    def test_a_reboot_that_never_happened_is_not_reported_as_one(self):
        result, commands, _ = self.run_restore(ignores_reboot=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(commands.count("svc power reboot"), 1)
        self.assertEqual(commands.count("reboot"), 1)
        self.assertIn("did not restart", result.stdout + result.stderr)
        self.assertNotIn("booted", result.stdout)


if __name__ == "__main__":
    unittest.main()
