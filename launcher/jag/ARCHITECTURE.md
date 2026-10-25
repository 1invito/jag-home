# JAG Home development architecture

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

The native focus graph has six app positions and three actions. The graph is
shared with host tests and explicitly bounds movement at every edge. Settings
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

Therefore v1 has an **empty automatic-cleanup allowlist** on every API level and
does not request the kill permission or call process-control APIs. Free Memory
shows the requested confirmation, an explicit unsupported explanation, an actual
`ActivityManager.getMemoryInfo()` snapshot, and a manual application-management
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
