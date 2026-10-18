#!/usr/bin/env bash
# Shared helpers. Sourced by the other scripts; not meant to be run directly.

ADB=${ADB:-adb}
DRY_RUN=${DRY_RUN:-0}
ASSUME_YES=${ASSUME_YES:-0}
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
DATA="$ROOT/data"
REPORT="$ROOT/report"
CONF=${CONF:-$ROOT/dumbtv.conf}
mkdir -p "$REPORT"

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

# Is package $1 on the keep list? keep_pkg is also used to protect an app the
# user named from the "remove" lists.
keep_pkg() {
  local k
  for k in $KEEP_APPS; do [[ $k == "$1" ]] && return 0; done
  return 1
}

step() { printf '\n\033[1m%s\033[0m\n' "$*"; }
note() { printf '  %s\n' "$*"; }
die()  { printf 'error: %s\n' "$*" >&2; exit 1; }

dev()  { "$ADB" -s "$SERIAL" shell "$@" 2>&1 </dev/null; }
dev1() { dev "$@" | head -1; }

# Device command that honours --dry-run.
acmd() {
  if [[ $DRY_RUN == 1 ]]; then note "[dry-run] $*"; return 0; fi
  dev "$@"
}

confirm() {
  [[ $ASSUME_YES == 1 ]] && return 0
  printf '%s [y/N] ' "$1"
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
  if [[ -z ${SERIAL:-} ]]; then
    local found
    found=$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')
    [[ -z $found ]] && die "no adb device. Enable ADB debugging on the TV, then: adb connect <tv-ip>:5555"
    SERIAL=$(printf '%s\n' "$found" | head -1)
    [[ $(printf '%s\n' "$found" | wc -l | tr -d ' ') -gt 1 ]] &&
      note "several devices attached, using $SERIAL (set ADB_SERIAL to choose)"
  fi
  dev getprop ro.build.version.sdk >/dev/null || die "device $SERIAL is not responding"
}

is_fireos()  { case $(dev1 getprop ro.product.manufacturer) in Amazon*) return 0 ;; *) return 1 ;; esac; }
is_tv()      { dev getprop ro.build.characteristics | grep -qi tv; }
# Default gateway. Android puts a "default" route in a per-network table, not
# in the main table, and a running VPN adds a tun route that must be ignored.
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
present()    { dev "pm path $1" | grep -q '^package:'; }
disabled()   { dev "pm list packages -d" | grep -qx "package:$1"; }

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

# Print a file's package entries: comments and blank lines stripped.
list_of() { sed 's/#.*//' "$1" | awk 'NF'; }

# Is package $1 listed in the never-disable file?
protected_by_policy() { list_of "$DATA/fireos/never-disable.txt" | grep -qx "$1"; }
