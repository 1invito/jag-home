# JAG Home v2 development architecture

The upstream launcher uses native Java Views, an asynchronous PackageManager
catalog, canvas-drawn tiles, file-based settings and a bare SDK shell build.
`Home` coordinates wallpaper, package and time updates; `Options` edits settings;
`Catalog` resolves installed activities and artwork. `Guard`, `Keeper`,
`BootReceiver` and `install.sh` implement device-specific HOME takeover, including
global settings changes. Those components are deliberately **not linked** into JAG.

JAG reuses those design patterns and the MIT-licensed SDK build pipeline in a
separate source/resource tree. Upstream source, manifest, signing key, scripts and
release checksum remain intact. `com.jag.home.dev.Home` is an ordinary launchable
activity, not a HOME intent handler. There are no services, receivers, providers,
permissions, network requests, hidden API calls or boot actions in its manifest.

The six favorites are fixed package identities, with a persisted permutation.
PackageManager resolves actual launch activities; missing, disabled or restricted
apps keep their place and display an unavailable state. App artwork is rasterized
off the UI thread to bounded card-size bitmaps. Wallpaper decoding uses bounds
first, power-of-two sampling, and an output cap of 1920 × 1080. A single decoded
wallpaper is cached while visible; images are released when the activity stops.
Only package/time broadcasts while visible trigger updates; there is no polling.

The native focus graph has six favorites, zero to four compact recents and four
dock actions. It is shared with host tests and bounds movement at every edge,
skipping empty/disabled rows. Focus is restored by a stable row/package token,
so changed ordering or removed recents cannot restore an invalid numeric slot. Settings
are native focusable rows and dialogs; Back dismisses a dialog, then returns
from settings. Long-press or Menu on a favorite opens its options.

## Memory capability decision

On Android 9, `killBackgroundProcesses(package)` plus the normal
`KILL_BACKGROUND_PROCESSES` permission can ask ActivityManager to kill eligible
background processes. It is a void request, not a per-process result, and is not
force-stop, cache clearing, data clearing or package disabling. Android can
restart a killed process.

However, Android 9's ActivityManagerService filters `getRunningAppProcesses()`
to the calling UID unless the caller has privileged task visibility. Running
tasks/services are also restricted. An ordinary launcher cannot establish that a
third-party app has no active playback or shared protected process. A fixed app
allowlist alone cannot prove this. Fire OS may impose further restrictions.

Therefore the app has an **empty automatic-cleanup allowlist** on every API level
and does not request the kill permission or call process-control APIs. V2 replaces
the misleading Free Memory confirmation with **Memory Status**: an actual
`ActivityManager.getMemoryInfo()` snapshot, an explicit no-apps-stopped statement,
and a manual application-management
shortcut. The manual list excludes the VPN, both launchers and all system apps;
the OS's general management screen remains reachable if package details are not
supported. JAG does not operate any destructive Settings controls. No claimed
"freed" count is inferred from unrelated memory fluctuations.

Sources checked against AOSP Android 9.0.0_r1:

- [ActivityManager.java](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-9.0.0_r1/core/java/android/app/ActivityManager.java):
  `getMemoryInfo`, `getRunningTasks`, `getRunningServices`, `killBackgroundProcesses`.
- [ActivityManagerService.java](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-9.0.0_r1/services/core/java/com/android/server/am/ActivityManagerService.java):
  `getRunningAppProcesses` UID filtering and `killBackgroundProcesses` checks.
- [Android API reference](https://developer.android.com/reference/android/app/ActivityManager#killBackgroundProcesses(java.lang.String)):
  current API restrictions differ on Android 14+, so no cross-version cleanup
  assumption is made.

## Compatibility and validation limits

Compile against Android 25, minimum 25, target 28, JDK 17, Java 8 bytecode,
build-tools 34.0.0, no Gradle and no third-party runtime libraries. The primary
device remains Fire OS 7.7.1.7 / Android 9. Package names supplied by the owner
are queried, never assumed installed. No activity names for third-party apps
are hardcoded. On Fire OS the system document picker and Settings intents may
be absent; local app-owned wallpaper files and guarded intent fallbacks handle
this. Physical focus behavior, Fire OS Settings availability, playback/VPN
continuity, PSS, frame timing and the existing dumbtv Guard's treatment of a
manually opened JAG activity require device validation. A host build cannot
establish performance or HOME integration on a Fire Stick.

## V2 scene, lifetime and local history

`Theme` provides four immutable color/behavior token sets for one implementation.
`Atmosphere` creates static shader geometry only when layout changes. `Tile`
caches its rounded path, text, artwork destination and sheen, with an exact 16:9
artwork area. `DockButton` builds original line-icon paths on size changes.
Directional navigation performs bounded arithmetic and focus changes only:
there is no image decode, PackageManager query, layout rebuild or per-frame
object construction in these draw methods. A stateful transparent background
marks custom focus as handled without using API 26-only calls or hidden APIs,
avoiding Android's rectangular fallback highlight behind the rounded border.

The visible scene is rebuilt on resume to apply settings/history, not on focus.
Package broadcasts are registered only while resumed. Concurrent package changes
coalesce into at most one active load and one requested follow-up. Catalog and
fresh launch resolution run on a single worker. A lifecycle generation rejects
stale results and recycles only never-displayed worker bitmaps. Visible bitmaps
are detached and dereferenced on stop; they are not recycled underneath the GPU.
Recent cards share the exact favorite catalog objects and bitmap references.

Six banner bitmaps at the maximum 384 × 216 ARGB size require about 1.9 MiB of
pixel storage; this is an allocation bound, **not measured TOTAL PSS**. One
1920 × 1080 RGB_565 wallpaper is about 4 MiB; alpha formats may require about
8 MiB. Graphics context, textures, views and system overhead require actual
PSS measurement. Reduce Motion, Ultra Minimal, low-RAM classification and
running-low memory callbacks disable scaling; critical running-memory pressure
also drops wallpaper references to expose the default scene.

`RecentHistory` normalizes at most four known favorite IDs. Only the successful
`Ui.open` path records a launch in the private existing properties file. Failed
or blocked launches and other apps' activity are never recorded. No timestamps,
usage access, polling, content metadata or extra listeners are needed. Disabling
history stops both display and recording. Visual reset preserves its entries,
history opt-out and favorite ordering; clear-history is a separate confirmation.
V1 keys retain their meaning, including explicit Ice accent, clock and ordering.
New missing/invalid keys have safe defaults; no destructive migration runs.

`SIGNING.md` documents retained certificate verification and the unsigned CI
artifact. `HOME_MIGRATION_V2.md` studies the actual upstream Guard and describes
future snapshots and rollback only. Neither document adds a device mutation path
to the application or build.
