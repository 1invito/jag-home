#!/usr/bin/env bash
# Interview: what does this person actually want to keep?
# Writes dumbtv.conf (git-ignored), which debloat.sh, dnsfilter.sh and
# launcher.sh read. Agents must run this before the destructive steps instead of
# guessing. Usage: ./scripts/configure.sh [--keep "pkg pkg"] [--remove "pkg"] [-y]
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

KEEP_ARG=""
REMOVE_ARG=""
while [[ $# -gt 0 ]]; do
  case $1 in
    --keep)   KEEP_ARG=$2; shift 2 ;;
    --remove) REMOVE_ARG=$2; shift 2 ;;
    -y|--yes) ASSUME_YES=1; shift ;;
    --dry-run) DRY_RUN=1; shift ;;
    *) die "unknown flag: $1" ;;
  esac
done

CONF=${CONF:-$ROOT/dumbtv.conf}
KEEP=${KEEP_ARG:-}
EXTRA=${REMOVE_ARG:-}

yn() { # prompt, default 1=yes 0=no
  local prompt=$1 def=$2 a
  if [[ $ASSUME_YES == 1 ]]; then printf '%s' "$def"; return; fi
  if [[ $def == 1 ]]; then printf '%s [Y/n] ' "$prompt"; else printf '%s [y/N] ' "$prompt"; fi
  read -r a
  [[ -z $a ]] && { printf '%s' "$def"; return; }
  case $a in y|Y|yes|YES) printf 1 ;; *) printf 0 ;; esac
}

# app-name lookup, so the questions read as names instead of package ids.
# The name may contain spaces ("Aaj Tak"), so match the package anywhere in the
# line and take everything before it.
app_name() {
  awk -v p="$1" 'index($0,p)==0 {next} {n=substr($0,1,index($0,p)-1); sub(/[[:space:]]+$/,"",n); print n; exit}' \
    "$DATA/apps.txt" 2>/dev/null
}

add_keep() { case " $KEEP " in *" $1 "*) ;; *) KEEP="$KEEP $1" ;; esac; }

step "what stays on the TV"
note "Everything that only does ads, tracking or experiments is removed either way."
note "This step is only about apps a person might actually watch."
printf '\n'

# 1. the third-party apps this repo removes by default
while read -r p; do
  [[ -z $p ]] && continue
  [[ $p == \#* ]] && continue
  name=$(app_name "$p"); name=${name:-$p}
  if [[ $(yn "Remove $name?" 1) == 0 ]]; then
    add_keep "$p"
    printf '  keep   %-34s %s\n' "$name" "$p"
  else
    printf '  remove %-34s %s\n' "$name" "$p"
  fi
done < "$DATA/fireos/user-apps.txt"

# 2. Prime Video is a system app: it is never removed, but its CDN sits in the
#    blocklist, so keeping it means the filter has to leave those hosts alone.
printf '\n'
if [[ $(yn "Do you watch Prime Video? (keeps its CDN out of the filter)" 1) == 1 ]]; then
  PRIME=1; printf '  prime  kept\n'
else
  PRIME=0; printf '  prime  blocked (playback will not work)\n'
fi

# 3. optional pieces
printf '\n'
LAUNCHER=$(yn "Replace Amazon's home screen with Projectivy?" 1)
DNSFILTER=$(yn "Run the on-TV DNS filter (blocks Amazon telemetry and updates)?" 1)

printf '\n'
if [[ $ASSUME_YES == 0 && -z $KEEP_ARG ]]; then
  printf 'Other apps to keep, space separated package names (Enter for none): '
  read -r more
  for p in $more; do add_keep "$p"; done
  printf 'Extra apps to remove, space separated package names (Enter for none): '
  read -r extras
  EXTRA="$EXTRA $extras"
fi

if [[ $DRY_RUN == 1 ]]; then
  step "would write $CONF"
  note "KEEP_APPS=\"$(printf '%s' "$KEEP" | sed 's/^ //')\""
  note "REMOVE_EXTRA=\"$(printf '%s' "$EXTRA" | sed 's/^ //')\""
  note "PRIME=$PRIME LAUNCHER=$LAUNCHER DNSFILTER=$DNSFILTER"
  exit 0
fi

cat > "$CONF" <<EOF_CONF
# Written by scripts/configure.sh. Git-ignored; it describes this household, not the repo.
KEEP_APPS="$(printf '%s' "$KEEP" | sed 's/^ //')"
REMOVE_EXTRA="$(printf '%s' "$EXTRA" | sed 's/^ //')"
PRIME=$PRIME
LAUNCHER=$LAUNCHER
DNSFILTER=$DNSFILTER
EOF_CONF

step "saved"
note "${CONF#$ROOT/}"
note "keep:     $(printf '%s' "${KEEP:-none}" | sed 's/^ //')"
note "remove:   $(printf '%s' "${EXTRA:-nothing extra}" | sed 's/^ //')"
note "prime=$PRIME launcher=$LAUNCHER dnsfilter=$DNSFILTER"
note "change any answer later by running this again"
