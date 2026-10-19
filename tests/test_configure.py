"""Keep-list validation in configure.sh, without ADB or a television.

Run: python3 -m unittest discover -s tests -v
"""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]


class ConfigureKeepTest(unittest.TestCase):
    def configure(self, *flags):
        with tempfile.TemporaryDirectory() as tmp:
            conf = Path(tmp) / "dumbtv.conf"
            env = dict(os.environ, CONF=str(conf))
            result = subprocess.run(
                ["bash", str(ROOT / "scripts" / "configure.sh"), "-y", *flags],
                env=env, capture_output=True, text=True,
            )
            return result, conf.read_text() if conf.exists() else None

    def test_app_on_removal_list_is_kept(self):
        result, conf = self.configure("--keep", "in.startv.hotstar")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('KEEP_APPS="in.startv.hotstar"', conf)

    def test_amazon_package_is_kept(self):
        result, conf = self.configure("--keep", "com.amazon.tv.acr")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('KEEP_APPS="com.amazon.tv.acr"', conf)

    def test_app_on_no_list_is_refused_before_writing(self):
        result, conf = self.configure("--keep", "com.netflix.ninja")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("com.netflix.ninja", result.stderr)
        self.assertIsNone(conf)

    def test_extra_removal_can_be_kept(self):
        result, conf = self.configure(
            "--keep", "com.netflix.ninja", "--remove", "com.netflix.ninja")
        self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == "__main__":
    unittest.main()
