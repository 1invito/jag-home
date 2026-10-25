#!/usr/bin/env bash
# Separate stable test APKs on a disposable local AOSP emulator only. Never a household TV.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
serial=${JAG_TEST_SERIAL:-emulator-5558}
port=${JAG_TEST_ADB_PORT:-5038}
[[ $serial =~ ^emulator-[0-9]+$ ]] || { echo 'Disposable emulator required' >&2; exit 1; }
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
BT=$SDK/build-tools/34.0.0
JAR=$SDK/platforms/android-25/android.jar
adb_() { "$SDK/platform-tools/adb" -P "$port" -s "$serial" "$@"; }
hardware=$(adb_ shell getprop ro.hardware | tr -d '\r')
[[ $hardware == ranchu || $hardware == goldfish ]] || { echo 'Refusing physical hardware' >&2; exit 1; }
[[ $(adb_ shell getprop ro.build.version.sdk | tr -d '\r') == 28 && $(adb_ shell getprop sys.boot_completed | tr -d '\r') == 1 ]] || {
  echo 'Wait for a disposable API 28 emulator to boot' >&2; exit 1;
}
./build-stable.sh
WORK=$(mktemp -d "${TMPDIR:-/tmp}/jag-stable-tests.XXXXXX")
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/classes"
"$BT/aapt2" link -I "$JAR" --manifest stable/tests/android/AndroidManifest.xml --min-sdk-version 28 --target-sdk-version 28 -o "$WORK/tests.apk"
"$JAVA_HOME/bin/javac" --release 8 -Xlint:all -Werror -cp "$JAR" -d "$WORK/classes" stable/tests/android/src/com/jag/home/tests/StableTest.java
(
  cd "$WORK"; find classes -name '*.class' > classes.txt
  "$BT/d8" --release --min-api 28 --lib "$JAR" --output . @classes.txt
  zip -q -X tests.apk classes.dex
)
"$BT/zipalign" -f -p 4 "$WORK/tests.apk" "$WORK/aligned.apk"
export JAG_STABLE_KEY_PASSWORD=${JAG_STABLE_KEY_PASSWORD:-$JAG_STABLE_STORE_PASSWORD}
"$BT/apksigner" sign --ks "$JAG_STABLE_KEYSTORE" --ks-key-alias "$JAG_STABLE_KEY_ALIAS" \
  --ks-pass env:JAG_STABLE_STORE_PASSWORD --key-pass env:JAG_STABLE_KEY_PASSWORD --out build/jag-home-stable-tests.apk "$WORK/aligned.apk"
"$BT/apksigner" verify build/jag-home-stable.apk
adb_ install -r build/jag-home-stable.apk
adb_ install -r build/jag-home-stable-tests.apk
adb_ shell am instrument -w com.jag.home.tests/com.jag.home.tests.StableTest | tee build/stable-emulator-results.txt
grep -q 'JAG_STABLE_TESTS_PASSED' build/stable-emulator-results.txt
! grep -q 'JAG_STABLE_TESTS_FAILED' build/stable-emulator-results.txt
python3 stable/tests/emulator_binding.py "$serial" "$SDK/platform-tools/adb" "$port"
