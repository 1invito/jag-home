#!/usr/bin/env python3
"""A pretend Fire TV for the scripts: answers adb commands from a JSON state file
and logs every shell command, so tests can see exactly what a script would do."""
import json
import os
import sys
from pathlib import Path

args = sys.argv[1:]
if args in (['start-server'], ['get-state']) or args[:1] == ['connect']:
    print('device' if args == ['get-state'] else '')
    sys.exit(0)
if args[:3] != ['-s', 'test-tv', 'shell']:
    sys.exit(91)

command = ' '.join(args[3:])
with Path(os.environ['FAKE_TV_LOG']).open('a') as log:
    log.write(command + '\n')

state_path = Path(os.environ['FAKE_TV_STATE'])
state = json.loads(state_path.read_text())
apps = state['packages']
words = command.split()


def save():
    state_path.write_text(json.dumps(state))


if command == 'getprop ro.build.version.sdk':
    print(state.get('sdk', 28))
elif command == 'getprop ro.product.manufacturer':
    print('Amazon')
elif command == 'getprop sys.boot_completed':
    print(1)
elif command == 'cmd package help':
    print('Package manager (package) commands:\n  path [--user USER_ID] PACKAGE')
elif words[:2] == ['pm', 'path']:
    if words[2] in apps:
        print(f'package:/system/app/{words[2]}.apk')
elif command == 'pm list packages -s':
    print('\n'.join(f'package:{p}' for p, a in apps.items() if a['system']))
elif command == 'pm list packages -d':
    print('\n'.join(f'package:{p}' for p, a in apps.items() if not a['enabled']))
elif words[:4] == ['pm', 'disable-user', '--user', '0']:
    name = words[4]
    if apps.get(name, {}).get('protected'):
        print('Security exception: Cannot disable a protected package')
    else:
        apps[name]['enabled'] = False
        save()
        print(f'Package {name} new state: disabled-user')
elif words[:4] == ['pm', 'uninstall', '--user', '0']:
    apps.pop(words[-1], None)
    save()
    print('Success')
elif words[:2] == ['dumpsys', 'package']:
    print('    userId=10050')
elif command == 'ps -A -o NAME':
    print('NAME')
elif command == 'dumpsys activity activities':
    print('  mResumedActivity: ActivityRecord{1 u0 tv.dumbtv.home/.Home t1}')
