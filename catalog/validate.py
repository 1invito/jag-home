#!/usr/bin/env python3
"""Validate catalog/devices/*.yaml.

With PyYAML installed the whole file is parsed. Without it, this falls back to a
shallow check (top-level keys, id/filename match, platform, referenced paths) and
says so — CI always has PyYAML, so nothing reaches main unchecked.

Exit codes: 0 valid, 1 problems found.
"""
import pathlib
import re
import sys

try:
    import yaml as _yaml
except ImportError:
    _yaml = None

ROOT = pathlib.Path(__file__).resolve().parent.parent
DEVICES = ROOT / "catalog" / "devices"
REQUIRED = ["id", "title", "platform", "developer_mode", "ip_location", "data", "verify"]
PLATFORMS = {"fireos", "androidtv", "tizen", "webos", "roku", "other"}
WITH_DATA = {"fireos", "androidtv"}
STATUSES = {"tested", "untested", "partial"}
NESTED = {"data", "verify", "adb", "launcher", "prompts", "models", "quirks", "known_limits"}

problems = []
seen = {}


def fail(name, message):
    problems.append(f"{name}: {message}")


def shallow(text):
    """Top-level scalar keys only. Empty scalars stay empty so they fail."""

    doc = {}
    for line in text.splitlines():
        m = re.match(r"^([a-z_]+):\s*(.*?)\s*$", line)
        if m:
            value = m.group(2)
            if not value and m.group(1) in NESTED:
                value = "__nested__"
            doc[m.group(1)] = value
    return doc


def referenced_paths(text):
    for line in text.splitlines():
        m = re.match(r"^\s+([a-z_]+):\s*([A-Za-z0-9_./-]+)\s*$", line)
        if m and m.group(1) in PATH_KEYS:
            yield m.group(1), m.group(2)


PATH_KEYS = {"packages", "protected", "never_disable", "user_apps", "settings",
             "settings_restore", "blocked", "allowed"}


def check(path, doc, refs, full):
    for key in REQUIRED:
        if key not in doc or doc[key] in ("", None, [], {}):
            fail(path.name, f"missing or empty required key: {key}")

    slug = doc.get("id")
    if slug:
        if slug in seen:
            fail(path.name, f"duplicate id, also used by {seen[slug]}")
        seen[slug] = path.name
        if slug != path.stem:
            fail(path.name, f"id '{slug}' must match filename '{path.stem}'")

    platform = doc.get("platform")
    if platform and platform not in PLATFORMS:
        fail(path.name, f"platform '{platform}' not one of {sorted(PLATFORMS)}")

    status = doc.get("status")
    if status not in (None, "") and status not in STATUSES:
        fail(path.name, f"status '{status}' not one of {sorted(STATUSES)}")

    title = doc.get("title") or ""
    if isinstance(title, str) and 0 < len(title) < 3:
        fail(path.name, "title must be at least 3 characters")

    if full:
        adb = doc.get("adb") or {}
        if isinstance(adb, dict) and "port" in adb:
            port = adb["port"]
            if not isinstance(port, int) or not 1 <= port <= 65535:
                fail(path.name, f"adb.port must be an integer 1-65535, got {port!r}")
        for name in ("data", "verify"):
            block = doc.get(name)
            if not isinstance(block, dict):
                fail(path.name, f"{name}: must be a mapping")

    if platform in WITH_DATA and platform:
        data = doc.get("data") or {}
        if isinstance(data, dict) and not data:
            fail(path.name, f"platform '{platform}' needs entries under data:")

    for key, ref in refs:
        if not (ROOT / ref).exists():
            fail(path.name, f"{key} points at a missing file: {ref}")


def main():
    files = sorted(p for p in DEVICES.glob("*.yaml") if not p.name.startswith("_"))
    if not files:
        print("no device files found", file=sys.stderr)
        return 1

    for path in files:
        text = path.read_text()
        if _yaml:
            try:
                doc = _yaml.safe_load(text) or {}
            except _yaml.YAMLError as exc:
                fail(path.name, f"not valid yaml: {exc}")
                continue
            if not isinstance(doc, dict):
                fail(path.name, "top level must be a mapping")
                continue
            data = doc.get("data") if isinstance(doc.get("data"), dict) else {}
            verify = doc.get("verify") if isinstance(doc.get("verify"), dict) else {}
            refs = [(k, v) for k, v in list(data.items()) + list(verify.items())
                    if isinstance(v, str)]
            check(path, doc, refs, full=True)
        else:
            doc = shallow(text)
            refs = list(referenced_paths(text))
            check(path, doc, refs, full=False)

    for line in problems:
        print(f"  {line}", file=sys.stderr)
    if problems:
        print(f"\n{len(problems)} problem(s)", file=sys.stderr)
        return 1

    mode = "full parse" if _yaml else "shallow check — install pyyaml for the full one"
    print(f"{len(files)} device file(s) valid ({mode})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
