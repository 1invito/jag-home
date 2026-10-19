# Gotchas

Everything here cost real time to work out. Read it before debugging anything.

## The DNS filter

**`am force-stop dnsfilter.android` breaks filtering silently.** The app comes
back (always-on VPN), the VPN network re-registers, and DNS stops going through
it: every lookup goes straight to the router. The traffic log goes quiet, which
is the tell. Recovery is a reboot, not a restart. Never force-stop it — the one
exception is `scripts/dnsfilter.sh`, which stops it during setup and reboots
immediately afterwards so the pushed list is the one that loads.

**Without storage permission the blocklist import writes a 234-byte header.**
This only matters for the `filterAutoUpdateURL` + `file://` route, which an
easier version of this repo used. The current script avoids it: the list is
composed on the computer and pushed as `FILTERHOSTS.TXT`, with auto-update
disabled. If you do go back to `file://` imports, grant `READ_EXTERNAL_STORAGE`
and `WRITE_EXTERNAL_STORAGE` first — a sideloaded app gets no permission prompt.

**`detectDNS = true` stops capturing DNS after a network change.** Pin
`detectDNS = false`, give an explicit `fallbackDNS` (the router) and set
`routeIPs = <gateway>`. The `routeIPs` line is what puts the router's resolver
inside the tunnel.

**The list is only rebuilt at boot**, and only when `FILTERHOSTS.TXT` is missing,
the configured interval has passed, or `filterAutoUpdateURL` is set. The script
sidesteps all of that by pushing the file itself and disabling auto-update; when
you edit the list, push it again and reboot.

**`additionalHosts.txt` is ignored** on at least Fire OS 7.7 builds — entries
there never take effect. Custom hosts belong in the pushed list
(`data/blocklists/extra-hosts.txt`, merged by `dnsfilter.sh`), not in that file.

**The VPN consent dialog is phone-sized on TVs** and often ignores directional
keys and taps. Pre-granting `appops ... ACTIVATE_VPN allow` skips it entirely.

## The launcher

**The accessibility service is the only hook, and it can be cleared.** If HOME
lands on Amazon's home screen, check:

```sh
adb shell settings get secure enabled_accessibility_services   # empty = broken
adb shell settings get secure accessibility_enabled             # 0 = broken
```

Re-run `scripts/launcher.sh`. Toggling the service off and on again is what
actually rebinds it.

**Amazon's home for ~5 s on every HOME press** means
`alarm_manager_constants` lost `min_futurity=0` (an update or a settings reset).
Check with `adb shell settings get global alarm_manager_constants` and re-run
`scripts/launcher.sh`. With it set, HOME shows ~0.3 s of black, then the home
screen.

**The HOME key never reaches an accessibility key filter on Fire OS** — D-pad
keys do, HOME does not. Do not build on `onKeyEvent` for HOME; react to Amazon's
home window instead, as `launcher/` does.

**`android.settings.SETTINGS` reopens Amazon's home** on Fire OS: it resolves to a
stub (`CTSDummySettingsHandler`) that forwards to `HomeActivity_vNext`, which the
launcher then replaces. Open settings with `MAIN` +
`android.intent.category.LEANBACK_SETTINGS` instead.

**Injected keys skip accessibility filters.** `adb shell input keyevent HOME`
proves nothing about a real remote; to test like a remote, write to the remote's
input device: `sendevent /dev/input/eventN 1 172 1` (shell is in the `input`
group; find N with `getevent -pl`).

**After every boot the home screen takes ~15 s to appear**; until then Android shows
`com.amazon.tv.settings.v2/.system.FallbackHome`, a blank screen. That is startup
order, not damage.

## Packages

**A disabled package can still be running.** Persistent priv-apps ignore
`pm disable-user` for the *running process*: the state changes, the process
stays, and `am force-stop` is accepted but does not kill it. `debloat.sh` has a
second pass that denies such packages background work and data.

**`com.amazon.vizzini` must not be disabled.** It is persistent, and while
disabled it draws a non-focusable "Unable to Detect Your Remote" window over
everything that no tap can dismiss — taps go to the focused window underneath.
It is in `never-disable.txt`; block its endpoints instead.

**Protected lists change per build.** A package that disables on Fire OS 7.7.1.7
may be protected on another build, and vice versa. Trust the output, not the
file.

## Updates

**An Amazon update re-enables a lot of this.** The OTA packages are protected, so
the only defence is DNS: `softwareupdates.amazon.com` and the payload CDN must not
resolve. That is what `extra-hosts.txt` does. If the user ever unblocks them, or
factory-resets, re-run the pass.

## What never works

- `adb root` / `adb remount` — `user` build, `ro.secure=1`.
- Writing `/system/etc/hosts` — dm-verity, and the documented brick path.
- `pm hide`, `pm suspend`, `pm clear` on protected packages — missing permissions.
- Disabling the launcher — protected, and it leaves the TV with no home screen.

## Fire OS 6 (Android 7.1)

Found on a Fire TV Stick 4K (mantis, 2018) on Fire OS 6.7.1.1. The scripts read
the API level at connect time and switch to `data/fireos6/` on their own.

**No `cmd package install-existing`.** An Amazon app uninstalled with
`pm uninstall -k --user 0` would only come back with a factory reset, so
`debloat.sh` only disables system apps there. `restore.sh` then needs nothing but
`pm enable`.

**`com.amazon.firebat` is Prime Video**, not an OTA helper: launching it opens
`com.amazon.pyrocore.IgnitionActivity`. It is in `data/fireos6/never-disable.txt`.

**`com.amazon.vizzini` re-enables itself at every boot.** `pm disable-user`
answers `disabled-user`, and after a reboot it is enabled and running again. Leave
it alone either way.

**`ps -A -o NAME` answers `bad pid '-A'`.** Fire OS 6 still has the toolbox `ps`;
`running_names` in `scripts/lib.sh` falls back to its last column.

**No `RUN_ANY_IN_BACKGROUND`.** That app-op arrived in Android 9. `RUN_IN_BACKGROUND
ignore` exists and refuses background service starts (look for `rejectTime` in
`appops get`), but a service that started while the app was open keeps running
until the system needs the memory.

**`always_on_vpn_app` is gone after the reboot.** Fire OS 6 does not keep the
value `settings put` writes, so nothing starts the filter at boot except its own
receiver (`AUTOSTART = true` in its config). Android 7.1 sends no BOOT_COMPLETED
to an app that was force-stopped and not opened since, so `dnsfilter.sh` opens it
once more before the reboot. `verify.sh` checks `tun0` there instead of the setting.

**The app can write an empty list over the pushed one** (84 bytes: a
`# Downloaded by personalDNSFilter at:` header and nothing else). At start it
reloads at once when `previousAutoUpdateURL` differs from the update URL, or when
`FILTERHOSTS.TXT.DLD_CNT` is missing; with no `filterAutoUpdateURL` that reload
downloads nothing. The upstream template ships `previousAutoUpdateURL = <none>`,
which never equals the emptied URL, so this is not Fire OS 6 only. `dnsfilter.sh`
now empties `previousAutoUpdateURL` and writes `DLD_CNT` (whose mtime starts the
reload interval) after the push.

**The release launcher APK needs Android 9.** `launcher/install.sh` notices sdk 25
and builds from source with `MIN_SDK=25` (needs a JDK and `platforms;android-25`).
The build installs and runs, but on 6.7.1.1 it cannot take HOME over, see below.

**Fire OS 6.7 refuses third-party accessibility services.** `settings put secure
enabled_accessibility_services ...` exits 0 and changes nothing; writing the row
through `content insert --uri content://settings/secure` does store it, but the
service is never bound (`dumpsys accessibility` shows `services:{}`), and the log
(`logcat -b all`) has `FireOSFwk:A11yAuthorizer ... "trust"#"false" ... "package"#"tv.dumbtv.home"`.
That is Amazon's allowlist, not a bug to work around, so dumbtv home cannot own
HOME on this build. `install.sh` now checks the binding, says so, and undoes the
hook and `min_futurity`. Older Fire OS 6 builds may still bind it; untested.

**`monkey` exits with `SYS_KEYS has no physical keys`** when the remote has gone
to sleep, so it launches nothing. Pass `--pct-syskeys 0`, or launch apps with
`am start -n`.
