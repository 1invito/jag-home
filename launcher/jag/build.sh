#!/usr/bin/env bash
# Separate-package development APK. Adapted from dumbtv's MIT SDK build pipeline.
# Requires JDK 17, Android platform 25, build-tools 34.0.0 and zip (Linux/WSL/macOS).
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
mode=${1:-signed}
[[ $mode == signed || $mode == --unsigned ]] || { echo 'usage: build.sh [--unsigned]' >&2; exit 2; }
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
JAR=$SDK/platforms/android-25/android.jar
BT=$SDK/build-tools/34.0.0
[[ -f $JAR && -x $BT/aapt2 ]] || {
  echo "error: set ANDROID_HOME to an SDK containing platforms;android-25 and build-tools;34.0.0" >&2; exit 1;
}
[[ -n ${JAVA_HOME:-} && -x $JAVA_HOME/bin/javac ]] || {
  echo "error: set JAVA_HOME to JDK 17" >&2; exit 1;
}
major=$("$JAVA_HOME/bin/javac" -version 2>&1 | sed -n 's/^javac \([0-9]*\).*/\1/p')
[[ $major == 17 ]] || { echo "error: use JDK 17 (found $major)" >&2; exit 1; }
export JAVA_HOME
KEYSTORE=${JAG_DEV_KEYSTORE:-$PWD/../build/jag-home-dev.keystore}
mkdir -p ../build build
if [[ $mode == signed ]]; then
  [[ -f $KEYSTORE ]] || { echo 'error: retained v1 development keystore missing; no replacement key will be generated. See SIGNING.md.' >&2; exit 1; }
  [[ -n ${JAG_STORE_PASSWORD:-} ]] || { echo 'error: securely supply JAG_STORE_PASSWORD (see SIGNING.md)' >&2; exit 1; }
  export JAG_KEY_PASSWORD=${JAG_KEY_PASSWORD:-$JAG_STORE_PASSWORD}
fi
WORK=$(mktemp -d "${TMPDIR:-/tmp}/jag-home.XXXXXX")
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/gen" "$WORK/classes"
"$BT/aapt2" compile --dir res -o "$WORK/res.zip"
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml "$WORK/res.zip" \
  --min-sdk-version 25 --target-sdk-version 28 --version-code 2 --version-name 0.2-dev \
  --java "$WORK/gen" -o "$WORK/app.apk"
# Run inside WORK with relative paths so javac argument files also support spaces in the checkout.
cp -R src "$WORK/src"
(
  cd "$WORK"
  find src gen -name '*.java' -print > sources.txt
  "$JAVA_HOME/bin/javac" --release 8 -Xlint:all -Werror -encoding UTF-8 \
    -classpath "$JAR" -d classes @sources.txt
  find classes -name '*.class' -print > classes.txt
  "$BT/d8" --release --min-api 25 --lib "$JAR" --output . @classes.txt
  zip -q -X app.apk classes.dex
)
"$BT/zipalign" -f -p 4 "$WORK/app.apk" "$WORK/aligned.apk"
APK=build/jag-home-dev-unsigned.apk
if [[ $mode == signed ]]; then
  "$BT/apksigner" sign --ks "$KEYSTORE" --ks-key-alias "${JAG_KEY_ALIAS:-jagdev}" \
    --ks-pass env:JAG_STORE_PASSWORD --key-pass env:JAG_KEY_PASSWORD \
    --out "$WORK/signed.apk" "$WORK/aligned.apk"
  "$BT/apksigner" verify --verbose "$WORK/signed.apk"
  certificate=$("$BT/apksigner" verify --print-certs "$WORK/signed.apk" | sed -n 's/Signer #1 certificate SHA-256 digest: //p')
  [[ $certificate == 3d61b2c1d0e1ae94dd0dd1fe76c7508ea8c9a8897257be81fbe4d893c0f18939 ]] || {
    echo 'error: certificate differs from the retained cloud v1 APK. No update artifact replaced. See SIGNING.md.' >&2; exit 1;
  }
  APK=build/jag-home-dev.apk
  cp "$WORK/signed.apk" "$APK"
else
  cp "$WORK/aligned.apk" "$APK"
  echo 'UNSIGNED validation artifact: cannot be installed. Sign with the retained v1 key before use.'
fi
"$BT/aapt2" dump badging "$APK" | sed -n '1,4p'
permissions=$("$BT/aapt2" dump permissions "$APK")
if [[ $permissions == *uses-permission* ]]; then echo 'error: development APK must have no permissions' >&2; exit 1; fi
printf 'built %s (%s bytes)\n' "$PWD/$APK" "$(wc -c < "$APK" | tr -d ' ')"
