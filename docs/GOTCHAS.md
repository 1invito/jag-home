# Gotchas

Everything here cost real time to work out. Read it before debugging anything.

## The DNS filter

**`am force-stop dnsfilter.android` breaks filtering silently.** The app comes
back (always-on VPN), the VPN network re-registers, and DNS stops going through
it: every lookup goes straight to the router. The traffic log goes quiet, which
is the tell. Recovery is a reboot, not a restart. Never force-stop it.

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

**A 3–6 s flash of Amazon's home screen** when pressing HOME from an app is
normal: Amazon wins the intent, Projectivy reacts to the window change a moment
later. A second HOME press is instant.

**After every boot the launcher takes ~15 s to appear**; until then Android shows
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
