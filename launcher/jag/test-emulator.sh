#!/usr/bin/env bash
# ONLY for an already booted, disposable API 28 emulator. Resets JAG's own test settings.
# Never installs to a physical TV; upstream dumbtv is never queried or modified.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
serial=${JAG_TEST_SERIAL:-emulator-5558}
port=${JAG_TEST_ADB_PORT:-5038}
[[ $serial =~ ^emulator-[0-9]+$ ]] || { echo 'error: disposable emulator serial required' >&2; exit 1; }
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
BT=$SDK/build-tools/34.0.0
JAR=$SDK/platforms/android-25/android.jar
adb_() { "$SDK/platform-tools/adb" -P "$port" -s "$serial" "$@"; }
hardware=$(adb_ shell getprop ro.hardware | tr -d '\r')
api=$(adb_ shell getprop ro.build.version.sdk | tr -d '\r')
[[ ( $hardware == ranchu || $hardware == goldfish ) && $api == 28 ]] || {
  echo 'error: this suite requires a disposable Android 9 emulator' >&2; exit 1;
}
[[ $(adb_ shell getprop sys.boot_completed | tr -d '\r') == 1 ]] || { echo 'error: emulator has not booted' >&2; exit 1; }
./build.sh
WORK=$(mktemp -d "${TMPDIR:-/tmp}/jag-instrumentation.XXXXXX")
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/classes"
"$BT/aapt2" link -I "$JAR" --manifest tests/android/AndroidManifest.xml \
  --min-sdk-version 25 --target-sdk-version 28 -o "$WORK/tests.apk"
"$JAVA_HOME/bin/javac" --release 8 -Xlint:all -Werror -cp "$JAR" -d "$WORK/classes" \
  tests/android/src/com/jag/home/dev/tests/DeviceTest.java
(
  cd "$WORK"
  find classes -name '*.class' > classes.txt
  "$BT/d8" --release --min-api 25 --lib "$JAR" --output . @classes.txt
  zip -q -X tests.apk classes.dex
)
"$BT/zipalign" -f -p 4 "$WORK/tests.apk" "$WORK/aligned.apk"
"$BT/apksigner" sign --ks "${JAG_DEV_KEYSTORE:-$PWD/../build/jag-home-dev.keystore}" --ks-key-alias jagdev \
  --ks-pass pass:jagdev-local --key-pass pass:jagdev-local --out build/jag-home-tests.apk "$WORK/aligned.apk"
"$BT/apksigner" verify build/jag-home-tests.apk
adb_ install -r build/jag-home-dev.apk
adb_ install -r build/jag-home-tests.apk
adb_ shell am instrument -w com.jag.home.dev.tests/com.jag.home.dev.tests.DeviceTest | tee build/emulator-results.txt
grep -q '^JAG_DEVICE_TESTS_PASSED\|stream=JAG_DEVICE_TESTS_PASSED' build/emulator-results.txt
! grep -q 'JAG_DEVICE_TESTS_FAILED' build/emulator-results.txt
