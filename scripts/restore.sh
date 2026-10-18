#!/usr/bin/env bash
# Undoes the package, app-op, network-policy and settings changes.
# Apps that were uninstalled from /data have to be reinstalled from the store.
# Usage: ./scripts/restore.sh [--dry-run] [-y]
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
parse_flags "$@"
need_device

step "plan"
note "re-enable/restore every package in data/fireos/packages.txt"
note "clear app-ops and network blacklists for data/fireos/protected.txt"
note "put back every setting from data/fireos/settings.restore.txt"
note "the TV reboots when this finishes"
if [[ $DRY_RUN == 0 ]]; then confirm "Restore Amazon defaults on $SERIAL?" || die "cancelled"; fi

step "packages"
for p in $(list_of "$DATA/fireos/packages.txt"); do
  a=$(acmd cmd package install-existing "$p" | head -1)
  b=$(acmd pm enable --user 0 "$p" | head -1)
  printf '  %-46s %s / %s\n' "$p" "${a:-no output}" "${b:-no output}"
done

step "network policy"
for p in $(list_of "$DATA/fireos/protected.txt"); do
  acmd appops set "$p" RUN_IN_BACKGROUND allow >/dev/null
  acmd appops set "$p" RUN_ANY_IN_BACKGROUND allow >/dev/null
  uid=$(uid_of "$p")
  [[ -n $uid ]] && acmd cmd netpolicy remove restrict-background-blacklist "$uid" >/dev/null
done
acmd cmd netpolicy set restrict-background false >/dev/null

# Prefer the value this device actually had before the pass (snapshotted by
# preflight.sh); fall back to the stock defaults in data/.
restore_value() {
  local ns=$1 key=$2 fallback=$3 snapshot=$REPORT/settings-before-$1.txt v
  if [[ -f $snapshot ]]; then
    v=$(grep -m1 "^$key=" "$snapshot" | cut -d= -f2-)
    [[ -n $v ]] && { printf '%s' "$v"; return; }
  fi
  printf '%s' "$fallback"
}

step "settings"
while read -r ns key value; do
  [[ -z ${ns:-} || $ns == \#* ]] && continue
  want=$(restore_value "$ns" "$key" "$value")
  acmd settings put "$ns" "$key" "$want" >/dev/null
  printf '  %-46s %s\n' "$key" "$want"
done < <(list_of "$DATA/fireos/settings.restore.txt" | sed 's/ = / /')

[[ $DRY_RUN == 1 ]] && { note "[dry-run] done"; exit 0; }
reboot_wait
note "restored. The DNS filter and Projectivy are still installed; remove them in"
note "Settings > Applications if you want them gone too."
