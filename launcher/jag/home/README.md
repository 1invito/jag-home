# JAG Home v3 — owner-operated HOME integration

This implementation adds **`com.jag.home`** beside the existing **`com.jag.home.dev`**
and **`tv.dumbtv.home`**. It reuses v2's native UI, six favorites, themes, wallpaper,
local recents and memory optimizations. Installing the stable APK leaves its
Guard **disarmed** and does not change HOME/accessibility/alarm settings.

This cloud task does not contact a household device, activate redirection or
merge a PR. Owner reports confirm v2 UI works on the Fire Stick; the new Guard,
boot behavior, Home latency and recovery still need a controlled physical trial.
No recommendation to activate is made before those checks and performance pass.

## Components and supported scope

- Stable UI uses the tested v2 Java namespace with a separate Android application
  ID, UID, data directory, manifest and owner certificate. No development APK
  update or signing-key reuse is needed.
- `HomeGuard` is the only persistent component: an event-driven accessibility
  service limited to window-state events from Amazon Home and a small package
  set. It cannot retrieve screen contents or intercept remote keys. There is no
  Usage Access, screen/task polling, network permission or foreground cleaner.
- An explicitly armed, exclusive Guard recognizes the actual verified Amazon
  Home class and schedules the stable activity through an AlarmManager
  PendingIntent. The owner tool retains the complete alarm constants string and
  changes only `min_futurity` when necessary; an existing `0` is not rewritten.
- There is **no overlay window on the supported API 28 path**. Android 11's
  visibility workaround is not ported to Fire OS 7. No cover can obscure Settings
  or the native long-Home panel after a UI crash. A brief Amazon frame may be
  visible; measure the actual transition on the Fire Stick.
- Same-process first-draw acknowledgement cancels pending launches. Duplicate
  events are gated; a one-shot timeout releases pending state. Boot-completed
  delivery marks one pending startup launch for the enabled Guard. No periodic
  boot/foreground polling is used. No new Android HOME resolver is registered.
- The Guard refuses arming outside Amazon API 28 and whenever the original
  dumbtv Guard is still enabled. Trial arming expires; committed arming persists
  across reboot. Back stays on stable Home only while the Guard is active;
  settings/dialog Back and manual disarmed-app Back remain normal.
- `HomeControl` is a receiver restricted to callers with Android's signature-level
  `DUMP` capability (the existing authorized ADB shell). Ordinary apps cannot arm
  it. The application does not request that permission. The only normal manifest
  permission is `RECEIVE_BOOT_COMPLETED`.

The tool supports **only AFTSSS / Amazon / API 28 with the sole Android user 0**.
It refuses multiple Android users or any selected nonzero profile. On Android 9,
`settings list` and `delete` reject `--user`; their default user 0 is used only
after this explicit single-user check. Get/put and every activity/control/install
operation specify user 0. Amazon account profiles are not silently manipulated.
Other models/Fire OS versions remain blocked rather than guessed.

## Build and signing

Follow [stable/SIGNING.md](../stable/SIGNING.md) to create and securely retain a
dedicated owner certificate on your own machine. Build with JDK 17, Android
platform 25 and build-tools 34.0.0:

```bash
# Owner signing environment must already be supplied securely.
./launcher/jag/build-stable.sh
```

Output: `launcher/jag/build/jag-home-stable.apk`, versionCode 3, minimum/target API
28. Keep this package's key for all updates. An unsigned validation artifact is
available with `--unsigned`; it cannot be installed. Cloud test-signed APKs use
a disposable test identity and are not owner production releases.

The Windows migration tool needs **Python 3.10+ and your existing adb.exe**.
Install/trial/acceptance APK verification additionally needs native Windows JDK 17
and Android build-tools 34.0.0 (or use the complete tool inside WSL with a matching
Linux ADB setup). Do not mix Windows paths/binaries into a WSL process. The tool
uses `java -jar .../apksigner.jar` and native `aapt2.exe`, avoiding batch-file
shell quoting. Rollback/status/watchdog need only Python and ADB after install.

## Appearance transfer without changing the installed preview

V2 stores `jag.properties` privately with backup disabled, so an ordinary ADB
shell cannot export that file. This implementation does not root the device,
change preview permissions, run `pm clear`, install an exporter into it or claim
automatic access to its private configuration.

Copy `appearance.example.properties` to a private ignored/local file and fill in
only the selected settings visible in v2's Customize page. The template includes
the exact six favorite identities; reorder them as currently selected. No launch
history, accounts, credentials or arbitrary private paths are accepted. Use the
original selected wallpaper file from your PC, or pull only a **known selected
image** from the preview's own external `wallpapers/` folder if accessible.
Never pull other apps' data or unrelated folders.

`--backup --appearance-file ... --wallpaper-file ...` retains those owner-selected
files privately. `--install` stages them only in stable JAG's own external-files
`migration/` folder. Open the stable app manually, choose Customize → Import
selected appearance and confirm. Only approved visual choices/order are copied;
the optional image is decoded, bounded and re-encoded into stable private
storage. History and unrelated keys are preserved. There is no cross-package
private-data copy. If no appearance file is supplied, backup explicitly records
that automatic v2 settings export was unavailable; configure stable UI manually
before confirming `JAG WORKS` in a trial.

## Windows workflow, with installation separate from activation

Commands below are **owner-operated** PowerShell examples, not deployment done
by Codex. Use the newly built owner-signed APK. Keep state on a private local
user-profile volume, not in Git, a public folder or a synced shared location.
On Windows the user-profile ACL supplies privacy; verify it for your environment.
POSIX state files are created with restrictive modes. Do not publish backups,
raw device diagnostics or identifiers.

```powershell
$env:ADB_SERIAL = 'TV_ADDRESS:5555'
$state = Join-Path $env:LOCALAPPDATA 'JAGHome\trial-01'
$env:ANDROID_HOME = 'C:\Path\To\Android\Sdk'
$env:JAVA_HOME = 'C:\Path\To\jdk-17'
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
$tool = '.\launcher\jag\home\migrate.py'
adb connect $env:ADB_SERIAL
adb -s $env:ADB_SERIAL get-state
# Continue only after authorization reports device.

py -3 $tool --preflight --state-dir $state
py -3 $tool --backup --state-dir $state --appearance-file '.\report\appearance.properties' --wallpaper-file 'C:\Path\To\SelectedWallpaper.jpg'

# Public certificate digest printed by your owner build, not an APK file hash:
$cert = Read-Host 'Owner stable certificate SHA-256 (lowercase, no separators)'
py -3 $tool --install --state-dir $state --apk '.\launcher\jag\build\jag-home-stable.apk' --owner-cert $cert --java $java
py -3 $tool --status --state-dir $state
```

If no image/configuration transfer is wanted, omit those two backup options.
Backups are immutable: reuse the same state directory for status/trial/rollback,
but select a **new** one for a fresh baseline after intentionally changed settings.
Keep the original directory for recovery. The tool never runs `adb connect`,
discovers a device or switches Android users; the explicit transport is required.

Manually open the installed stable app before activation. Its actual activity
is declared in its manifest and can also be resolved with PackageManager:

```powershell
adb -s $env:ADB_SERIAL shell am start -W -n com.jag.home/com.jag.home.dev.Home
```

Check appearance, remote navigation, six real apps and Settings. Home must still
follow the existing dumbtv integration at this point. Do not uninstall/disable
either existing launcher or change Amazon/DNS/VPN settings.

## Modes

| Mode | Operation |
|---|---|
| `--preflight` | Device-read-only compatibility, sole-user identity, enabled original packages, actual activity resolution, original bound Guard and settings parse checks |
| `--backup` | Device-read-only, immutable private settings/default-HOME/package baseline plus explicit owner appearance files |
| `--install` | Verify owner certificate and APK identity, install only stable for user 0, verify installed bytes and disarmed status; no HOME setting changes |
| `--trial` | Interactive probes/confirmations, detached watchdog handshake, disarmed binding test, exclusive temporary handover and first-draw acknowledgement |
| `--verify` | Check current user/device, exact expected settings/default resolver, one bound Guard, first draw/foreground, package state and new crash/ANR evidence; press physical Home first |
| `--reboot-check` | Explicitly arm a bounded 90-second reboot grace; owner reboots manually; a changed boot generation plus new ready state is required |
| `--accept` | Interactive owner confirmation, actual benchmark/physical acceptance file, verified reboot and healthy trial; remove expiry only after checks |
| `--rollback` | Disarm/cancel only stable JAG, restore captured setting presence/values or preserve observed concurrent edits, launch/verify original dumbtv; no broad cleanup |
| `--status` | Read current journal, watchdog freshness, stable armed/bound state and conflict/reboot status |

`--preflight` resolves recovery activities but does not launch them. `--trial`
actually probes dumbtv and stable JAG and requires the owner to visually confirm
both work before modifying HOME settings. Noninteractive/piped approval is
refused. Merely installing or opening stable JAG never activates interception.

## Temporary trial and independent recovery

```powershell
py -3 $tool --trial --state-dir $state --java $java --seconds 900
```

Type `TRY JAG` only when ready, `DUMBTV WORKS` after the original recovery app
opens, and `JAG WORKS` after the stable manual UI works. The controller then:

1. Checks the installed APK against the recorded owner artifact and the exact
   original snapshot. It starts a separate detached Python watchdog and verifies
   its private nonce/PID heartbeat before any settings write.
2. Adds the new **disarmed** service while keeping dumbtv active. It proves actual
   authorization/binding on this build before removing the original service.
3. Preserves every unrelated service. It changes `min_futurity` only if needed,
   removes only dumbtv's Guard membership, waits for the old binding to disappear,
   then arms stable JAG with a finite session lease. The app itself also refuses
   to arm if the old Guard is enabled.
4. Sends one explicit HOME key as the initial trial probe and waits for the
   independent watchdog to confirm real first draw/foreground. It does not treat
   an activity PID, a setting string or an install success as readiness.

Keep the physical remote ready. If the injected HOME event behaves differently
on Fire OS and JAG does not appear, press physical Home during the displayed
15-second readiness window. A failed probe rolls back; it is not a reason to
skip the binding/readiness gates. Software key injection never substitutes for
the real-remote acceptance checks.

The watchdog lives independently of both the trial CLI and Android process. It
continues after the CLI exits. Definite crash/ANR, failed binding, missing first
draw or overall timeout restores the original integration. During playback it
does **not** require JAG to remain foreground. Its bounded diagnostic polling
runs on the PC only during the trial; the Android Guard never polls screens or
processes. Keep the PC awake and the authorized ADB transport available.

No automatic recovery is promised after ADB/network loss, host shutdown, a killed
watchdog or a switched Android profile. The app's finite trial lease also fails
closed, using elapsed time within the same boot and a wall-clock fallback across
reboot; it does not by itself restore old system settings. Amazon Home and both
existing launchers remain installed. Keep the rollback command and verified
Settings/ADB recovery route ready independently.

Pause other ADB/settings editors during handover. Android 9's settings provider
does not offer atomic compare-and-swap for this shell workflow. The tool checks
before/after writes, journals write intent before mutation, serializes its own
processes with OS locks and preserves **observed** concurrent service/alarm edits
on rollback. It cannot prove absence of a third-party write in the tiny gap
between a read and write. It never clears the whole accessibility list or forces
unrelated services off. Conflicts remain explicit and block unattended acceptance.

## Reboot, remote and performance acceptance

While enough trial time remains, first verify Home, Back, long Home, streaming,
VPN and Settings using the real remote. Home should immediately select JAG;
long Home should show the native quick panel. Then:

```powershell
py -3 $tool --verify --state-dir $state
py -3 $tool --reboot-check --state-dir $state
# Type REBOOT CHECK, then restart the Fire Stick manually using its verified UI.
# Keep the PC awake. The overall trial deadline is never extended.
py -3 $tool --status --state-dir $state
py -3 $tool --verify --state-dir $state
```

The 90-second grace tolerates boot-time availability/binding delays, but a new
recorded crash still triggers recovery. Acceptance requires a changed device
boot count and fresh Guard-ready state; grace expiry or the overall trial timeout
still rolls back. If boot takes longer, retain dumbtv and investigate rather than
silently extending an unverified migration.

Copy `measurements.example.json` into the private report directory. Replace its
null/false/zero fields only with real observations. Use at least ten comparable
warm trials and median/p95, keeping cold timing separate, under the same device,
resolution, player/VPN/network, wallpaper and thermal conditions. See parent
[TESTING.md](../TESTING.md) for `am start -W`, `dumpsys meminfo/gfxinfo`, crash/ANR
and repeated-cycle commands. Measure physical Home-to-stable-artwork with a camera
or frame trace; `am start -W` alone is not that latency.

The owner tool refuses acceptance when measured Home median/p95 exceeds the
baseline by more than 10%, additional visible PSS exceeds 15 MiB, fields are
missing/nonfinite, fewer than ten warm trials were measured, or remote/playback/
VPN/Settings/long-Home/reboot checks are not confirmed. Do not recommend activation
if the integration is significantly slower, leaks memory or breaks those checks.

```powershell
py -3 $tool --accept --state-dir $state --java $java --measurements '.\report\jag-home-measurements.json'
# Type KEEP JAG only after all actual checks succeed.
```

Successful acceptance persists arming across reboot and stops the independent
trial watchdog. Keep the private original backup and dumbtv installed. No PR is
merged and no rollout is performed automatically.

## Exact rollback and manual recovery

At any point after a trial starts, or after acceptance:

```powershell
py -3 $tool --rollback --state-dir $state
py -3 $tool --status --state-dir $state
```

Rollback checks device identity and sole user 0, disarms stable JAG and cancels
its own pending launch. If its control receiver cannot run after a crash, only
**`com.jag.home`** is force-stopped to cancel its alarms/service. Neither dumbtv,
Amazon, networking, VPN nor streaming apps are stopped, disabled or cleared.
The original service membership and complete alarm string are restored exactly,
including absent versus empty versus literal-null fields, if no external edit
occurred. The original `accessibility_enabled` value is preserved. The default
HOME activity is never changed by this workflow; an external change is reported,
not blindly overwritten. Actual original Guard binding and dumbtv foreground are
checked after restoration, but the owner still verifies physical Home.

When concurrent edits are observed, rollback removes only JAG's new component,
reinstates only the displaced old Guard and preserves unrelated new services or
alarm options. If another writer changed `min_futurity` itself, its value is kept
and reported as a conflict. An externally changed accessibility-enabled flag is
not forced back; missing old binding blocks a full recovery claim. Status reports
`rolled_back_conflict` and exact restoration is not claimed. Review differences
privately and use a new baseline directory for any later trial.

If ADB remains available, run rollback from another terminal; it needs Python,
ADB and the private state directory, even if JAG crashes or JDK/SDK tools are
unavailable. No `restore.sh`, `all.sh`, `install.sh`, factory reset or package
cleanup is used. If ADB is unavailable, use the exact Settings recovery route
verified with the owner before trial to remove **only the stable JAG accessibility
service**, then open retained dumbtv. Do not invent menu paths. Native long-Home
and Amazon Home are intentionally left available; they still require physical
validation on this Fire OS build. Reconnect ADB and finish scoped rollback as soon
as possible. Never clear either existing launcher's data or uninstall it.

## Tests and limits

Run `./launcher/jag/test-home.sh` for pure Guard policy, host transaction/watchdog
tests and stable manifest/security barriers. The independent-process test exits
the parent without a finally block and confirms the surviving child restores the
original mock device. Real Android 9 emulator tests in
`test-stable-emulator.sh` verify native registration, separate UID/storage,
disarmed UI, whitelist import and real disarmed service binding/unsupported-OS
refusal. They hard-refuse non-emulator transports before writes.

Mocks do not establish Fire OS's accessibility authorization, physical Home or
long-Home behavior, reboot performance, streaming/VPN continuity or latency/PSS.
Windows code uses native Python subprocess/locking APIs but cloud execution is
on Linux; perform read-only Windows preflight before any trial. Owner production
signing and actual Fire Stick deployment remain intentionally outside this task.


## Optional explicit owner waiver of measured performance

For an owner who has confirmed the real remote, playback, VPN, Settings, long-Home,
and a **fresh reboot in the current trial**, but elects not to time 10 HOME
returns, the host tool supports an explicitly **unmeasured** performance waiver.
This is not a performance pass: the <=10% latency and <=15 MiB PSS gates were
**not tested**. The owner knowingly accepts that added latency or RAM use might
be discovered later. This option does not modify Fire OS, disable the watchdog,
skip the live-health/identity/install checks or bypass the fresh reboot check.
The original dumbtv stays installed and the saved rollback remains available.

Create an ignored private JSON file (not in Git) with **only** these owner-verified
booleans, setting them to true only after genuinely testing them on this build:

```json
{
  "remote_ok": true,
  "playback_ok": true,
  "vpn_ok": true,
  "settings_ok": true,
  "long_home_ok": true,
  "reboot_ok": true,
  "performance_waived": true
}
```

After a new owner-operated trial, real reboot check and `--verify`, run
`--accept --waive-performance --measurements /private/path/physical-checks.json`.
The tool separately asks for `I ACCEPT UNMEASURED PERFORMANCE` and `KEEP JAG`
and rejects the waiver if the file includes fabricated timings or any missing
physical checks. This flag changes only the **host-side acceptance rule**; it
does not require rebuilding or updating the Android APK. The watchdog still
rolls back after its deadline if not accepted. Never manually alter the
journal/lease to extend a trial. For ordinary measured acceptance, omit the flag
and use `measurements.example.json` with 10 real warm trials and PSS.
