#!/usr/bin/env bash
# Proof that it worked: blocked hosts must answer 0.0.0.0, everyday hosts must
# still resolve, and the home screen must be Projectivy unless the user said to
# keep Amazon's. Exit code 0 means every enabled check passed.
# Usage: ./scripts/verify.sh
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
parse_flags "$@"
need_device
load_conf

fail=0
printf '\n%-10s %-38s %s\n' '' host result
printf '%-10s %-38s %s\n' '----------' '--------------------------------------' '----------------------'

if [[ $DNSFILTER == 0 ]]; then
  printf '%-10s %-38s %s\n' skip dns "DNSFILTER=0, not checked"
else
  while read -r h; do
    line=$(dev "ping -c1 -W2 $h" | head -1)
    case $line in
      *0.0.0.0*|*127.0.0.1*) printf '%-10s %-38s blocked\n' ok "$h" ;;
      *) printf '%-10s %-38s NOT BLOCKED\n' FAIL "$h"; fail=1 ;;
    esac
  done < <(list_of "$DATA/blocklists/verify-blocked.txt")

  while read -r h; do
    line=$(dev "ping -c1 -W2 $h" | head -1)
    case $line in
      *0.0.0.0*|*127.0.0.1*) printf '%-10s %-38s WRONGLY BLOCKED\n' FAIL "$h"; fail=1 ;;
      "") printf '%-10s %-38s no answer\n' FAIL "$h"; fail=1 ;;
      *) printf '%-10s %-38s resolves\n' ok "$h" ;;
    esac
  done < <(list_of "$DATA/blocklists/verify-allowed.txt")
fi

home=$(dev 'dumpsys activity activities' | grep -m1 mResumedActivity | sed 's/.*u0 //;s/}.*//')
wake=$(dev 'dumpsys power' | sed -n 's/.*mWakefulness=\([A-Za-z]*\).*/\1/p' | head -1)
a11y=$(dev 'settings get secure enabled_accessibility_services' | tr -d '\r')
if [[ $LAUNCHER == 0 ]]; then
  printf '%-10s %-38s %s\n' skip home "LAUNCHER=0, not checked"
elif printf '%s' "$home" | grep -q projengmenu; then
  printf '%-10s %-38s %s\n' ok home "$home"
elif [[ $wake == Asleep && $a11y == *projengmenu* ]]; then
  printf '%-10s %-38s %s\n' ok home "armed, screen off"
else
  printf '%-10s %-38s %s\n' FAIL home "${home:-unknown} (wakefulness: ${wake:-?})"
  fail=1
fi

printf '%-10s %-38s %s\n' packages "" "$(dev 'pm list packages -d' | wc -l | tr -d ' ') disabled"
if [[ $DNSFILTER == 0 ]]; then
  printf '%-10s %-38s %s\n' skip filter "not installed by choice"
else
  printf '%-10s %-38s %s\n' ok filter \
    "$(dev 'grep -c . /sdcard/Android/data/dnsfilter.android/files/PersonalDNSFilter/FILTERHOSTS.TXT 2>/dev/null' | tr -d '\r') entries"
  printf '%-10s %-38s %s\n' ok vpn "$(dev 'settings get secure always_on_vpn_app' | tr -d '\r')"
fi

if [[ $fail == 0 ]]; then
  printf '\nall checks passed\n'
else
  printf '\nfailures above. Recovery steps: docs/GOTCHAS.md\n'
fi
exit $fail
