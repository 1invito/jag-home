#!/usr/bin/env python3
"""Owner-operated Windows/Python ADB HOME migration. Never discovers or connects to a device."""
import argparse
import json
import os
from pathlib import Path
import sys
import time

from adb_backend import Adb
from controller import Controller
from protocol import Blocked


def confirm(text):
    if not sys.stdin.isatty():
        raise Blocked('Trial/acceptance requires an interactive owner confirmation; no piped approval')
    print('Type ' + text + ' only after the requested check succeeds: ', end='', flush=True)
    if input().strip() != text:
        raise Blocked('Owner confirmation was not supplied')


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group(required=True)
    for name in ('preflight', 'backup', 'install', 'trial', 'verify', 'rollback', 'status', 'accept', 'reboot-check'):
        modes.add_argument('--' + name, action='store_true')
    modes.add_argument('--watchdog', action='store_true', help=argparse.SUPPRESS)
    parser.add_argument('--serial', default=os.environ.get('ADB_SERIAL'))
    parser.add_argument('--adb', default='adb', help='Explicit adb.exe path, or adb on PATH')
    default = Path(os.environ['LOCALAPPDATA']) / 'JAGHome' / 'migration' if os.name == 'nt' and 'LOCALAPPDATA' in os.environ else Path.cwd() / 'report' / 'jag-home-migration'
    parser.add_argument('--state-dir', type=Path, default=default)
    parser.add_argument('--sdk', default=os.environ.get('ANDROID_HOME'))
    parser.add_argument('--java')
    parser.add_argument('--apk', type=Path)
    parser.add_argument('--owner-cert', help='Public owner stable certificate SHA-256, lowercase without separators')
    parser.add_argument('--appearance-file', type=Path)
    parser.add_argument('--wallpaper-file', type=Path)
    parser.add_argument('--measurements', type=Path)
    parser.add_argument('--seconds', type=int, default=300)
    args = parser.parse_args(argv)
    if not args.serial: parser.error('Select exactly one authorized device using --serial or ADB_SERIAL')
    adb = Adb(args.adb, args.serial, args.sdk, args.java)
    tool = Controller(adb, args.state_dir)
    try:
        if args.preflight:
            result = tool.preflight()
            print(json.dumps({'compatible': True, 'user_verified': True, 'original_guard_bound': True,
                              'recovery_activity_resolved': True, 'other_services_preserved': True}))
        elif args.backup:
            result = tool.backup(args.appearance_file, args.wallpaper_file)
            print(json.dumps({'backup': 'saved privately', 'appearance': result['appearance']}))
        elif args.install:
            if not args.apk or not args.owner_cert: raise Blocked('--install requires --apk and --owner-cert')
            tool.install(args.apk, args.owner_cert)
            print('Stable package installed disarmed. Existing HOME redirection was not changed.')
        elif args.trial:
            print('This trial stages a disarmed Guard, then temporarily replaces only dumbtv Home redirection. Keep this PC awake and ADB available.')
            print('Confirm no other HOME redirector is active, and keep the private backup/recovery commands available.')
            print('Pause other ADB/settings editors: Android 9 settings have no atomic compare-and-swap across independent writers.')
            confirm('TRY JAG')
            tool.prepare(args.seconds, confirm)
            try:
                tool.launch_watchdog(Path(__file__).resolve())
                print('Keep the remote ready: press physical Home if JAG does not appear after staging. First-draw verification has a 15-second window.', flush=True)
                tool.handover()
                ready_deadline = time.monotonic() + 22
                while time.monotonic() < ready_deadline:
                    journal = tool.journal()
                    if journal['phase'] != 'trial': raise Blocked('Watchdog restored the original integration')
                    if journal.get('ready'):
                        print('Temporary JAG trial verified. Test the physical remote/reboot/playback/VPN. It automatically rolls back unless explicitly accepted.')
                        return 0
                    time.sleep(.25)
                raise Blocked('Independent watchdog did not confirm first draw')
            except Exception:
                tool.rollback('trial startup failed')
                raise
        elif args.verify:
            tool.verify()
            print('Verified JAG foreground/first draw, exclusive binding, unchanged package state and no new recorded crash/ANR. Physical remote/VPN tests remain owner checks.')
        elif args.rollback:
            print(json.dumps(tool.rollback()))
        elif args.accept:
            if not args.measurements: raise Blocked('Provide actual owner measurements and physical acceptance checks')
            if args.measurements.stat().st_size > 16384: raise Blocked('Measurements file exceeds 16 KB')
            data = json.loads(args.measurements.read_text(encoding='utf-8-sig'))
            confirm('KEEP JAG')
            tool.accept(data)
            print('Owner accepted JAG. Stable arming persists across reboot; keep the original backup and dumbtv installed.')
        elif args.reboot_check:
            confirm('REBOOT CHECK')
            tool.reboot_check()
            print('Reboot the Fire Stick manually now. A bounded 90-second boot grace is armed; the overall trial timeout still applies.')
        elif args.watchdog:
            tool.watchdog()
        elif args.status:
            if not (args.state_dir / 'snapshot.json').is_file():
                print(json.dumps({'phase': 'no private backup'})); return 0
            journal = tool.journal()
            fresh = False
            if (args.state_dir / 'watchdog-ready.json').is_file():
                stamp = tool.read_json('watchdog-ready.json')
                fresh = stamp.get('nonce') == journal.get('nonce') and time.time() - stamp.get('time', 0) < 8
            snapshot = tool.check_identity()
            summary = {'phase': journal['phase'], 'watchdog_fresh': fresh,
                       'concurrent_change_conflicts': journal.get('conflicts', []), 'selected_user_verified': True,
                       'reboot_verified': journal.get('reboot_verified', False)}
            try:
                status = adb.control(snapshot['identity'], 'status')
                summary.update(stable_armed=status['armed'], stable_bound=status['bound'], original_guard_enabled=status['old_guard_enabled'])
            except Blocked: summary['stable_control'] = 'unavailable or not installed'
            print(json.dumps(summary))
        return 0
    except (Blocked, OSError, ValueError, KeyError) as error:
        # Do not print ADB output, serials, raw settings or credential paths.
        print('Blocked: ' + (str(error) if isinstance(error, Blocked) else 'Invalid/unavailable local data; check the private backup and configuration'), file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
