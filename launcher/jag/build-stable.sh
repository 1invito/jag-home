#!/usr/bin/env bash
# Stable applicationId com.jag.home; reuses v2 UI sources and a separately reviewed integration tree.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
mode=${1:-signed}
[[ $mode == signed || $mode == --unsigned ]] || { echo 'usage: build-stable.sh [--unsigned]' >&2; exit 2; }
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
BT=$SDK/build-tools/34.0.0
JAR=$SDK/platforms/android-25/android.jar
[[ -f $JAR && -x $BT/aapt2 && -x ${JAVA_HOME:-}/bin/javac ]] || { echo 'JDK 17, platform 25 and build-tools 34.0.0 required' >&2; exit 1; }
[[ $("$JAVA_HOME/bin/javac" -version 2>&1) == javac\ 17.* ]] || { echo 'JDK 17 required' >&2; exit 1; }
if [[ $mode == signed ]]; then
  : "${JAG_STABLE_KEYSTORE:?Provide an owner-controlled stable keystore; no key is generated}"
  : "${JAG_STABLE_KEY_ALIAS:?Provide the owner key alias}"
  : "${JAG_STABLE_STORE_PASSWORD:?Provide the keystore password securely in the environment}"
  : "${JAG_STABLE_CERT_SHA256:?Provide the expected public owner certificate SHA-256}"
  export JAG_STABLE_KEY_PASSWORD=${JAG_STABLE_KEY_PASSWORD:-$JAG_STABLE_STORE_PASSWORD}
  [[ $JAG_STABLE_CERT_SHA256 =~ ^[a-f0-9]{64}$ && $JAG_STABLE_CERT_SHA256 != 3d61b2c1d0e1ae94dd0dd1fe76c7508ea8c9a8897257be81fbe4d893c0f18939 ]] || {
    echo 'Use a dedicated stable certificate, not the development certificate' >&2; exit 1;
  }
fi
WORK=$(mktemp -d "${TMPDIR:-/tmp}/jag-stable.XXXXXX")
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/gen" "$WORK/classes" build
cp -R src "$WORK/src"
cp -R stable/src/. "$WORK/src/"
cp stable/adapter/com/jag/home/dev/*.java "$WORK/src/com/jag/home/dev/"
cp -R res "$WORK/res"
cp -R stable/res/. "$WORK/res/"
"$BT/aapt2" compile --dir "$WORK/res" -o "$WORK/res.zip"
"$BT/aapt2" link -I "$JAR" --manifest stable/AndroidManifest.xml "$WORK/res.zip" \
  --min-sdk-version 28 --target-sdk-version 28 --version-code 3 --version-name 0.3-home \
  --java "$WORK/gen" -o "$WORK/app.apk"
(
  cd "$WORK"
  find src gen -name '*.java' > sources.txt
  "$JAVA_HOME/bin/javac" --release 8 -Xlint:all -Werror -encoding UTF-8 -cp "$JAR" -d classes @sources.txt
  find classes -name '*.class' > classes.txt
  "$BT/d8" --release --min-api 28 --lib "$JAR" --output . @classes.txt
  zip -q -X app.apk classes.dex
)
"$BT/zipalign" -f -p 4 "$WORK/app.apk" "$WORK/aligned.apk"
APK=build/jag-home-stable-unsigned.apk
if [[ $mode == signed ]]; then
  "$BT/apksigner" sign --ks "$JAG_STABLE_KEYSTORE" --ks-key-alias "$JAG_STABLE_KEY_ALIAS" \
    --ks-pass env:JAG_STABLE_STORE_PASSWORD --key-pass env:JAG_STABLE_KEY_PASSWORD \
    --out "$WORK/signed.apk" "$WORK/aligned.apk"
  "$BT/apksigner" verify --verbose "$WORK/signed.apk"
  cert=$("$BT/apksigner" verify --print-certs "$WORK/signed.apk" | sed -n 's/Signer #1 certificate SHA-256 digest: //p')
  [[ $cert == "$JAG_STABLE_CERT_SHA256" ]] || { echo 'Stable signer differs from expected owner certificate' >&2; exit 1; }
  APK=build/jag-home-stable.apk
  cp "$WORK/signed.apk" "$APK"
else
  cp "$WORK/aligned.apk" "$APK"
  echo 'UNSIGNED stable validation artifact: not installable.'
fi
"$BT/aapt2" dump badging "$APK" | sed -n '1,4p'
printf 'built %s (%s bytes)\n' "$PWD/$APK" "$(wc -c < "$APK" | tr -d ' ')"
