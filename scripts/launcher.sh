#!/usr/bin/env bash
# Installs Projectivy Launcher and makes it the home screen.
# On Fire OS the HOME button is hijacked through an accessibility service,
# because the Amazon launcher is a protected package and Fire OS ignores
# "default home" settings. Usage: ./scripts/launcher.sh [--dry-run] [-y]
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
parse_flags "$@"
need_device
load_conf

if [[ $LAUNCHER == 0 ]]; then
  note "LAUNCHER=0 in dumbtv.conf — keeping Amazon's home screen. Re-run ./scripts/configure.sh to change that."
  exit 0
fi

PKG=com.spocky.projengmenu
HOME_ACT=$PKG/.ui.home.MainActivity
A11Y=$PKG/$PKG.services.ProjectivyAccessibilityService
APK_URL=https://github.com/spocky/miproja1/releases/download/4.72/ProjectivyLauncher-4.72-c97-xda-release.apk
APK=/tmp/dumbtv-projectivy.apk

step "plan"
note "install Projectivy 4.72 and take over the HOME button"
note "Amazon's launcher stays installed: long-press HOME returns to it (settings escape hatch)"
if [[ $DRY_RUN == 0 ]]; then confirm "Replace the home screen on $SERIAL?" || die "cancelled"; fi

if ! present "$PKG" && [[ $DRY_RUN == 0 ]]; then
  [[ -s $APK ]] || curl -fsSL -o "$APK" "$APK_URL" || die "could not download $APK_URL"
  adb -s "$SERIAL" install -r "$APK" | tail -1
fi

acmd appops set $PKG SYSTEM_ALERT_WINDOW allow >/dev/null
acmd dumpsys deviceidle whitelist +$PKG >/dev/null

if is_fireos; then
  # Toggling off first forces the accessibility manager to rebind a service whose
  # process died; without the toggle a stale entry can leave HOME with Amazon.
  acmd settings put secure enabled_accessibility_services "" >/dev/null
  sleep 2
  acmd settings put secure enabled_accessibility_services "$A11Y" >/dev/null
  acmd settings put secure accessibility_enabled 1 >/dev/null
fi

acmd cmd package set-home-activity "$PKG/$HOME_ACT" >/dev/null 2>&1
acmd am start -n "$HOME_ACT" >/dev/null 2>&1
[[ $DRY_RUN == 1 ]] && { note "[dry-run] done"; exit 0; }

sleep 5
home=$(dev 'dumpsys activity activities' | grep -m1 mResumedActivity | sed 's/.*u0 //;s/}.*//')
if printf '%s' "$home" | grep -q projengmenu; then
  note "home screen is Projectivy"
else
  note "home screen is $home"
  note "press HOME once or run this script again; see docs/GOTCHAS.md if it will not stick"
  exit 1
fi
