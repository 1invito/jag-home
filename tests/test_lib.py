"""Shared helpers in scripts/lib.sh, run against a fake ADB.

Run: python3 -m unittest discover -s tests -v
"""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]

# Prints the match first, then keeps writing like a long `cmd package help`.
# A reader that stops at the first match makes it die of SIGPIPE, as real adb does.
FAKE_ADB = r'''#!/usr/bin/env bash
case "$*" in
  start-server) exit 0 ;;
  "-s test-tv shell getprop ro.build.version.sdk") echo 30 ;;
  "-s test-tv shell cmd package help")
    echo "  install-existing [--user USER_ID] PACKAGE"
    sleep 0.2
    yes "  help text" | head -c 1000000 ;;
  *) exit 92 ;;
esac
'''


class NeedDeviceTest(unittest.TestCase):
    def test_install_existing_detected_under_pipefail(self):
        with tempfile.TemporaryDirectory() as tmp:
            adb = Path(tmp) / "adb"
            adb.write_text(FAKE_ADB)
            adb.chmod(0o755)
            env = dict(os.environ, ADB=str(adb), ADB_SERIAL="test-tv")
            result = subprocess.run(
                ["bash", "-c",
                 'set -uo pipefail; source "$1"; need_device; echo "REINSTALL=$REINSTALL"',
                 "bash", str(ROOT / "scripts" / "lib.sh")],
                env=env, capture_output=True, text=True,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("REINSTALL=1", result.stdout)


if __name__ == "__main__":
    unittest.main()
