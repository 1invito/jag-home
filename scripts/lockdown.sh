#!/usr/bin/env bash
# Takes away background execution and background data from the packages Amazon
# refuses to disable. Usage: ./scripts/lockdown.sh [--dry-run] [-y]
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
parse_flags "$@"
need_device
load_conf

step "network policy"
note "data saver on, then a blacklist entry per protected package"
acmd cmd netpolicy set restrict-background true >/dev/null

for p in $(list_of "$DATA/fireos/protected.txt"); do
  if keep_pkg "$p"; then
    printf '  %-46s SKIP (keep list)\n' "$p"; continue
  fi
  if ! present "$p"; then
    printf '  %-46s absent\n' "$p"; continue
  fi
  uid=$(uid_of "$p")
  if [[ -z $uid || $uid -lt 10000 ]]; then
    printf '  %-46s SKIP (shared system uid %s)\n' "$p" "${uid:-?}"
    continue
  fi
  acmd appops set "$p" RUN_IN_BACKGROUND ignore >/dev/null
  acmd appops set "$p" RUN_ANY_IN_BACKGROUND ignore >/dev/null
  acmd am force-stop "$p" >/dev/null 2>&1
  acmd cmd netpolicy add restrict-background-blacklist "$uid" >/dev/null
  printf '  %-46s ops off, data blocked (uid %s)\n' "$p" "$uid"
done

step "verify"
if [[ $DRY_RUN == 1 ]]; then
  note "skipped in dry-run"
else
  note "data saver: $(dev 'cmd netpolicy get restrict-background' | tr -d '\r')"
  note "blacklist:  $(dev 'cmd netpolicy list restrict-background-blacklist' | tr -d '\r')"
  for p in $(list_of "$DATA/fireos/protected.txt"); do
    present "$p" && note "$(printf '%-44s' "$p") $(dev "appops get $p RUN_ANY_IN_BACKGROUND" | tr -d '\r')"
  done
fi
