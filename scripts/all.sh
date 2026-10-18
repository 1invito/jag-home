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

# bash 3.2 (macOS) errors on "${FLAGS[@]}" when the array is empty under set -u.
run() { "$@" ${FLAGS[@]+"${FLAGS[@]}"}; }

run "$DIR/preflight.sh" || exit 1
printf '\n'

if [[ ! -f $CONF ]]; then
  run "$DIR/configure.sh" || exit 1
fi

run "$DIR/debloat.sh"   || exit 1
run "$DIR/lockdown.sh"  || exit 1
run "$DIR/dnsfilter.sh" || exit 1
run "$DIR/launcher.sh"  || exit 1
"$DIR/verify.sh"
