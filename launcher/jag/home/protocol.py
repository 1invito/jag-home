"""Pure, conservative HOME handover and rollback transformations."""
from dataclasses import dataclass
import re

OLD = 'tv.dumbtv.home/tv.dumbtv.home.Guard'
NEW = 'com.jag.home/com.jag.home.integration.HomeGuard'
STABLE = 'com.jag.home'
DEV = 'com.jag.home.dev'
DUMB = 'tv.dumbtv.home'
AMAZON = 'com.amazon.tv.launcher/com.amazon.tv.launcher.ui.HomeActivity_vNext'
KEYS = (('secure', 'enabled_accessibility_services'), ('secure', 'accessibility_enabled'),
        ('global', 'alarm_manager_constants'))


class Blocked(RuntimeError):
    """A prerequisite or observable conflict prevents a safe operation."""


@dataclass(frozen=True)
class Setting:
    present: bool
    value: str = ''

    def json(self):
        return {'present': self.present, 'value': self.value}

    @staticmethod
    def load(data):
        if type(data.get('present')) is not bool or not isinstance(data.get('value'), str):
            raise Blocked('Invalid setting snapshot')
        return Setting(data['present'], data['value'])


def component(value):
    if not isinstance(value, str) or not re.fullmatch(r'[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+', value):
        raise Blocked('Unrecognized component; no activity names will be guessed')
    package, name = value.split('/')
    return package + '/' + (package + name if name.startswith('.') else name)


def services(setting):
    return [component(item) for item in setting.value.split(':') if item] if setting.present else []


def stage_services(original):
    items = services(original)
    if OLD not in items or NEW in items:
        raise Blocked('Expected only the working dumbtv redirector before staging')
    return Setting(True, original.value + (':' if original.value else '') + NEW)


def handover_services(current):
    items = services(current)
    if OLD not in items or NEW not in items:
        raise Blocked('Both services must be registered while JAG is disarmed')
    return Setting(True, ':'.join(item for item in current.value.split(':') if item and component(item) != OLD))


def alarm_map(setting):
    if not setting.present or not setting.value:
        return {}
    result = {}
    for token in setting.value.split(','):
        if '=' not in token:
            raise Blocked('Unrecognized alarm constants; refusing to rewrite them')
        key, value = token.split('=', 1)
        if not key or key in result:
            raise Blocked('Duplicate or invalid alarm constant')
        result[key] = value
    return result


def immediate_alarm(original):
    values = alarm_map(original)
    if values.get('min_futurity') == '0':
        return original
    tokens = [token for token in original.value.split(',') if token and token.split('=', 1)[0] != 'min_futurity'] if original.present else []
    return Setting(True, ','.join(tokens + ['min_futurity=0']))


def restore_services(original, expected, current):
    if current == expected or current == original:
        return original, False
    # Concurrent additions/removals are kept. Reinstate only our displaced original Guard.
    items = [item for item in current.value.split(':') if item and component(item) != NEW] if current.present else []
    if OLD in services(original) and OLD not in [component(item) for item in items]:
        items.append(next(item for item in original.value.split(':') if item and component(item) == OLD))
    return Setting(True, ':'.join(items)), True


def restore_alarm(original, expected, current):
    if current == expected or current == original:
        return original, False
    if original == expected:  # We never owned an alarm change.
        return current, True
    before, after, now = alarm_map(original), alarm_map(expected), alarm_map(current)
    if now.get('min_futurity') != after.get('min_futurity'):
        return current, True  # Another writer changed our specific key; do not overwrite it.
    tokens = [token for token in current.value.split(',') if token and token.split('=', 1)[0] != 'min_futurity']
    if 'min_futurity' in before:
        tokens.append('min_futurity=' + before['min_futurity'])
    # If a concurrent writer left an empty key, preserve its present/empty distinction.
    return Setting(True, ','.join(tokens)), True


def bound(dump, user, label, full_class):
    """Android 9 services / later Bound services, scoped to the exact current user."""
    for block in dump.split('User state[')[1:]:
        match = re.search(r'attributes:\{id=(\d+)[,}]', block)
        if not match or int(match[1]) != user:
            continue
        section = re.search(r'^\s*(?:Bound services|services):\s*\{', block, re.M)
        if section is None:
            raise Blocked('Accessibility bound-service format is unknown')
        start = section.end(); depth = 1; end = start
        while end < len(block) and depth:
            depth += (block[end] == '{') - (block[end] == '}'); end += 1
        if depth:
            raise Blocked('Truncated accessibility diagnostics')
        content = block[start:end - 1]
        return any('Service[label=' + item + ',' in content for item in (label, full_class))
    raise Blocked('Current user accessibility section not found')
