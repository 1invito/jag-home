# JAG Home · development preview

A separate, native Java launcher preview for the Fire TV Stick 3rd generation
(2020), Fire OS 7.7.1.7 / Android 9. JAG Home Dev installs as **`com.jag.home.dev`**
alongside **`tv.dumbtv.home`**. It does not claim HOME or install an accessibility
service. Pressing Home uses your existing dumbtv integration.

The implementation is based on dumbtv's native View architecture and MIT build
pipeline. Upstream copyright and [MIT license](../../LICENSE) are retained.
See [architecture and API investigation](ARCHITECTURE.md) for the decisions and
[device validation](TESTING.md) for physical-device checks and measurements.

## What's implemented

- Original navy gradient, spacious margins, JAG wordmark, clock/date, six rounded
  cards and three quick actions. System typefaces and restrained 120 ms focus
  scaling; visible focus also works with animations disabled.
- Netflix, Sony LIV, JioHotstar, YouTube, FanCode and ExpressVPN in the requested
  order. PackageManager resolves installed activities and native banners/icons.
  Missing or disabled apps remain visible with an honest unavailable state.
- D-pad navigation, Select, Back, Menu and long-Select options. Local app ordering,
  tile size, labels, accent, clock format/visibility, dimming and animation settings.
- Static JPG/PNG/WebP wallpaper selection from app-owned folders or a system
  document picker when supplied by Fire OS. Sampled decode is bounded to 1080p;
  decoded images are cached while visible and released when the activity stops.
  Corrupt, missing, oversized or unsupported images fall back to navy.
- Free Memory confirmation, actual available/total memory, explicit public API
  limitations and native app-management shortcuts. **No automatic process killing.**
- Access to system settings, app details, the existing dumbtv launcher, About and
  appearance reset. Preferences stay in JAG's own private storage.

There are no permissions, analytics, ads, network calls, dependencies, polling,
boot receivers, services, image rotation or hidden APIs. No baseline or Fire Stick
performance improvement is claimed.

## Build on Windows using WSL

The supported Windows build path is **WSL 2 with Ubuntu 24.04**, using Linux JDK
and SDK tools inside WSL. Do not mix Windows SDK executables with this Linux
build. Android Studio is optional. Installing the resulting APK can use your
existing Windows `adb.exe` in Git Bash.

In WSL:

```bash
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk-headless python3 curl unzip zip git
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
mkdir -p "$ANDROID_HOME/cmdline-tools"
curl -fL https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip -o /tmp/jag-commandlinetools.zip
printf '%s  %s\n' e025545c62a8e64c7559119566a569fb1dec5f60 /tmp/jag-commandlinetools.zip | sha1sum -c -
unzip -q /tmp/jag-commandlinetools.zip -d "$ANDROID_HOME/cmdline-tools"
mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
sdkmanager --licenses
sdkmanager 'platforms;android-25' 'build-tools;34.0.0'
```

The archive checksum above is the value in Google's SDK repository metadata for
this exact tools archive. Do not disable verification or replace the expected
digest on failure. Review the Android SDK licenses when prompted. If you already
have command-line tools, reuse them rather than overwriting their installation.

Clone your branch into the WSL filesystem, then build:

```bash
git clone --branch feat/jag-home-dev https://github.com/1invito/jag-home.git
cd jag-home
./launcher/jag/test.sh
./launcher/jag/build.sh
sha256sum launcher/jag/build/jag-home-dev.apk
```

Output: **`launcher/jag/build/jag-home-dev.apk`**. The cloud build uses the same
JDK 17 / platform 25 / build-tools 34.0.0 pipeline. No Gradle is used.
For subsequent WSL sessions, restore `JAVA_HOME`, `ANDROID_HOME`,
`ANDROID_SDK_ROOT` and `PATH` above. This procedure requires x86-64 Linux/WSL;
ARM Windows needs a compatible x86-64 build environment.

The builder creates an ignored, development-only signing key at
`launcher/build/jag-home-dev.keystore`. Keep it for updates to this development
package. It never reads or replaces upstream `launcher/release.keystore`.
`JAG_DEV_KEYSTORE` can select a retained development key with the same `jagdev`
alias and local development password. Do not use this development key for a
production release. APK signatures are verified at the end of the build.

## Manually install with Windows Git Bash

No script deploys JAG to your TV. Follow this section only when you choose to
test. Copy the built APK from WSL into the Windows checkout, for example:

```bash
# WSL: change the destination to your actual Windows checkout
mkdir -p /mnt/c/Users/YOUR_NAME/Projects/jag-home/launcher/jag/build
cp launcher/jag/build/jag-home-dev.apk /mnt/c/Users/YOUR_NAME/Projects/jag-home/launcher/jag/build/
```

Then in **Windows Git Bash**, from that checkout:

```bash
# Replace TV_ADDRESS with the device's address; do not commit it.
export ADB_SERIAL='TV_ADDRESS:5555'
adb connect "$ADB_SERIAL"
adb -s "$ADB_SERIAL" get-state
# Must say device. If unauthorized, accept the prompt on the TV before continuing.
adb -s "$ADB_SERIAL" install -r launcher/jag/build/jag-home-dev.apk
adb -s "$ADB_SERIAL" shell am start -W -n com.jag.home.dev/.Home
```

The component is JAG's own declared activity. No third-party activities are
hardcoded. Installation adds only `com.jag.home.dev`; it does not replace dumbtv,
change settings, grant usage/accessibility access, disable packages or alter DNS.
Never use upstream `launcher/install.sh`, `scripts/all.sh`, `restore.sh` or the
cleanup scripts to install or remove this preview.

If an update reports `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, use the original
**JAG development key** to rebuild. Do not uninstall the existing dumbtv launcher
or reset its data to fix a signing mismatch. Removing only `com.jag.home.dev`
is an optional last resort and loses JAG's own settings, not streaming accounts.

## Wallpaper with the remote

From the home screen choose Wallpaper, then an image or Default. On a Fire TV
without a usable document picker, first add pictures from Windows Git Bash:

```bash
MSYS_NO_PATHCONV=1 adb -s "$ADB_SERIAL" shell mkdir -p /sdcard/Android/media/com.jag.home.dev/wallpapers
MSYS_NO_PATHCONV=1 adb -s "$ADB_SERIAL" push 'C:/Users/YOUR_NAME/Pictures/wallpaper.jpg' /sdcard/Android/media/com.jag.home.dev/wallpapers/
```

Use your own path and image; nothing is downloaded by JAG. The app also scans
`/sdcard/Android/data/com.jag.home.dev/files/wallpapers/` when available.
Select the filename using the remote. Images must be JPG, PNG or WebP, at most
32 MB, with sensible dimensions; a 1920 × 1080 still is recommended. Reopen the
wallpaper menu after adding files. Every settings change is local and requires
no keyboard. Appearance reset returns to navy and keeps the custom app order.

## Free Memory limitations

Free Memory asks for confirmation, then reads available memory **once**. It
reports that no apps were stopped and offers app-management settings. Android 9
supports limited background kill requests with a normal permission, but public
process visibility cannot establish that a streaming app, VPN or shared service
is safe to target. JAG intentionally has an empty cleanup allowlist and requests
no kill permission on any Android version.

Killing a background process, force-stopping an app, clearing its cache, clearing
its data and disabling a package are different operations. JAG performs none of
these. Android may restart background processes; increased free RAM is not a
reliable performance score. The management page is a manual fallback: leave
active playback and VPN settings alone. JAG never operates destructive controls
in that page and never claims a before/after gain.

## Return to dumbtv and rollback

Press the remote's **Home** button. The existing dumbtv Home integration remains
responsible for it. Back exits the preview; Settings also has Return to existing
dumbtv. If needed, from Git Bash:

```bash
# Start the existing app through PackageManager; no activity-name guess is needed.
adb -s "$ADB_SERIAL" shell monkey -p tv.dumbtv.home -c android.intent.category.LEANBACK_LAUNCHER 1
# Optional: remove ONLY the development preview and its own settings/wallpapers.
adb -s "$ADB_SERIAL" uninstall com.jag.home.dev
```

No HOME rebinding, global settings restoration or reboot should be necessary
because the preview never changes those. Do not run upstream `restore.sh`: that
would undo the owner's existing cleanup. Existing disabled packages, accounts,
streaming-app data and DNS configuration stay as they were.

## Future production migration (separate, deliberate task)

After successful hardware validation, prefer a separate stable package such as
`com.jag.home`, leaving `tv.dumbtv.home` installed for recovery. Give production
its own controlled signing certificate; archive it securely and sign every
update with the same certificate. A different package does not inherit app data
or settings. Export only JAG's appearance/order via an explicit migration step.

Neither a new package nor a new certificate can update upstream `tv.dumbtv.home`.
An in-place update would require its existing certificate and would conflict
with the side-by-side recovery objective. Never uninstall dumbtv to sidestep this.
The `.dev` preview has **no** Guard, Keeper or accessibility-service identifier.
Any later production HOME service would have a new fully qualified component
under the production package and require a separate, reviewed manifest and
installer. Do not point the old installer at JAG: it contains hardcoded upstream
IDs, alarm settings and accessibility writes.

Before a migration, privately record the current HOME/accessibility/alarm
configuration and certificate fingerprints, then design a narrowly scoped,
verified change that preserves unrelated services and can restore those exact
values. Obtain owner approval before applying it. Validate Home, Back, long Home,
remote pairing, system settings, playback and VPN continuity. Keep dumbtv
installed and reachable during the trial. The migration rollback must restore
the recorded binding and launch dumbtv **before** removing a new service/package;
it must not use the broad cleanup restore script. None of these production
changes are implemented or performed by this preview.
