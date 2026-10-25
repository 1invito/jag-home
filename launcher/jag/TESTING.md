# Validation and Fire Stick test procedure

## Recorded implementation validation

Validated in the cloud on 2026-10-25:

| Check | Observed result |
|---|---|
| Pure Java production models | 570 assertions passed |
| Development safety regression suite | 3 tests passed |
| Native Android 9 / API 28 emulator suite | 42 assertions passed |
| Upstream Python fake-TV suite | 77 tests passed |
| Catalog validation | All 29 device files valid with full YAML parsing |
| Bash syntax and upstream version manifest | Passed |
| SDK 25 / JDK 17 / build-tools 34 build | Passed; separate APK 37,472 bytes |
| APK signature, package and permission inspection | Verified; `com.jag.home.dev`, min 25, target 28, zero permissions |
| Fire Stick 3rd generation / Fire OS 7.7.1.7 | Not run; no household device contacted |

The emulator is headless AOSP Android 9 at 1920 × 1080 with software CPU/GPU
emulation. All six real streaming/VPN packages are absent there; the missing-app
path is validated against the actual PackageManager. Real app artwork, app
launching, playback/VPN continuity and Fire OS Settings/picker behavior remain
physical-device checks. A first emulator pass exposed missing initial focus in
touch mode; explicit focusability now handles that state. Dialog checks wait for
actual window/accessibility readiness rather than assuming a queued UI event is
already displayed. These results are not physical-device performance data.

## Automated checks

From the repository root with the build environment activated:

```bash
./launcher/jag/test.sh
./launcher/jag/build.sh
python3 -m unittest discover -s tests -v
python3 catalog/validate.py
for f in scripts/*.sh launcher/*.sh launcher/jag/*.sh; do bash -n "$f" || exit 1; done
./launcher/release-check.sh --manifest
```

The JAG host suite exercises the actual app-order, settings persistence/recovery,
navigation graph, wallpaper validation/sampling and fail-closed memory-policy
models. The safety tests inspect the manifest and prohibit process mutation,
privileged settings writes and unsafe build/deployment behavior. These are
regression checks, not proof of Fire OS behavior.

The build verifies the APK signature and rejects any permission declaration.
The new GitHub workflow runs the JAG model/safety checks and SDK build and uploads
only the development APK; signing keys and instrumentation APKs are excluded.
The original upstream CI still runs independently.

### Disposable Android 9 emulator

`launcher/jag/test-emulator.sh` builds a dependency-free native Instrumentation
APK, installs it and JAG **only on a disposable API 28 emulator**, then exercises
the rendered activity with D-pad events. It checks PackageManager missing states,
six controls, focus boundaries, row movement, missing-app dialog recovery,
settings changes/persistence/cancel and the installed manifest's permission/HOME
properties. It also checks the actual memory confirmation/fallback and real
sampled wallpaper decoding, corrupt-file and missing-file results.
It resets only JAG's own private test settings. The script refuses
physical device serials and non-emulator hardware.

```bash
# Start a disposable Android 9 emulator first. No real TV may be used for this suite.
JAG_TEST_SERIAL=emulator-5558 JAG_TEST_ADB_PORT=5038 ./launcher/jag/test-emulator.sh
```

It defaults to a dedicated ADB server port 5038 to avoid the ordinary device
server. Change the port only to match your disposable emulator. A real pass must
contain `JAG_DEVICE_TESTS_PASSED`; a successful build/install alone is insufficient.
Results are written to ignored `launcher/jag/build/emulator-results.txt`.
An AOSP emulator is not Fire OS and cannot validate its app catalog, VPN,
playback, document picker, Settings intents, existing Guard or Bluetooth remote.

## Manual Fire OS 7.7.1.7 test

Use the separate APK installation commands in [README.md](README.md). Do not
invoke the emulator suite or upstream cleanup/install scripts on the Fire Stick.
Check [the existing AFTSSS report](../../catalog/devices/fireos-amazon-aftsss.yaml)
for this model; its partial upstream report does not establish JAG compatibility.
The owner already has dumbtv configured: do not repeat cleanup or HOME setup.

1. Before installation, confirm dumbtv works, Home returns to it, streaming apps
   launch with their existing accounts, ExpressVPN is connected, and system
   settings/remote work. Privately note the disabled-package count and current
   configuration. Do not publish identifiers or raw device logs.
2. Install only `com.jag.home.dev` and launch `.Home` manually. Confirm the title
   JAG Home Dev differs from dumbtv and all six apps appear in the requested order.
3. Navigate every edge with Up/Down/Left/Right. Select each installed app; verify
   existing logins and playback. Return with Home: **dumbtv is expected**. Reopen
   JAG manually. The preview intentionally cannot prove production Home timing.
4. A missing/disabled app must show Not installed/Unavailable, retain its position,
   and show an honest error on Select. App settings may open; no installation,
   enable/disable or data deletion happens automatically.
5. Test Menu and long Select, change order with Select dialogs, then reopen JAG.
   Every control needs a visible focus indicator. Back dismisses dialogs, exits
   settings to JAG, then exits JAG. The Home button always follows the existing
   integration. Long Home should still show the Fire OS quick menu.
6. Select JPG, PNG and WebP still images, test dimming and Default. Test no file
   picker, a deleted selected image and a corrupt image; navy must remain usable.
   Wallpapers must not change during focus movement or rotate while idle.
7. Change every appearance setting. Disable animations and labels; focus must
   remain visible. Toggle clock/date, 12/24 hours and tile size. Reopen and verify
   persistence. Restore appearance; verify app order and all other apps survive.
8. With VPN and playback active, choose Free Memory, then Cancel. Choose again
   and confirm. It must state no apps were stopped, show available memory or an
   explicit read failure, and offer manual settings. VPN/playback continue.
   Never expect or infer an automatic cleanup gain. Do not manually stop an
   active streaming app or VPN in system settings.
9. Check system Settings, app-management fallbacks and Return to existing dumbtv.
   If a Fire OS intent is unsupported, the UI must fail gracefully. Home remains
   the recovery route without changing any integration setting.
10. Repeat return/reopen cycles after Netflix, YouTube and another streaming app.
    Check for corrupt artwork, missing focus, crashes, ANRs or escalating memory.
    Test preview removal only if desired, then confirm original dumbtv, accounts,
    VPN, disabled packages and DNS remain unchanged.

## Comparable performance measurements

Use the **same device**, resolution, wall image, display settings, network,
foreground app state and thermal conditions for baseline and JAG. Run at least
10 trials, keep the first cold run separate, and report median and p95. A frame
comparison under software-emulated Android would not establish Fire Stick speed.
Use a private local output folder and strip identifiers before publishing.

From Windows Git Bash, `ADB_SERIAL` must identify exactly the owner's device:

```bash
# Resolve the baseline's real activity; record the returned component privately.
adb -s "$ADB_SERIAL" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER -p tv.dumbtv.home

# Warm explicit launch timing for JAG (not HOME-button latency): read TotalTime/WaitTime.
adb -s "$ADB_SERIAL" shell am start -W -n com.jag.home.dev/.Home

# Memory snapshots while each launcher is visible and after the same streaming app.
adb -s "$ADB_SERIAL" shell dumpsys meminfo com.jag.home.dev
adb -s "$ADB_SERIAL" shell dumpsys meminfo tv.dumbtv.home
adb -s "$ADB_SERIAL" shell cat /proc/meminfo

# Frame counters; reset only the tested app's statistics before each trial.
adb -s "$ADB_SERIAL" shell dumpsys gfxinfo com.jag.home.dev reset
# Exercise the same left/right/up/down sequence with the remote for 30 seconds.
adb -s "$ADB_SERIAL" shell dumpsys gfxinfo com.jag.home.dev framestats
# Repeat separately for tv.dumbtv.home.

# Crash/ANR observations; inspect locally and redact before sharing.
adb -s "$ADB_SERIAL" logcat -d -b crash
adb -s "$ADB_SERIAL" shell dumpsys activity lastanr
```

`am start -W` may report only an existing activity/task handoff on a warm launch,
not time until icons are drawn. Label that result correctly. For cold starts,
reboot normally and launch once before any automatic process-kill operation;
do not force-stop the working dumbtv launcher/accessibility integration.
Use an external high-frame-rate camera or platform frame trace to measure from
Select to fully drawn favorites, including artwork, and from the physical Home
press to the stable baseline screen. JAG v1 has no HOME binding: production Home
latency is **unmeasured/not applicable** until a separately authorized migration.
For a comparable preview-return metric, measure Back from the same app or an
explicit ADB launch for both launchers and name the method.

From `dumpsys meminfo`, record TOTAL PSS, Java/native heaps and graphics PSS,
both visible and after the same app has played for five minutes. From
`/proc/meminfo`, record MemAvailable before and after each app launch. Do not
equate a system-wide fluctuation with memory freed by JAG. Test 20 navigation
cycles, repeated settings changes, multiple wallpapers and at least 10
launch/return cycles. Record janky-frame percentage, frame-time percentiles,
focus/input delay, any ANRs and crashes. Some Fire OS builds omit counters; mark
those unavailable and use camera/frame-trace evidence rather than inventing data.

| Metric | Original dumbtv 1.2 | JAG Home Dev | Conditions |
|---|---|---|---|
| Cold fully drawn time | Unmeasured | Unmeasured | Same reboot/start procedure |
| Warm explicit launch, median / p95 | Unmeasured | Unmeasured | 10+ trials |
| Physical HOME return time | Unmeasured | Not applicable in preview | Existing binding retained |
| Comparable Back/explicit return | Unmeasured | Unmeasured | Same streaming app |
| Visible / background TOTAL PSS | Unmeasured | Unmeasured | Same wallpaper and idle period |
| MemAvailable before / after apps | Unmeasured | Unmeasured | No cleanup attribution |
| Janky frames / input delay / ANRs | Unmeasured | Unmeasured | Same navigation sequence |

Populate this table only with actual device observations. The target of roughly
1 GB physical RAM motivates bounded allocations; APK size and host test success
are not measurements of Fire Stick runtime memory or responsiveness.
