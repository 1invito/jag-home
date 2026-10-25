#!/usr/bin/env bash
# Negative signing tests only. Creates a temporary unrelated key, never deploys anything.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
BT=$SDK/build-tools/34.0.0
[[ -n ${JAG_STORE_PASSWORD:-} ]] || { echo 'provide the private signing environment first' >&2; exit 1; }
[[ -f build/jag-home-dev.apk ]] || { echo 'build the valid signed APK first' >&2; exit 1; }
export JAG_KEY_PASSWORD=${JAG_KEY_PASSWORD:-$JAG_STORE_PASSWORD}
WORK=$(mktemp -d "${TMPDIR:-/tmp}/jag-signing.XXXXXX")
trap 'rm -rf "$WORK"' EXIT
before=$(sha256sum build/jag-home-dev.apk | cut -d' ' -f1)
if JAG_DEV_KEYSTORE="$WORK/missing.keystore" ./build.sh > "$WORK/missing.log" 2>&1; then
  echo 'error: missing key unexpectedly succeeded' >&2; exit 1
fi
[[ ! -e $WORK/missing.keystore ]]
grep -q 'retained v1 development keystore missing' "$WORK/missing.log"
if env -u JAG_STORE_PASSWORD -u JAG_KEY_PASSWORD ./build.sh > "$WORK/password.log" 2>&1; then
  echo 'error: missing password unexpectedly succeeded' >&2; exit 1
fi
grep -q 'securely supply JAG_STORE_PASSWORD' "$WORK/password.log"
"$JAVA_HOME/bin/keytool" -genkeypair -keystore "$WORK/wrong.keystore" -alias jagdev \
  -keyalg RSA -keysize 2048 -validity 2 -dname 'CN=Disposable signing regression fixture' \
  -storepass:env JAG_STORE_PASSWORD -keypass:env JAG_KEY_PASSWORD > "$WORK/keytool.log" 2>&1
if JAG_DEV_KEYSTORE="$WORK/wrong.keystore" ./build.sh > "$WORK/wrong.log" 2>&1; then
  echo 'error: unrelated signer unexpectedly accepted' >&2; exit 1
fi
grep -q 'certificate differs' "$WORK/wrong.log"
after=$(sha256sum build/jag-home-dev.apk | cut -d' ' -f1)
[[ $before == "$after" ]] || { echo 'error: valid signed artifact was replaced on failure' >&2; exit 1; }
./build.sh --unsigned > "$WORK/unsigned.log" 2>&1
if "$BT/apksigner" verify build/jag-home-dev-unsigned.apk > "$WORK/verify.log" 2>&1; then
  echo 'error: validation artifact was unexpectedly signed' >&2; exit 1
fi
echo 'Signing guards passed: missing key/password and wrong certificate rejected; valid artifact preserved; CI artifact unsigned.'
