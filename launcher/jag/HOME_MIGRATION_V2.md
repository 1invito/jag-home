# Future stable HOME migration — design only

Historical v2 design: the implemented v3 tool and its narrower API 28-only
contract are documented in [home/README.md](home/README.md). In particular, v3
does not change the Android default HOME resolver or use an overlay, and handles
Android 9's user-0-only list/delete limitation explicitly. Do not run design
pseudocode below as an installer; use the tested owner-operated modes.

**No production migration or HOME takeover is implemented or executed in this
branch.** The preview remains an ordinary `com.jag.home.dev` application. Every
device command below is a future owner-operated planning example. This cloud
task does not contact a household TV.

## Why changing the default HOME activity is insufficient

The actual upstream [Guard](../src/tv/dumbtv/home/Guard.java) watches only
`TYPE_WINDOW_STATE_CHANGED` for Amazon's verified Home activity. Fire OS handles
HOME before a third-party accessibility filter sees the key. A normal activity
start immediately afterward encounters Android's approximately five-second
app-switch delay. Guard sends a system-fired alarm `PendingIntent`; the installer
sets global `alarm_manager_constants` to include `min_futurity=0` so that alarm
can fire immediately. Guard covers Amazon's window with an accessibility overlay
until dumbtv has drawn, with a 1,500 ms safety uncover, a 150 ms launch fallback
and a 1,000 ms repeated-event limit. Arrival cancels queued launches.

Some Fire OS builds additionally restrict accessibility binding. Fire OS 6 uses
a different Keeper path; that is not a Fire OS 7 migration solution. A default
HOME resolver result, a saved service string, a PID or an open app window alone
does not prove that a service bound or that physical HOME works correctly.

Do not copy `launcher/install.sh` and substitute package names. It also changes
alarm settings, rewrites accessibility lists, removes another launcher's known
service and changes other device configuration. None of those broad operations
are appropriate to a narrow migration of this already working device.

## 1. Preconditions and private snapshots

Before implementing any stable migration, require explicit owner authorization
and successful v2 physical-device acceptance. Validate the actual model/build,
SDK 28, ADB authorization, one selected device, enabled/installed dumbtv and
development packages, package versions and signer fingerprints. Check the model's
catalog report; do not invent menu paths. Confirm the existing Home/long-Home,
remote, Settings access, streaming accounts and ExpressVPN work first.

Keep `tv.dumbtv.home` installed and enabled. Use a new stable package such as
`com.jag.home`, with its own securely retained certificate and independently
tested components. Do not reuse the development key for production or attempt
to update dumbtv with a different signer. Back up both APKs and JAG appearance
settings privately; do not export streaming-app data or credentials.

Write a private, restrictive-permission snapshot under ignored `report/`, with
these typed fields:

| Snapshot | Required evidence |
|---|---|
| Current Android user | `adb shell am get-current-user`; retain that exact user |
| Model, Fire OS build, SDK | `getprop`, limited to the necessary properties |
| Existing package versions/signers | PackageManager metadata plus pulled APK certificate verification |
| `secure.enabled_accessibility_services` | Exact value and a separate **present** flag |
| `secure.accessibility_enabled` | Exact value and present flag |
| `global.alarm_manager_constants` | Entire exact string and present flag, not just `min_futurity` |
| Actual default HOME component | Current user's resolved HOME plus package-manager evidence that it is a unique restorable default |
| Actual bound services | Current user's bound-service section from accessibility diagnostics |
| Enabled/disabled package state | Read-only baseline sufficient to establish that migration did not change it |

For example, use `settings --user USER get secure KEY` and
`cmd package resolve-activity --brief --user USER -a android.intent.action.MAIN
-c android.intent.category.HOME` through the explicitly selected ADB transport.
Capture the presence flag using a key-specific `settings list` filter. Preserve
the distinction among an absent key, an empty value and a literal `null` string;
`settings get` alone does not distinguish them. Store only the selected keys,
not a broad dump of accounts/settings. Strip CR transport terminators, not
meaningful value contents.

If the HOME result is ambiguous, points to a resolver rather than an actual
default, or cannot be restored on this build using a tested supported command,
**block migration**. Do not guess a component or treat an Amazon resolver result
as evidence that the current dumbtv Guard is unnecessary.

Record and test the device's real Settings recovery route with the owner before
altering any HOME mechanism. ADB must remain reachable independently of the
launcher; test a second authorized session. Keep private snapshots off Git,
issues, screenshots and PRs.

## 2. Stable package, service and signing design

Build the stable app under `com.jag.home` with an owner-controlled production
certificate; every future update must use that certificate. Separately design
and review any `com.jag.home/.Guard` component. The preview APK has no such
component, HOME filter, boot receiver or privileged permissions.

First validate the proposed service/overlay/alarm behavior on a matching Fire OS
7 device without changing the working Guard. Do not run two competing HOME
redirectors during a handover. A migration may proceed only once the new
implementation and a scoped handover have been tested; do not replace the
working Guard with an untested port. No Usage Access or broad accessibility
permission should be repurposed for recents or memory management.

Keep the production signing key offline or in a protected signing service; do
not place it in ordinary PR CI. Verify the installed stable package's signer,
version and declared components before attempting to enable it. A separate
package does not inherit dev settings; migrate only a bounded, explicitly
exported appearance/order/history file if the owner chooses to.

## 3. Narrow transaction and failure detection

The future migration controller must have a durable journal with stages:
`snapshot complete`, `stable installed`, `handover applied`, `binding verified`,
`physical acceptance complete`. It must verify the snapshot against current
values immediately before the first mutation; any concurrent change blocks
automatic application.

Arm an **external ADB-side watchdog** with the snapshot and a short, bounded
trial timeout before changing the binding. Do not rely on the new launcher or
its service to roll itself back after crashing. Installation alone leaves
dumbtv active and safe. A later authorized handover changes only the specific
old/new HOME service membership and, if proven necessary, the single existing
alarm option while retaining every unrelated entry. Record each read-back
result. Never disable or uninstall Amazon/dumbtv/system packages or unrelated
accessibility services.

Verify the new service in the current user's **bound** services, the actual
foreground activity, a first-draw readiness signal from the stable app, absence
of crash/ANR evidence and reliable response to the physical remote. A setting
write returning zero does not establish any of those outcomes. A definitive
crash, missing binding or bounded first-draw timeout can trigger rollback.
No PID-only or screenshot-only check should label the handover successful.

Keep the watchdog armed until the owner confirms physical HOME/Back/long-Home,
navigation, Settings, remote pairing, multiple streaming launches, account
continuity and ExpressVPN connectivity. Record median/p95 Home latency and PSS
against the existing dumbtv baseline under matching conditions. ADB/network loss
prevents guaranteed automatic rollback; do not claim otherwise.

## 4. Exact rollback logic

For an uncontended transaction, rollback restores **captured values**, never
assumed defaults. The future controller must:

1. Reconnect to the same selected device and Android user; verify identity
   locally without publishing identifiers. Read current values and compare them
   with the transaction's expected post-change values.
2. Remove only the new stable service from the binding using the tested scoped
   handover, preserving unrelated services. Cancel only the stable app's own
   queued HOME launch/overlay through a reviewed recovery endpoint if reachable.
   Do not terminate the existing dumbtv app or networking/VPN components.
3. Restore the captured HOME component using a supported, preflight-tested
   `cmd package set-home-activity --user USER COMPONENT` operation. If that command
   is unsupported or read-back disagrees, stop automation and use the recorded
   manual recovery path rather than guessing alternatives.
4. Restore each captured secure/global key exactly: **put** its captured string
   if it existed; **delete** it if absent. Restore the complete alarm constants
   string. Restore the original accessibility membership and enabled value
   without removing any originally present unrelated service.
5. Read back presence/value, resolve HOME again and verify the original Guard
   is bound. Resolve and launch dumbtv using PackageManager's actual launch
   intent/component. Test the physical Home button and Settings before declaring
   recovery complete. Keep the stable package installed but inactive until the
   owner has verified recovery; only then offer its removal.

The restoration primitive has this precise contract (design pseudocode):

```python
def restore_key(namespace, key, captured):
    if captured["present"]:
        adb_shell(["settings", "--user", snapshot["user"],
                   "put", namespace, key, captured["value"]])
    else:
        adb_shell(["settings", "--user", snapshot["user"],
                   "delete", namespace, key])
    assert read_key_with_presence(namespace, key) == captured
```

Here `adb_shell(args)` sends one POSIX-shell-quoted command using
`shlex.join(args)` through `subprocess.run(["adb", "-s", selected_serial,
"shell", command], check=True)`. This preserves empty strings and shell
metacharacters instead of interpolating captured values into command text.
The snapshot's user is converted to a string before constructing arguments.
The real implementation must test exact round-trips on the target build.

If unrelated settings/service membership changed after the snapshot, exact
whole-value rollback would overwrite someone else's work. In that case, remove
only the new stable component and restore only the displaced dumbtv membership,
preserving the newly observed unrelated services; do not blindly replace the
list, turn accessibility off or overwrite concurrent alarm changes. Report the
conflict and require owner review of the remaining differences. Exact snapshot
restoration and preservation of later external edits cannot both be promised.

## 5. Recovery if HOME fails or JAG crashes

With ADB still available, the external controller can apply the verified rollback
above and explicitly launch the existing dumbtv package through its resolved
launch intent. It must not run upstream `restore.sh`, `all.sh`, `install.sh` or
debloat scripts; those alter unrelated working state.

Without usable ADB, use the Settings recovery control/menu path verified in
preflight (often reachable through the device's native long-Home quick panel,
but that must be checked on this exact build). Disable **only** the new stable
HOME service using the recorded device-supported route, then open the retained
dumbtv app. If needed, manually remove only the stable JAG package after recovery
is confirmed. Do not clear dumbtv data, factory-reset the device, disable system
packages or change DNS. Do not invent a menu path if Fire OS does not expose the
required recovery control. In that case migration must remain blocked until an
independent recovery route is established.

This plan still requires a separate implementation, review, owner approval and
real Fire OS validation. It is not authorization to deploy or change HOME now.
