#!/usr/bin/env bash
# The whole pass in the right order. Reboots the TV twice, ~5 minutes.
# Usage: ./scripts/all.sh [--dry-run] [-y]
#
# It asks what to keep before touching anything, unless dumbtv.conf already
# exists (then the saved answers are reused) or -y was passed (defaults).
set -uo pipefail
DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
FLAGS=("$@")
CONF="$DIR/../dumbtv.conf"

"$DIR/preflight.sh"
printf '\n'

if [[ ! -f $CONF ]]; then
  "$DIR/configure.sh" "${FLAGS[@]}" || exit 1
fi

"$DIR/debloat.sh"   "${FLAGS[@]}" || exit 1
"$DIR/lockdown.sh"  "${FLAGS[@]}" || exit 1
"$DIR/dnsfilter.sh" "${FLAGS[@]}" || exit 1
"$DIR/launcher.sh"  "${FLAGS[@]}" || exit 1
"$DIR/verify.sh"
