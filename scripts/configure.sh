#!/usr/bin/env bash
# The interview. Writes dumbtv.conf (git-ignored), which debloat.sh, dnsfilter.sh,
# launcher.sh and youtube.sh read. Every default is the safe one: no app is removed
# unless it is named, and Prime Video and Alexa keep working.
# Usage: ./scripts/configure.sh [--remove "pkg pkg"] [--keep "pkg pkg"] [--alexa 0|1]
#                               [--system-dns 0|1] [-y] [--dry-run]
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

KEEP_ARG=""; REMOVE_ARG=""; ALEXA_ARG=""; SYSTEM_DNS_ARG=""
while [[ $# -gt 0 ]]; do
  case $1 in
    --keep)       KEEP_ARG=$2; shift 2 ;;
    --remove)     REMOVE_ARG=$2; shift 2 ;;
    --alexa)      ALEXA_ARG=$2; shift 2 ;;
    --system-dns) SYSTEM_DNS_ARG=$2; shift 2 ;;
    -y|--yes)     ASSUME_YES=1; shift ;;
    --dry-run)    DRY_RUN=1; shift ;;
    *) die "unknown flag: $1" ;;
  esac
done

KEEP=${KEEP_ARG:-}
EXTRA=${REMOVE_ARG:-}

# Prompt on stderr, answer on stdout: $() must capture only 0 or 1.
yn() {
  local prompt=$1 def=$2 a
  if [[ $ASSUME_YES == 1 ]]; then printf '%s' "$def"; return; fi
  if [[ $def == 1 ]]; then printf '%s [Y/n] ' "$prompt" >&2; else printf '%s [y/N] ' "$prompt" >&2; fi
  read -r a
  [[ -z $a ]] && { printf '%s' "$def"; return; }
  case $a in y|Y|yes|YES) printf 1 ;; *) printf 0 ;; esac
}

step "what dumbtv does either way"
note "Disables Amazon's ads, sponsored rows, screen fingerprinting (ACR), telemetry and"
note "experiments, and turns off ad tracking. Your apps are not touched."

step "a few choices"
if [[ $(yn "Do you watch Prime Video? (keeps the servers it needs reachable)" 1) == 1 ]]; then
  PRIME=1; note "prime: kept"
else
  PRIME=0; note "prime: its servers are blocked, so it will not play"
fi
if [[ -n $ALEXA_ARG ]]; then
  ALEXA=$ALEXA_ARG
else
  ALEXA=$(yn "Keep Alexa and the remote's voice button? (no = Alexa and the microphone app are switched off)" 1)
fi
LAUNCHER=$(yn "Replace Amazon's home screen with dumbtv home (open source, no ads, no internet access)?" 1)
DNSFILTER=$(yn "Run the DNS filter on the TV (blocks Amazon telemetry and stops automatic updates)?" 1)
if [[ -n $SYSTEM_DNS_ARG ]]; then
  SYSTEM_DNS=$SYSTEM_DNS_ARG
else
  SYSTEM_DNS=$(yn "Fire OS 6 only: let system apps bypass the filter so the clock syncs? Amazon's system-user apps go unfiltered too" 0)
fi
# Default no: YouTube is the app people actually watch. scripts/youtube.sh is the
# only thing that touches it, and only when this is 1.
if [[ $(yn "Replace the YouTube app with SmartTube (no ads, no Shorts)? Your YouTube account and subscriptions are untouched" 0) == 1 ]]; then
  YOUTUBE=1; note "youtube: replaced with SmartTube"
else
  YOUTUBE=0; note "youtube: left alone"
fi

if [[ $ASSUME_YES == 0 && -z $REMOVE_ARG ]]; then
  note "Apps are only removed when you name them here. Names to package ids: data/apps.txt"
  printf 'Apps to remove, space separated package names (Enter for none): ' >&2
  read -r extras
  EXTRA="$EXTRA $extras"
fi
if [[ $ASSUME_YES == 0 && -z $KEEP_ARG ]]; then
  printf 'Anything from the cleanup to keep, space separated package names (Enter for none): ' >&2
  read -r more
  KEEP="$KEEP $more"
fi

KEEP=$(printf '%s' "$KEEP" | xargs)
EXTRA=$(printf '%s' "$EXTRA" | xargs)

# A keep entry that is on no list would protect nothing; it is almost always a typo.
known=$(cat "$DATA"/fireos*/packages.txt "$DATA"/fireos/alexa.txt "$DATA"/fireos*/user-apps.txt 2>/dev/null |
  sed 's/#.*//' | awk 'NF {print $1}')
for k in $KEEP; do
  printf '%s\n' $known $EXTRA | grep -qx -- "$k" ||
    die "keep entry '$k' is on no list: dumbtv never touches it, so leave it out (or fix the typo)"
done
never=$(cat "$DATA"/fireos*/never-disable.txt 2>/dev/null | sed 's/#.*//' | awk 'NF {print $1}')
for r in $EXTRA; do
  printf '%s\n' $never | grep -qx -- "$r" &&
    die "'$r' keeps the TV working (data/fireos*/never-disable.txt), so dumbtv will not remove it"
done

# Not asked: an advanced switch for one filter problem (docs/TROUBLESHOOTING.md). Keep a set value.
FILTER_AUTOSTART=$([[ -f $CONF ]] && sed -n 's/^FILTER_AUTOSTART=//p' "$CONF" | tail -1)
FILTER_AUTOSTART=${FILTER_AUTOSTART:-1}

if [[ $DRY_RUN == 1 ]]; then
  step "would write $CONF"
  note "REMOVE_EXTRA=\"$EXTRA\""
  note "KEEP_APPS=\"$KEEP\""
  note "PRIME=$PRIME ALEXA=$ALEXA LAUNCHER=$LAUNCHER DNSFILTER=$DNSFILTER SYSTEM_DNS=$SYSTEM_DNS YOUTUBE=$YOUTUBE"
  exit 0
fi

mkdir -p "$REPORT"
cat > "$CONF" <<EOF_CONF
# Written by scripts/configure.sh. Git-ignored; it describes this household, not the repo.
REMOVE_EXTRA="$EXTRA"
KEEP_APPS="$KEEP"
PRIME=$PRIME
ALEXA=$ALEXA
LAUNCHER=$LAUNCHER
DNSFILTER=$DNSFILTER
SYSTEM_DNS=$SYSTEM_DNS
YOUTUBE=$YOUTUBE
FILTER_AUTOSTART=$FILTER_AUTOSTART
EOF_CONF

# Prove the file before anyone sources it.
# shellcheck disable=SC1090
source "$CONF"
for v in PRIME ALEXA LAUNCHER DNSFILTER SYSTEM_DNS YOUTUBE FILTER_AUTOSTART; do
  case ${!v} in 0|1) ;; *) die "$v must be 0 or 1 in $CONF" ;; esac
done

step "saved"
note "${CONF#$ROOT/}"
note "remove:  ${EXTRA:-nothing}"
note "keep:    ${KEEP:-nothing extra}"
note "prime=$PRIME alexa=$ALEXA launcher=$LAUNCHER dnsfilter=$DNSFILTER system_dns=$SYSTEM_DNS youtube=$YOUTUBE"
note "change any answer later by running this again"
