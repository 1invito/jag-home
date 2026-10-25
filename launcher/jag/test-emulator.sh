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
export JAG_KEY_PASSWORD=${JAG_KEY_PASSWORD:-$JAG_STORE_PASSWORD}
"$BT/apksigner" sign --ks "${JAG_DEV_KEYSTORE:-$PWD/../build/jag-home-dev.keystore}" --ks-key-alias "${JAG_KEY_ALIAS:-jagdev}" \
  --ks-pass env:JAG_STORE_PASSWORD --key-pass env:JAG_KEY_PASSWORD --out build/jag-home-tests.apk "$WORK/aligned.apk"
"$BT/apksigner" verify build/jag-home-tests.apk
adb_ install -r build/jag-home-tests.apk
: > build/emulator-results.txt
phase() {
  local phase_name=$1
  adb_ shell am instrument -w -e phase "$phase_name" com.jag.home.dev.tests/com.jag.home.dev.tests.DeviceTest | tee "$WORK/phase.txt"
  cat "$WORK/phase.txt" >> build/emulator-results.txt
  grep -Fq "JAG_DEVICE_TESTS_PASSED [$phase_name]" "$WORK/phase.txt"
  ! grep -q JAG_DEVICE_TESTS_FAILED "$WORK/phase.txt"
}
if [[ -n ${JAG_V1_APK:-} ]]; then
  badging=$("$BT/aapt2" dump badging "$JAG_V1_APK")
  [[ $badging == *"name='com.jag.home.dev' versionCode='1'"* ]] || { echo 'error: expected original v1 development APK' >&2; exit 1; }
  "$BT/apksigner" verify "$JAG_V1_APK"
  adb_ install -r -d "$JAG_V1_APK"
  phase seed-v1
  adb_ install -r build/jag-home-dev.apk
  phase migration
else
  adb_ install -r build/jag-home-dev.apk
fi
fixture_ids=(com.netflix.ninja com.onemainstream.sonyliv.android in.startv.hotstar com.amazon.firetv.youtube com.fancode.tv)
for package in "${fixture_ids[@]}"; do
  if adb_ shell pm path "$package" | grep -q '^package:'; then
    adb_ shell dumpsys package "$package" | grep -q 'versionName=jag-fixture' || {
      echo 'error: use a clean disposable emulator without real favorite apps' >&2; exit 1;
    }
    adb_ uninstall "$package"
  fi
done
phase missing
# Five locally built fixture packages exercise successful launches and bounded recency.
# They are never copied into the launcher APK or installed on a physical device.
mkdir -p "$WORK/fixture-classes" "$WORK/fixture-res/drawable"
cp res/drawable/icon.xml "$WORK/fixture-res/drawable/icon.xml"
cp res/drawable/banner.xml "$WORK/fixture-res/drawable/banner.xml"
"$JAVA_HOME/bin/javac" --release 8 -Xlint:all -Werror -cp "$JAR" -d "$WORK/fixture-classes" tests/android/fixture/Screen.java
(
  cd "$WORK"
  find fixture-classes -name '*.class' > fixture-classes.txt
  mkdir fixture-dex
  "$BT/d8" --release --min-api 25 --lib "$JAR" --output fixture-dex @fixture-classes.txt
)
"$BT/aapt2" compile --dir "$WORK/fixture-res" -o "$WORK/fixture-res.zip"
for package in "${fixture_ids[@]}"; do
  # Netflix fixture intentionally has only a square icon, other fixtures have a banner.
  banner='android:banner="@drawable/banner"'
  [[ $package != com.netflix.ninja ]] || banner=
  cat > "$WORK/fixture-manifest.xml" <<XML
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="$package">
  <application android:label="JAG Test Fixture" android:icon="@drawable/icon" $banner>
    <activity android:name="com.jag.fixture.Screen" android:exported="true" android:screenOrientation="landscape">
      <intent-filter><action android:name="android.intent.action.MAIN" /><category android:name="android.intent.category.LEANBACK_LAUNCHER" /></intent-filter>
    </activity>
  </application>
</manifest>
XML
  "$BT/aapt2" link -I "$JAR" --manifest "$WORK/fixture-manifest.xml" "$WORK/fixture-res.zip" \
    --min-sdk-version 25 --target-sdk-version 28 --version-code 1 --version-name jag-fixture -o "$WORK/fixture.apk"
  cp "$WORK/fixture-dex/classes.dex" "$WORK/classes.dex"
  (cd "$WORK" && zip -q -X fixture.apk classes.dex)
  "$BT/zipalign" -f -p 4 "$WORK/fixture.apk" "$WORK/fixture-aligned.apk"
  "$BT/apksigner" sign --ks "${JAG_DEV_KEYSTORE:-$PWD/../build/jag-home-dev.keystore}" --ks-key-alias "${JAG_KEY_ALIAS:-jagdev}" \
    --ks-pass env:JAG_STORE_PASSWORD --key-pass env:JAG_KEY_PASSWORD --out "$WORK/fixture-signed.apk" "$WORK/fixture-aligned.apk"
  adb_ install -r "$WORK/fixture-signed.apk"
done
phase fixtures
