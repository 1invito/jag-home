# JAG Home v2 validation and Fire OS acceptance

V3 stable HOME integration has separate tests and owner deployment gates in
[home/README.md](home/README.md). V2 UI results below do not establish Fire OS
authorization or latency for the new Guard. No Home handover is performed by
the development APK or the cloud task.

## Recorded cloud validation

Validated on 2026-10-25, with the retained v1 cloud certificate:

| Check | Observed result |
|---|---|
| Production Java models | 3,602 assertions passed |
| Manifest, no tracking/mutation, signing/CI safety barriers | 5 tests passed |
| Actual v1 APK update | 18 migration assertions passed; 5 v1 preparation assertions |
| Native missing-app, theme, memory, wallpaper and settings UI | 80 assertions passed |
| Native fixture launches, recents, shared artwork and focus | 65 assertions passed |
| Signing guards | Missing key/password and wrong certificate rejected; existing artifact preserved |
| Signed and unsigned SDK build modes | Passed; unsigned output confirmed not signed |
| Signed v2 development APK | 45,664 bytes, versionCode 2, min API 25 / target 28, zero permissions |
| Upstream fake-TV regression suite | All 77 tests passed |
| Catalog, Bash syntax, upstream release manifest, diff whitespace | Passed; all 29 device records valid |
| Physical Fire Stick performance/playback/VPN | Unrun; device not contacted |

The signed output's public certificate fingerprint matches the retained cloud
v1 artifact. This is not verification of the APK installed on the user's TV.

## Automated scope

The cloud task does not contact the owner's Fire Stick. Runtime testing uses a
headless AOSP Android 9 / API 28 emulator at 1920 × 1080, with software CPU/GPU
emulation. This is compatibility/functional evidence, not Fire OS performance.

From the repository root, with JDK 17 and SDK 25/build-tools 34 configured:

```bash
./launcher/jag/test.sh
./launcher/jag/build.sh --unsigned
# To produce the installable update, securely supply the retained v1 key/env:
./launcher/jag/build.sh
python3 -m unittest discover -s tests -v
python3 catalog/validate.py
for f in scripts/*.sh launcher/*.sh launcher/jag/*.sh; do bash -n "$f" || exit 1; done
./launcher/release-check.sh --manifest
```

Host tests exercise the production models: all navigation vertices for zero to
four recents, bounded/deduplicated/validated history, failed/disabled launch
recording, theme persistence, motion overrides, appearance reset, legacy v1
configuration, responsive layout margins, missing apps, wallpaper sampling
(including odd dimensions) and fail-closed memory policy. Safety barriers reject
permissions, HOME/services/receivers, usage/accessibility tracking, process or
privileged settings mutation, network APIs, embedded signing passwords and
silently regenerated update keys. CI runs the models/safety/syntax/build checks
and uploads only a clearly named **unsigned** artifact.

### Disposable Android 9 runtime suite

Use only a disposable AOSP API 28 emulator. The runner requires an `emulator-N`
serial, ranchu/goldfish hardware and API 28 before any installation. It defaults
to a separate ADB server port 5038; it does not discover/connect to a TV. It
resets JAG's test settings and installs five **JAG Test Fixture** packages under
favorite identities to test actual PackageManager resolution/launches. These
fixtures are not real streaming apps and never enter the delivered launcher APK.
If an emulator already has a genuine favorite app, the runner aborts instead of
removing it. Only packages marked `versionName=jag-fixture` are replaced.

```bash
# Use your private retained signing environment; do not print credentials.
JAG_TEST_SERIAL=emulator-5558 JAG_TEST_ADB_PORT=5038 \
  JAG_V1_APK=/private/path/to/original-cloud-v1.apk \
  ./launcher/jag/test-emulator.sh
```

The optional `JAG_V1_APK` must be the original same-key versionCode 1 development
APK. The runner installs it only on the disposable emulator, seeds exact v1
settings, updates to v2 using `install -r`, and verifies retained order/appearance
plus safe defaults for new fields. Without that file, migration is **not run**;
missing/fixture suites still execute. Never use the downgrade/seed test sequence
on the household Fire Stick.

The runtime phases are:

1. `seed-v1`: writes only v1-schema private test settings under the v1 APK.
2. `migration`: verifies ordinary APK-update data retention and new defaults.
3. `missing`: actual PackageManager unavailable states, six favorites/hidden empty
   recents, remote edges/dock, dialogs, real memory information, theme selection
   and screenshots, settings recreation/cancel, real sampled decoder failures
   and memory-pressure degradation.
4. `fixtures`: real successful launches into local fixture activities, four-entry
   recency, duplicate prevention, primary/recent/dock transitions, one shared
   artwork object, true 16:9 geometry, tracking opt-out, re-enable, history
   preservation on visual reset and explicit clear/focus recovery.

Results are in ignored `launcher/jag/build/emulator-results.txt`. Each requested
phase must report `JAG_DEVICE_TESTS_PASSED [phase]`, not merely an install success.
On failure a diagnostic screenshot is written to JAG's own external-files folder.
The suite also saves theme/recents screenshots; fixture screenshots do not
represent the real apps' logos. No zero-test or skipped migration is a full pass.

For signed-build negative tests, `test-signing.sh` checks that absent credentials
or a temporary wrong certificate fail without replacing the valid APK or
creating a replacement key. Unsigned artifacts are explicitly checked as unsigned.

## Manual Fire OS 7.7.1.7 acceptance

Use [README.md](README.md) and [SIGNING.md](SIGNING.md) to compare the installed
certificate before any update. Keep `tv.dumbtv.home` and the working development
preview installed. Check the existing AFTSSS catalog report and actual device
menus; its partial upstream status is not evidence of JAG v2 compatibility.
Do not run upstream installer/cleanup/restore scripts or emulator test fixtures.

1. Record a private baseline: Home/long Home, Settings access, remote pairing,
   enabled/disabled-package state, streaming accounts, current VPN connection,
   JAG favorite order and appearance. No broad logs or identifiers go into Git.
2. Only after signatures match, manually update `com.jag.home.dev` using
   `adb install -r` and open `.Home`. Confirm v1 order, clock/labels, wallpaper,
   dimming and other preferences survive. Missing v2 keys start at Cinematic and
   recent tracking On, with no invented history.
3. Test all six real apps' native banners/icons, aspect ratio and launch intents,
   including the square-icon fallback. All login state stays intact. A missing,
   disabled, restricted or uninstalled app keeps its slot and fails honestly;
   no failed launch enters history and no app is automatically enabled/installed.
4. Traverse every primary/recent/dock transition with Up/Down/Left/Right at recent
   counts 0, 1, 2, 3 and 4. Select, long Select and Menu must work. Back dismisses
   dialogs, returns from Customize, then exits the preview. Each focused control
   needs a visible rounded indicator with animations both on and off. No square
   platform fallback should obscure the card/label.
5. Launch five different favorites from JAG; confirm the last four unique apps,
   newest first. Relaunch one and verify it moves to the front. Opening an app
   outside JAG must not update this local list. Disable recents and launch again:
   the row is hidden and history unchanged. Re-enable, reset visual defaults,
   then explicitly clear history; order survives and focus stays valid.
6. Inspect all four themes at normal sofa distance and screen densities/overscan.
   Cinematic shows the selected photo; Midnight/Graphite retain their static
   backgrounds; Ultra Minimal has no gradients, wallpaper, scale motion or extra
   date/labels. Switching back restores stored photo/preferences. Test every
   setting, including Reduce Motion and clock 12/24-hour/visibility. Repeat after
   returning from a streaming app and after an ordinary app restart.
7. Test JPG/PNG/WebP selection through the remote, no document picker, a deleted
   selection, corrupt bytes, large/odd dimensions, alpha images, dimming and
   Default. The navy fallback must remain usable; focus changes never rotate or
   re-decode wallpaper. If memory pressure suppresses wallpaper/motion, the
   static background and focus ring must remain usable.
8. Open Memory Status during playback/VPN operation. It must show real available
   memory or a read failure and say no apps were stopped. App-management/settings
   shortcuts are manual only; do not stop playback/VPN or clear app data. Check
   unsupported Settings intents fail gracefully. There is no cleaner service.
9. Verify system Settings and Return to existing dumbtv. **Physical Home must
   continue using the existing dumbtv binding**, not JAG. Long Home remains the
   native quick panel. No new accessibility component or HOME filter is present.
10. Perform at least 20 remote navigation cycles and ten launch/return cycles,
    including Netflix, YouTube, another player and ExpressVPN. Check focus,
    artwork restoration, crashes, ANRs and memory trend. Confirm disabled-package
    state, accounts, DNS/update behavior, VPN and original Home integration remain
    unchanged. No production HOME migration is part of this acceptance pass.

## Performance measurements and targets

Use the same Fire Stick, resolution, network, thermal conditions, foreground
streaming state, idle interval and wallpaper complexity for all comparisons.
Measure dumbtv 1.2, JAG v1 if available, and JAG v2. Record the first cold launch
separately, then **at least ten warm trials**, reporting median and nearest-rank
p95. A small APK is not evidence of startup speed, PSS or GPU behavior.

Acceptance targets, **not measured achievements**:

- No noticeable directional-input lag, missing focus or traps.
- No app-launch regression attributable to JAG; aim for warm-launch median/p95
  within approximately 10% of the measured comparable dumbtv result.
- Aim for no more than roughly 10–15 MiB additional visible TOTAL PSS under
  comparable visuals. Inspect native/Java/graphics breakdowns and memory trend,
  not only one snapshot or system-wide MemAvailable.
- No crashes/ANRs during ordinary navigation and repeated launch/return cycles.
- No recurring background CPU activity while another app is foregrounded.
- If targets conflict with effects, prioritize static focus/Reduce Motion or
  Ultra Minimal. Report a regression rather than reclassifying the target as met.

Use Windows Git Bash with an explicitly selected `ADB_SERIAL` and private
ignored report folder:

```bash
mkdir -p report/jag-v2-benchmark
# Resolve the real baseline component; do not invent its activity class.
adb -s "$ADB_SERIAL" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER -p tv.dumbtv.home

# Explicit warm handoff/start timing (not physical HOME-to-drawn latency).
adb -s "$ADB_SERIAL" shell am start -W -n com.jag.home.dev/.Home

# Visible and background PSS under matched conditions, before/after the same app.
adb -s "$ADB_SERIAL" shell dumpsys meminfo com.jag.home.dev
adb -s "$ADB_SERIAL" shell dumpsys meminfo tv.dumbtv.home
MSYS_NO_PATHCONV=1 adb -s "$ADB_SERIAL" shell cat /proc/meminfo

# Frame timing: reset only this app's counters, then use the same remote sequence.
adb -s "$ADB_SERIAL" shell dumpsys gfxinfo com.jag.home.dev reset
# Navigate for the same 30-second interval in each launcher.
adb -s "$ADB_SERIAL" shell dumpsys gfxinfo com.jag.home.dev framestats
# Repeat separately for the baseline package.

# Inspect crash/ANR evidence privately; redact before sharing.
adb -s "$ADB_SERIAL" logcat -d -b crash
adb -s "$ADB_SERIAL" shell dumpsys activity lastanr
# A one-shot CPU sample, not a background monitor in the application:
adb -s "$ADB_SERIAL" shell dumpsys cpuinfo
```

For repeatable explicit handoff trials, first resolve and verify the chosen
comparison app's real component, then set `COMPARISON_APP_COMPONENT` to that
value. Leave playback/account/VPN state unchanged; wait for the same stable state
in every trial. Do not force-stop dumbtv or other protected packages:

```bash
for trial in $(seq 1 10); do
  adb -s "$ADB_SERIAL" shell am start -W -n "$COMPARISON_APP_COMPONENT" > "report/jag-v2-benchmark/app-$trial.txt" || break
  # Confirm the same foreground state before each return measurement.
  adb -s "$ADB_SERIAL" shell am start -W -n com.jag.home.dev/.Home > "report/jag-v2-benchmark/warm-$trial.txt" || break
done
python3 - <<'STATS'
from pathlib import Path
import math, re, statistics
paths = list(Path('report/jag-v2-benchmark').glob('warm-*.txt'))
values = []
for path in paths:
    match = re.search(r'^TotalTime:\s*(\d+)', path.read_text(), re.M)
    if match:
        values.append(int(match[1]))
if len(values) < 10:
    raise SystemExit('Need at least 10 complete comparable warm trials')
values.sort()
print('trials:', len(values), 'median ms:', statistics.median(values),
      'p95 ms:', values[math.ceil(.95 * len(values)) - 1])
STATS
```

Use a fresh report folder for each run to avoid mixing old trials. Repeat the
same procedure with the actual dumbtv component and the same conditions. `am
start -W` may measure an existing-task handoff, not time until artwork is drawn;
label it correctly and retain warnings/status. Use a high-frame-rate camera or
platform frame trace for Select/Back-to-stable-artwork and physical remote input
latency. Current JAG v2 has no HOME binding: physical HOME timing belongs to
**dumbtv**, and production JAG HOME latency remains not applicable until a
separately authorized/tested migration.

For cold observations, reboot normally and measure the first launch separately;
do not clear accounts or stop the existing Guard. Record visible TOTAL PSS and
Java/native/graphics breakdown after a fixed idle interval, then background PSS
while the same player runs for five minutes. Repeat across ten cycles; inspect
trend for retained references rather than inferring a leak from a single GC
fluctuation. Record MemAvailable before/after app launches without attributing
system changes to JAG. Record frame-time percentiles, janky-frame percentage,
input delay, crashes and ANRs. If Fire OS omits a counter, mark it unavailable.

| Metric | dumbtv 1.2 | JAG v1 | JAG v2 | Target / method |
|---|---|---|---|---|
| First cold fully drawn time | Unmeasured | Unmeasured | Unmeasured | Separate cold trial |
| Warm median / p95 | Unmeasured | Unmeasured | Unmeasured | At least 10 comparable trials; aim ≤10% additional latency |
| Comparable Back/explicit return | Unmeasured | Unmeasured | Unmeasured | Same player and method |
| Physical HOME return | Unmeasured | Not applicable | Not applicable | Existing dumbtv binding retained |
| Visible TOTAL PSS | Unmeasured | Unmeasured | Unmeasured | Aim ≤10–15 MiB additional under matched visuals |
| Background PSS / CPU / memory trend | Unmeasured | Unmeasured | Unmeasured | Ten cycles, same idle/playback interval |
| Frame times / input delay / ANRs | Unmeasured | Unmeasured | Unmeasured | Same navigation and thermal state |

Populate this table only with actual Fire Stick observations. Emulator functional
passes and theoretical bitmap limits are not physical-device performance results.
