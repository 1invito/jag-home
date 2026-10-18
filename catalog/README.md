# Device catalog schema (explained)
#
# Every file in catalog/devices/ is one platform or model. This is the part that
# cannot be automated per device — the menu path, where the IP is shown, which
# dialogs ambush you — written down once so an agent never has to rediscover it.
#
# Required keys: id, title, platform, developer_mode, ip_location, data, verify
# Optional:     models, tested_on, adb, prompts, launcher, quirks, known_limits
#
# Run the validator after editing:  python3 catalog/validate.py
#
# JSON schema for tooling lives in schema.json; validate.py is dependency-light
# (PyYAML only) so contributors do not need a JS toolchain.
