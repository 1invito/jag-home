# How it works

Five mechanisms, each used because the previous one runs out of authority.
Everything happens over ADB as the `shell` user (uid 2000); nothing needs root.

## 1. Packages: `pm uninstall` then `pm disable-user`

`pm uninstall -k --user 0 <pkg>` removes the app for the user. For preinstalled
apps it is reversible with `cmd package install-existing`; for apps installed
from the store it deletes the APK.

When Amazon blocks that, `pm disable-user --user 0 <pkg>` leaves the code in
`/system` but stops it running: the package reads `enabled=3` (disabled-user).

When Amazon blocks *that* too, the answer is `Cannot disable a protected
package` — see section 3. The list differs between Fire OS builds; that is why
this repo asks you to add whatever it reports as `PROTECTED`.

## 2. Settings: `settings put`

Ad tracking, interest-based ads, usage reporting, wake word, voice, promo
autoplay and animation speed are all `Settings.Secure`/`Global` keys. They are
the same keys the on-screen toggles write. Some of them are rewritten by Amazon
at boot (the wake-word flags are known to reset), which is why the debloat pass
is worth re-running after an update.

## 3. App-ops and network policy: the only lever on protected packages

Shell cannot disable them, but it can revoke what they need:

```sh
appops set <pkg> RUN_IN_BACKGROUND ignore
appops set <pkg> RUN_ANY_IN_BACKGROUND ignore
cmd netpolicy set restrict-background true          # data saver
cmd netpolicy add restrict-background-blacklist <uid>
```

`RUN_ANY_IN_BACKGROUND` makes ActivityManager treat the app as restricted: its
background services are forced into standby. The netpolicy blacklist stops its
background data while data saver is on. It does not kill a running process, and
`am force-stop` is refused for protected packages — so the honest description is
"starved", not "stopped".

## 4. DNS: the part that actually ends the uploads

Fire OS keeps `/system` read-only with dm-verity, so there is no hosts file to
edit. Instead personalDNSfilter runs **on the TV** as a local VPN: every lookup
goes through it, and listed names are answered with `0.0.0.0`.

Three details make it hold:

- `routeIPs = <gateway>` puts the router's own resolver inside the tunnel, so an
  app that addresses the router directly still ends up in the filter.
- `detectDNS = false` plus an explicit `fallbackDNS` avoids the filter dropping
  out of the path when the TV changes network (Ethernet to Wi-Fi).
- `always_on_vpn_app` plus a Doze whitelist brings it back after a reboot.

The list is composed on the machine running the script — the HaGeZi Amazon
tracker feed plus `data/blocklists/extra-hosts.txt`, which carries the OTA
endpoints read out of `DeviceSoftwareOTA.apk` — and pushed to the TV as a single
file. `filterAutoUpdateURL` is deliberately disabled, so the TV never downloads a
blocklist and cannot end up with a half-written one. If the user keeps Prime
Video, the two video CDN lines are filtered out of the list before it is pushed.
Refresh the list by re-running `scripts/dnsfilter.sh`.

## 5. Launcher: accessibility, because the setting is ignored

On Android TV, `cmd package set-home-activity` works. On Fire OS it prints
`Success` and does nothing: `resolve-activity -c android.intent.category.HOME`
still answers `com.amazon.tv.launcher`, and both the launcher and
`com.amazon.firehomestarter` are protected packages.

So dumbtv home (`launcher/`, this repo's own launcher) takes over with a small
accessibility service, `Guard`, that sees only window changes — it reads no
screen content. Measured on a Fire OS 7.7 TV:

- **The HOME key cannot be intercepted.** Fire OS handles it before any
  accessibility key filter runs, so `Guard` waits for Amazon's home window instead.
- **Android holds back app launches for 5 s after a HOME press**
  (`APP_SWITCH_DELAY_TIME`). That is why third-party launchers flash Amazon's home
  for 3–6 s. `Guard` launches through an AlarmManager `PendingIntent`, which
  `system_server` sends and is exempt from the delay. Alarms normally fire no
  sooner than 5 s ahead, so `launcher.sh` sets
  `settings global alarm_manager_constants min_futurity=0`; `restore.sh` and
  `launcher/install.sh --undo` remove it.
- **Amazon's home is covered before it draws** by an overlay window `Guard` keeps
  ready without a surface, and removed once ours is on screen. HOME from an app:
  about 0.3 s of black, then the home screen.
- If the accessibility setting is cleared, HOME goes back to Amazon.
  `scripts/launcher.sh` puts it back.
- Holding HOME still opens Fire OS's own quick menu, and the launcher's Options
  screen can open Amazon's home on purpose.

## What is not used, and why

| Not used | Because |
|---|---|
| `pm hide` | shell lacks `MANAGE_USERS` |
| `pm suspend` | shell lacks `SUSPEND_APPS` |
| `pm clear` | refused for protected packages |
| `dpm set-device-owner` | refuses devices that already have an account |
| hosts file | dm-verity blocks writes to `/system` |
| root | no supported unlock; a temp-root exploit exists but cannot touch `/system` either |
