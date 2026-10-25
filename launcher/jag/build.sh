#!/usr/bin/env bash
# Separate-package development APK. Adapted from dumbtv's MIT SDK build pipeline.
# Requires JDK 17, Android platform 25, build-tools 34.0.0 and zip (Linux/WSL/macOS).
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
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
if [[ ! -f $KEYSTORE ]]; then
  "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$KEYSTORE" -alias jagdev \
    -keyalg RSA -keysize 2048 -validity 3650 -storepass jagdev-local -keypass jagdev-local \
    -dname 'CN=JAG Home Development' >/dev/null
fi
WORK=$(mktemp -d "${TMPDIR:-/tmp}/jag-home.XXXXXX")
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/gen" "$WORK/classes"
"$BT/aapt2" compile --dir res -o "$WORK/res.zip"
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml "$WORK/res.zip" \
  --min-sdk-version 25 --target-sdk-version 28 --version-code 1 --version-name 0.1-dev \
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
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-key-alias jagdev \
  --ks-pass pass:jagdev-local --key-pass pass:jagdev-local \
  --out build/jag-home-dev.apk "$WORK/aligned.apk"
"$BT/apksigner" verify --verbose build/jag-home-dev.apk
"$BT/aapt2" dump badging build/jag-home-dev.apk | sed -n '1,4p'
permissions=$("$BT/aapt2" dump permissions build/jag-home-dev.apk)
if [[ $permissions == *uses-permission* ]]; then echo 'error: development APK must have no permissions' >&2; exit 1; fi
printf 'built %s (%s bytes)\n' "$PWD/build/jag-home-dev.apk" "$(wc -c < build/jag-home-dev.apk | tr -d ' ')"
