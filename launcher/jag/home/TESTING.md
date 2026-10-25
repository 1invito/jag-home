# V3 HOME integration validation

## Recorded cloud results

Validated on 2026-10-25 with JDK 17, Python 3.12.14, build-tools 34.0.0 and an
isolated AOSP Android 9 / API 28 emulator:

| Check | Result |
|---|---|
| Shared preview models | 3,602 assertions and 5 safety tests passed |
| Stable Guard policy | 15 assertions passed |
| Host protocol, backend and migration recovery | 42 tests passed, including detached-parent-death recovery and fresh reboot evidence per trial |
| Stable manifest/source boundaries | 5 safety tests passed |
| Installed native stable app | 16 assertions passed, including coexistence with installed preview |
| Actual accessibility binding | Disarmed binding, activity resolution/foreground, no-content registration and unsupported-AOSP refusal passed; original emulator settings restored |
| Upstream regression suite / catalog | 77 tests passed / 29 entries validated |
| Builds | Preview and stable unsigned builds passed; disposable-key stable signing and signature verification passed |
| Signing failure paths | Missing key, wrong expected certificate and preview-certificate reuse rejected without replacing the prior artifact |

The validated stable identity is `com.jag.home`, versionCode 5, versionName
`0.5-history`, min/target API 28. Disposable-key APKs are QA artifacts, not the
owner-signed production release. No production owner key was created in the
cloud. Physical Fire TV and native Windows execution remain unrun.

Run from the repository root with JDK 17 and Python 3.10+:

```bash
./launcher/jag/test.sh
./launcher/jag/test-home.sh
./launcher/jag/build.sh --unsigned
./launcher/jag/build-stable.sh --unsigned
```

`test-home.sh` executes the production pure Java Guard gate and Python migration
state machine. The local FakeDevice is explicitly not an ADB connection. Tests
cover exact original setting presence (absent/empty/literal-null), current-user
parsing, read-only preflight/backup, installation without arming, disarmed staging,
preserved unrelated services/alarm options, exclusive handover, duplicate Guard
prevention, failed binding/arming, crash/ANR, first-draw timeout, profile changes,
pending-write journal recovery, missing/stale watchdog, bounded reboot grace,
real boot-generation acknowledgement, performance acceptance and exact/idempotent
rollback. The subprocess test terminates the parent without executing cleanup;
the detached child restores the mock device independently. No streaming/VPN data
or Amazon package mutation is permitted by the fake command audit.

Stable safety tests inspect the actual manifest/resources and source boundaries:
one accessibility service, no content retrieval/key filtering/overlay, DUMP-gated
owner receiver, a boot-only receiver, no HOME resolver, one normal permission,
no auto-generated production key and no protected-package cleanup commands.
Existing preview safety barriers remain in force for its separate manifest and
no-op adapter.

## Actual Android 9 emulator checks

Use a disposable API 28 AOSP emulator on an isolated ADB port. Never run this
suite on a household device. The script requires an emulator serial and
ranchu/goldfish hardware **before any installation or settings write**.

Supply a disposable test signing key separately; do not use/publish a production
key for validation. With the stable signing environment already set:

```bash
JAG_TEST_SERIAL=emulator-5558 JAG_TEST_ADB_PORT=5038 \
  ./launcher/jag/test-stable-emulator.sh
```

The test APK checks separate stable/preview UIDs and storage when the preview is
present, minimal installed permissions, real service/receiver registration,
no HOME resolver, disarmed native UI, visible focus and whitelist import that
retains history/rejects unrelated private keys. If the preview is absent,
coexistence is explicitly reported unrun rather than inferred.

The host binding test captures the emulator's own accessibility values, enables
only the **disarmed** stable service, verifies its actual binding and status,
checks `canRetrieveWindowContent=false`, proves that AOSP refuses Fire OS arming,
then restores the captured emulator values. It does not simulate successful Fire
OS activation. Two real API details were diagnosed during validation: API 28
rejects `--user` on `settings list/delete`, and AccessibilityManager may update
its enabled flag itself after registration. The implementation now handles
these rather than treating initial mock success as device evidence.

## Fire Stick owner acceptance (unrun in the cloud)

Follow [README.md](README.md), with the correct owner-signed package. Preflight
must resolve actual activities, verify sole user 0, enabled original launchers
and the bound original Guard. No trial occurs if compatibility/recovery fails.
Installation alone remains disarmed. During the bounded owner trial:

1. Confirm original dumbtv and stable JAG work visually before handing over.
2. Verify the new Guard can bind while disarmed and old Guard remains active.
3. Verify only one redirector is active after handover and actual Home lands on
   JAG from each of the five players and ExpressVPN.
4. Test D-pad, Back, long Home quick menu, Settings, remote pairing and the
   fallback app; monitor streaming logins/playback/VPN connectivity.
5. Arm `--reboot-check`, manually restart and verify fresh first draw/binding and
   a changed boot generation. The watchdog's 90-second boot grace is bounded by
   the original trial deadline and does not ignore recorded crashes.
6. Exercise timeout and `--rollback` while ADB remains available. Confirm the old
   Guard rebinds and physical Home returns to dumbtv, preserving debloat state,
   original services, complete alarm constants and app data. Do not manufacture a
   real crash on the household device merely to repeat a mock failure test.
7. Measure physical Home-to-stable-artwork against dumbtv: at least ten warm
   trials, median/p95, matched player/VPN/network/display/thermal conditions and
   wallpaper complexity. Keep first cold timing separate. Use camera/frame
   tracing; label `am start -W` as explicit activity handoff timing, not physical
   HOME latency. Record PSS/graphics breakdown and repeated-cycle memory trend.
8. Populate the provided measurement template only with real results. The owner
   tool refuses permanent acceptance without all physical checks, fresh reboot
   evidence, finite measurements, ≤10% additional median/p95 latency and ≤15 MiB
   additional visible TOTAL PSS. Account for the PC watchdog's bounded diagnostic
   traffic when judging comparable conditions. Do not recommend activation if
   responsiveness, memory, playback, VPN or recovery regresses.

Owner-run benchmark examples (use an explicitly selected transport on the
owner's PC, keeping logs in ignored private `report/`):

```powershell
adb -s $env:ADB_SERIAL shell am start -W -n com.jag.home/com.jag.home.dev.Home
adb -s $env:ADB_SERIAL shell dumpsys meminfo com.jag.home
adb -s $env:ADB_SERIAL shell dumpsys meminfo tv.dumbtv.home
adb -s $env:ADB_SERIAL shell dumpsys gfxinfo com.jag.home framestats
adb -s $env:ADB_SERIAL logcat -d -b crash
adb -s $env:ADB_SERIAL shell dumpsys activity lastanr
```

No physical-device latency/PSS/VPN/remote/reboot results are supplied by this
cloud task. Windows subprocess/locking code uses standard native Python APIs,
but cloud execution and mock/native tests run on Linux. Validate read-only
Windows preflight before any owner trial. ADB/network/host loss prevents a
guarantee of automatic recovery, and no such guarantee is made.

| Metric | dumbtv | stable JAG | Status |
|---|---|---|---|
| Physical HOME warm median / p95 | Unmeasured | Unmeasured | Owner measurement required |
| First cold/boot HOME | Unmeasured | Unmeasured | Owner reboot trial required |
| Visible/background TOTAL PSS | Unmeasured | Unmeasured | Matched visuals required |
| Streaming/VPN/long-Home continuity | Owner baseline | Unrun for new integration | Physical acceptance required |

The separate development v2 UI is owner-reported as working on the Fire Stick.
That report is not evidence that Fire OS authorizes or performs well with this
new stable certificate/service.


## Explicit unmeasured-performance owner waiver

A separate host CLI option `--accept --waive-performance` is available for
owners who decline the timing experiment but have confirmed every physical
check and a fresh reboot in the **current** trial. The evidence file contains
`performance_waived: true` and six true physical-check fields, with **no
numbers**. The journal records `unmeasured_performance_waiver: true`. This
is deliberately **not** equivalent to passing the <=10% latency or <=15 MiB
PSS budgets; these remain unknown under the waiver. The mode keeps identity,
watchdog heartbeat, installed APK identity, current foreground/draw/crash/ANR,
exclusive service binding, reboot-generation verification, expiry checks, and
rollback. The owner confirms the explicit waiver separately from `KEEP JAG`.
Regression tests cover acceptance rejection before reboot, missing physical
checks, fabricated metrics in waiver mode and retained original rollback.
