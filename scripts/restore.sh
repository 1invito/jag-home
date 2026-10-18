#!/usr/bin/env bash
# Undoes the package, app-op, network-policy, launcher and DNS changes.
# Apps that were uninstalled from /data have to be reinstalled from the store.
# Usage: ./scripts/restore.sh [--dry-run] [-y]
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
parse_flags "$@"
need_device

step "plan"
note "re-enable/restore every package in data/fireos/packages.txt"
note "clear app-ops and network blacklists"
note "detach the DNS filter and the launcher hook"
note "put back every setting from report/settings-before-*.txt, or the bundled defaults"
note "the TV reboots when this finishes"
if [[ $DRY_RUN == 0 ]]; then
  confirm "Restore Amazon defaults on $SERIAL?" || die "cancelled"
fi

mkdir -p "$REPORT"

# Prefer the value this device actually had before the pass (snapshotted by
# preflight.sh); fall back to the stock defaults in data/.
restore_value() {
  local ns=$1 key=$2 fallback=$3 snapshot="$REPORT/settings-before-$1.txt" v
  if [[ -f $snapshot ]]; then
    v=$(grep -m1 "^$key=" "$snapshot" 2>/dev/null | cut -d= -f2-)
    [[ -n $v ]] && { printf '%s' "$v"; return; }
  fi
  printf '%s' "$fallback"
}

step "packages"
for p in $(list_of "$DATA/fireos/packages.txt"); do
  a=$(acmd cmd package install-existing "$p" 2>&1 | head -1)
  b=$(acmd pm enable --user 0 "$p" 2>&1 | head -1)
  acmd appops set "$p" RUN_IN_BACKGROUND allow >/dev/null 2>&1
  acmd appops set "$p" RUN_ANY_IN_BACKGROUND allow >/dev/null 2>&1
  uid=$(uid_of "$p")
  [[ -n $uid ]] && acmd cmd netpolicy remove restrict-background-blacklist "$uid" >/dev/null 2>&1
  printf '  %-46s %s / %s\n' "$p" "${a:-no output}" "${b:-no output}"
done

step "packages that were only starved"
if [[ -f $REPORT/debloat.txt ]]; then
  while IFS=$'\t' read -r p state rest; do
    [[ -z ${p:-} ]] && continue
    acmd appops set "$p" RUN_IN_BACKGROUND allow >/dev/null 2>&1
    acmd appops set "$p" RUN_ANY_IN_BACKGROUND allow >/dev/null 2>&1
    uid=$(uid_of "$p")
    [[ -n $uid ]] && acmd cmd netpolicy remove restrict-background-blacklist "$uid" >/dev/null 2>&1
    printf '  %-46s %s\n' "$p" "${state:-unknown}"
  done < "$REPORT/debloat.txt"
else
  note "no report/debloat.txt, relying on protected.txt only"
  for p in $(list_of "$DATA/fireos/protected.txt"); do
    acmd appops set "$p" RUN_IN_BACKGROUND allow >/dev/null 2>&1
    acmd appops set "$p" RUN_ANY_IN_BACKGROUND allow >/dev/null 2>&1
    uid=$(uid_of "$p")
    [[ -n $uid ]] && acmd cmd netpolicy remove restrict-background-blacklist "$uid" >/dev/null 2>&1
    printf '  %-46s cleared\n' "$p"
  done
fi
acmd cmd netpolicy set restrict-background false >/dev/null 2>&1

step "on-device hooks"
# Without this the TV keeps sending DNS through a filter the user just tried to
# undo, and HOME stays hijacked.
acmd settings delete secure always_on_vpn_app >/dev/null 2>&1
acmd settings put secure enabled_accessibility_services "" >/dev/null 2>&1
acmd settings put secure accessibility_enabled 0 >/dev/null 2>&1
alarms=$(restore_value global alarm_manager_constants "")
if [[ -n $alarms && $alarms != null && $alarms != *min_futurity=0* ]]; then
  acmd settings put global alarm_manager_constants "$alarms" >/dev/null 2>&1
else
  acmd settings delete global alarm_manager_constants >/dev/null 2>&1
fi
note "always_on_vpn_app cleared, accessibility hook detached, alarm timing restored (apps stay installed)"

step "settings"
while read -r ns key value; do
  [[ -z ${ns:-} || $ns == \#* ]] && continue
  want=$(restore_value "$ns" "$key" "$value")
  acmd settings put "$ns" "$key" "$want" >/dev/null
  printf '  %-46s %s\n' "$key" "$want"
done < <(list_of "$DATA/fireos/settings.restore.txt" | sed 's/ = / /')

[[ $DRY_RUN == 1 ]] && { note "[dry-run] done"; exit 0; }
reboot_wait
note "restored. dumbtv home and personalDNSfilter are still installed; remove them in"
note "Settings > Applications if you want them gone too."
