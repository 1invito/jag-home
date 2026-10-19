#!/usr/bin/env bash
# Proof that it worked: blocked hosts must answer 0.0.0.0, everyday hosts must
# still resolve, and HOME must land on dumbtv home unless the user kept Amazon's.
# Exit code 0 means every enabled check passed.
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
      *"unknown host"*|*"not known"*|*"cannot resolve"*|"")
        printf '%-10s %-38s no answer\n' FAIL "$h"; fail=1 ;;
      *) printf '%-10s %-38s resolves\n' ok "$h" ;;
    esac
  done < <(list_of "$DATA/blocklists/verify-allowed.txt")
fi

home=$(dev 'dumpsys activity activities' | grep -m1 mResumedActivity | sed 's/.*u0 //;s/}.*//')
wake=$(dev 'dumpsys power' | sed -n 's/.*mWakefulness=\([A-Za-z]*\).*/\1/p' | head -1)
a11y=$(dev 'settings get secure enabled_accessibility_services' | tr -d '\r')
alarms=$(dev 'settings get global alarm_manager_constants' | tr -d '\r')
case $home in
  *tv.dumbtv.home*)     home_state=ok ;;
  *com.amazon.tv.launcher*) home_state=amazon ;;
  *)                    home_state=other ;;
esac
if [[ $LAUNCHER == 0 ]]; then
  printf '%-10s %-38s %s\n' skip home "LAUNCHER=0, not checked"
elif [[ $home_state == amazon && $a11y != *tv.dumbtv.home* ]]; then
  # Amazon's home in front and no hook armed: the launcher really is gone.
  printf '%-10s %-38s %s\n' FAIL home "${home:-unknown} (hook empty)"; fail=1
elif [[ $a11y == *tv.dumbtv.home* ]]; then
  # Hook armed: an app in the foreground is normal, HOME will land on dumbtv home.
  printf '%-10s %-38s %s\n' ok home "armed, front is ${home:-unknown}"
  if [[ $alarms != *min_futurity=0* ]]; then
    # Without it HOME still works, but Amazon's home shows for 5 s first.
    printf '%-10s %-38s %s\n' FAIL home-speed "min_futurity=0 missing; re-run launcher.sh"; fail=1
  fi
elif [[ $wake == Asleep ]]; then
  printf '%-10s %-38s %s\n' FAIL home "asleep and no hook"; fail=1
else
  printf '%-10s %-38s %s\n' FAIL home "${home:-unknown} (wakefulness: ${wake:-?})"; fail=1
fi

printf '%-10s %-38s %s\n' packages "" "$(dev 'pm list packages -d' | wc -l | tr -d ' ') disabled"

if [[ $DNSFILTER == 0 ]]; then
  printf '%-10s %-38s %s\n' skip filter "not installed by choice"
else
  n=$(dev 'grep -c . /sdcard/Android/data/dnsfilter.android/files/PersonalDNSFilter/FILTERHOSTS.TXT 2>/dev/null' | tr -d '\r')
  if [[ ${n:-0} -gt 0 ]] 2>/dev/null; then
    printf '%-10s %-38s %s entries\n' ok filter "$n"
  else
    printf '%-10s %-38s %s\n' FAIL filter "missing or empty"; fail=1
  fi
  vpn=$(dev 'settings get secure always_on_vpn_app' | tr -d '\r')
  if [[ $SDK -lt 28 ]]; then
    # Fire OS 6 forgets always_on_vpn_app at boot; the app starts itself, so check the tunnel.
    if dev 'ip addr show tun0' | grep -q 'tun0:.*UP'; then
      printf '%-10s %-38s %s\n' ok vpn "tun0 up (started by its boot receiver)"
    else
      printf '%-10s %-38s %s\n' FAIL vpn "tun0 down; open personalDNSfilter once, then reboot"; fail=1
    fi
  elif [[ $vpn == dnsfilter.android ]]; then
    printf '%-10s %-38s %s\n' ok vpn "$vpn"
  else
    printf '%-10s %-38s %s\n' FAIL vpn "${vpn:-unset}"; fail=1
  fi
fi

if [[ $LAUNCHER == 0 ]]; then
  printf '%-10s %-38s %s\n' skip alarm "LAUNCHER=0, not checked"
else
  # dumbtv home needs this to answer HOME immediately; an update can drop it.
  if dev 'settings get global alarm_manager_constants' | grep -q 'min_futurity=0'; then
    printf '%-10s %-38s %s\n' ok alarm "min_futurity=0"
  else
    printf '%-10s %-38s %s\n' warn alarm "missing min_futurity=0 (HOME is slower, still works)"
  fi
fi

if [[ $fail == 0 ]]; then
  printf '\nall checks passed\n'
else
  printf '\nfailures above. Recovery steps: docs/GOTCHAS.md\n'
fi
exit $fail
