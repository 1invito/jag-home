#!/usr/bin/env bash
# Shared helpers. Sourced by the other scripts; not meant to be run directly.
# Must stay bash 3.2 compatible: that is what /usr/bin/env bash is on macOS.

ADB=${ADB:-adb}
DRY_RUN=${DRY_RUN:-0}
ASSUME_YES=${ASSUME_YES:-0}
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
DATA="$ROOT/data"
REPORT="$ROOT/report"
CONF=${CONF:-$ROOT/dumbtv.conf}
# Both names are accepted; ADB_SERIAL is what the docs tell people to set.
SERIAL=${SERIAL:-${ADB_SERIAL:-}}

# Preferences written by scripts/configure.sh. Absent file = defaults: keep
# nothing extra, PRIME=1, LAUNCHER=1, DNSFILTER=1.
load_conf() {
  KEEP_APPS=${KEEP_APPS:-}
  REMOVE_EXTRA=${REMOVE_EXTRA:-}
  PRIME=${PRIME:-1}
  LAUNCHER=${LAUNCHER:-1}
  DNSFILTER=${DNSFILTER:-1}
  [[ -f $CONF ]] && source "$CONF"
  KEEP_APPS=${KEEP_APPS:-}; REMOVE_EXTRA=${REMOVE_EXTRA:-}
  PRIME=${PRIME:-1}; LAUNCHER=${LAUNCHER:-1}; DNSFILTER=${DNSFILTER:-1}
}

keep_pkg() {
  local k
  for k in $KEEP_APPS; do [[ $k == "$1" ]] && return 0; done
  return 1
}

# Refuse to run when a keep entry matches nothing: a typo would otherwise remove
# the app the user asked to keep.
check_keep_list() {
  local k known
  [[ -z $KEEP_APPS ]] && return 0
  known=$(list_of "$DATA/fireos/packages.txt" | awk '{print $1}')
  known="$known $(list_of "$DATA/fireos/user-apps.txt" | awk '{print $1}') $REMOVE_EXTRA"
  for k in $KEEP_APPS; do
    printf '%s\n' "$known" | tr ' ' '\n' | grep -qx "$k" ||
      die "keep list entry '$k' matches nothing in the removal lists (typo?)"
  done
}

step() { printf '\n\033[1m%s\033[0m\n' "$*"; }
note() { printf '  %s\n' "$*" >&2; }
ask_note() { printf '  %s\n' "$*"; }
die()  { printf 'error: %s\n' "$*" >&2; exit 1; }

dev()  { "$ADB" -s "$SERIAL" shell "$@" 2>&1 </dev/null; }
dev1() { dev "$@" | head -1; }

# A dropped connection must not look like "package absent".
dev_ok() {
  local out
  out=$(dev "$@") || return 1
  printf '%s\n' "$out" | grep -qiE "^error: (device|closed|no devices)|device '[^']*' not found" &&
    die "device $SERIAL stopped responding — reconnect and re-run"
  printf '%s\n' "$out"
}

# Device command that honours --dry-run. The dry-run line goes to stderr so a
# caller's >/dev/null cannot hide it.
acmd() {
  if [[ $DRY_RUN == 1 ]]; then note "[dry-run] $*"; return 0; fi
  dev "$@"
}

confirm() {
  [[ $ASSUME_YES == 1 ]] && return 0
  printf '%s [y/N] ' "$1" >&2
  read -r reply
  case $reply in y|Y|yes|YES) return 0 ;; *) return 1 ;; esac
}

parse_flags() {
  for a in "$@"; do
    case $a in
      --dry-run) DRY_RUN=1 ;;
      -y|--yes)  ASSUME_YES=1 ;;
      *) die "unknown flag: $a" ;;
    esac
  done
}

need_device() {
  command -v "$ADB" >/dev/null || die "adb not found in PATH"
  "$ADB" start-server >/dev/null 2>&1
  if [[ -z $SERIAL ]]; then
    local found count
    found=$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')
    [[ -z $found ]] && die "no adb device. Enable ADB debugging on the TV, then: adb connect <tv-ip>:5555"
    count=$(printf '%s\n' "$found" | wc -l | tr -d ' ')
    [[ $count -gt 1 ]] && die "several devices attached; set ADB_SERIAL to the one you mean: $(printf '%s' "$found" | tr '\n' ' ')"
    SERIAL=$(printf '%s\n' "$found" | head -1)
  fi
  dev getprop ro.build.version.sdk >/dev/null || die "device $SERIAL is not responding"
}

is_fireos()  { case $(dev1 getprop ro.product.manufacturer) in Amazon*) return 0 ;; *) return 1 ;; esac; }
gateway() {
  local gw
  gw=$(dev 'ip route show table all' \
    | awk '/^default/ && / via / && !/linkdown/ && !/ dev tun/ {
             for (i=1;i<=NF;i++) if ($i=="via") { print $(i+1); exit }
           }')
  [[ -z $gw ]] && gw=$(dev 'ip route show table all' \
    | awk '/^default/ && / via / && !/ dev tun/ {
             for (i=1;i<=NF;i++) if ($i=="via") { print $(i+1); exit }
           }')
  [[ -z $gw ]] && gw=$(dev 'dumpsys connectivity' | sed -n 's/.*ServerAddress: \([0-9.]*\).*/\1/p' | head -1)
  [[ -z $gw ]] && gw=$(dev 'getprop dhcp.wlan0.gateway' | tr -d '\r')
  printf '%s' "$gw"
}
uid_of()     { dev "dumpsys package $1" | sed -n 's/.*userId=\([0-9]*\).*/\1/p' | head -1; }
present()    { dev_ok "pm path $1" | grep -q '^package:'; }

reboot_wait() {
  note "rebooting the TV"
  dev reboot >/dev/null 2>&1 || true
  sleep 45
  local i
  for i in $(seq 1 30); do
    "$ADB" connect "$SERIAL" >/dev/null 2>&1 || true
    if [[ $(dev1 getprop sys.boot_completed) == 1 ]]; then
      note "booted"
      return 0
    fi
    sleep 5
  done
  die "device did not report boot_completed; check the TV by hand before running anything else"
}

list_of() { sed 's/#.*//; s/[[:space:]]*$//' "$1" | awk 'NF'; }
# Compare on the first field: list entries carry trailing spaces after comments
# are stripped, and an exact match would silently fail to protect anything.
protected_by_policy() { list_of "$DATA/fireos/never-disable.txt" | awk '{print $1}' | grep -qx "$1"; }

# App name for a package, from data/apps.txt. Names may contain spaces, so match
# the package anywhere in the line and keep whatever precedes it.
app_name() {
  awk -v p="$1" 'index($0,p)==0 {next} {n=substr($0,1,index($0,p)-1); sub(/[[:space:]]+$/,"",n); print n; exit}' \
    "$DATA/apps.txt" 2>/dev/null
}
