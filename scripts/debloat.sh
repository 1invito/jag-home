#!/usr/bin/env bash
# Uninstalls/disables the Amazon bloat and applies the privacy settings.
# Usage: ./scripts/debloat.sh [--dry-run] [-y]
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
parse_flags "$@"
need_device
is_fireos || die "this package list is for Fire OS; see docs/OTHER-TVS.md"
load_conf

step "plan"
note "packages   $(list_of "$DATA/fireos/packages.txt" | wc -l | tr -d ' ')"
note "user apps  $(list_of "$DATA/fireos/user-apps.txt" | wc -l | tr -d ' ') (keeping: ${KEEP_APPS:-none extra})"
note "settings   $(list_of "$DATA/fireos/settings.txt" | wc -l | tr -d ' ')"
if [[ $DRY_RUN == 0 ]]; then
  note "the TV will reboot when this finishes"
  confirm "Remove Amazon ads, telemetry and bloat from $SERIAL?" || die "cancelled"
fi

log="$REPORT/debloat.txt"
: > "$log"

step "Amazon packages"
if [[ $DRY_RUN == 1 ]]; then
  for p in $(list_of "$DATA/fireos/packages.txt"); do
    protected_by_policy "$p" && { printf '  %-46s SKIP (never-disable list)\n' "$p"; continue; }
    keep_pkg "$p" && { printf '  %-46s SKIP (keep list)\n' "$p"; continue; }
    present "$p" && printf '  %-46s would be removed or disabled\n' "$p"
  done
  for p in $(list_of "$DATA/fireos/user-apps.txt") $REMOVE_EXTRA; do
    keep_pkg "$p" && { printf '  %-46s SKIP (keep list)\n' "$p"; continue; }
    present "$p" && printf '  %-46s would be uninstalled\n' "$p"
  done
  printf '\n  dry-run: nothing was changed\n'
  exit 0
fi
removed=0; disabledn=0; protectedn=0; absent=0; suspect=0
for p in $(list_of "$DATA/fireos/packages.txt"); do
  if protected_by_policy "$p"; then
    printf '  %-46s SKIP (never-disable list)\n' "$p"
    continue
  fi
  if keep_pkg "$p"; then
    printf '  %-46s SKIP (keep list)\n' "$p"
    continue
  fi
  if ! present "$p"; then
    printf '  %-46s absent\n' "$p"
    absent=$((absent+1)); continue
  fi
  out=$(acmd pm uninstall -k --user 0 "$p" | head -1)
  case $out in
    Success*) printf '  %-46s removed\n' "$p"; removed=$((removed+1))
              printf '%s\tremoved\n' "$p" >> "$log"; continue ;;
  esac
  out=$(acmd pm disable-user --user 0 "$p" | head -1)
  case $out in
    *disabled-user*) printf '  %-46s disabled\n' "$p"; disabledn=$((disabledn+1))
                     printf '%s\tdisabled\n' "$p" >> "$log" ;;
    *protected*)     printf '  %-46s PROTECTED\n' "$p"; protectedn=$((protectedn+1))
                     printf '%s\tprotected\n' "$p" >> "$log" ;;
    *)               printf '  %-46s SUSPECT: %s\n' "$p" "$out"; suspect=$((suspect+1))
                     printf '%s\tsuspect\t%s\n' "$p" "$out" >> "$log" ;;
  esac
done

step "third-party apps"
for p in $(list_of "$DATA/fireos/user-apps.txt") $REMOVE_EXTRA; do
  if keep_pkg "$p"; then
    printf '  %-46s SKIP (keep list)\n' "$p"; continue
  fi
  if ! present "$p"; then
    printf '  %-46s absent\n' "$p"; continue
  fi
  out=$(acmd pm uninstall --user 0 "$p" | head -1)
  case $out in
    Success*) printf '  %-46s removed\n' "$p"; printf '%s\tremoved\n' "$p" >> "$log" ;;
    *)        printf '  %-46s kept: %s\n' "$p" "$out" ;;
  esac
done

step "processes that survived their own disable"
live=$(dev 'ps -A -o NAME' | tr -d '\r')
for p in $(list_of "$DATA/fireos/packages.txt"); do
  printf '%s\n' "$live" | grep -qx "$p" || continue
  protected_by_policy "$p" && continue
  keep_pkg "$p" && continue
  uid=$(uid_of "$p")
  acmd appops set "$p" RUN_IN_BACKGROUND ignore >/dev/null
  acmd appops set "$p" RUN_ANY_IN_BACKGROUND ignore >/dev/null
  acmd am force-stop "$p" >/dev/null
  if [[ -n $uid && $uid -ge 10000 ]]; then
    acmd cmd netpolicy add restrict-background-blacklist "$uid" >/dev/null
  fi
  printf '  %-46s still running -> ops off, data blocked\n' "$p"
done

step "privacy settings"
while read -r ns key value; do
  [[ -z ${ns:-} || $ns == \#* ]] && continue
  acmd settings put "$ns" "$key" "$value" >/dev/null
  printf '  %-46s %s\n' "$key" "$value"
done < <(list_of "$DATA/fireos/settings.txt" | sed 's/ = / /')

step "summary"
note "removed $removed · disabled $disabledn · protected $protectedn · absent $absent · suspect $suspect"
note "log: ${log#$ROOT/}"
[[ $suspect -gt 0 ]] && note "SUSPECT entries above need a human look before continuing"

if [[ $DRY_RUN == 0 ]]; then
  reboot_wait
  note "home screen now: $(dev 'dumpsys activity activities' | grep -m1 mResumedActivity | sed 's/.*u0 //;s/}.*//')"
  note "if that is not a home screen you can use, run ./scripts/restore.sh"
fi