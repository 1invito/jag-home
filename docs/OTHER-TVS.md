# Other TVs

The deciding factor is not how new the TV is. It is whether the OS gives you an
ADB shell.

| Platform | Packages | Launcher | On-device DNS filter | Notes |
|---|---|---|---|---|
| Fire OS 6 (2017–2018 devices) | yes, disable only | yes on 6.7 via Keeper (UsageStats + moveTaskToFront; accessibility refused) | yes | own lists in `data/fireos6/`; see `catalog/devices/fire-os-6.yaml` |
| Fire OS 7 (Android 9) | yes | dumbtv home + accessibility | yes | what this repo was built on |
| Fire OS 8 (Android 11) | yes | varies; accessibility sometimes ignored | yes | protected list differs; check `pm disable-user` output |
| Android TV / Google TV | yes | dumbtv home via `set-home-activity` (untested) | yes, or built-in Private DNS | telemetry is Google's, not Amazon's |
| Samsung Tizen | no shell | no | no | network-level blocking only |
| LG webOS | homebrew/dev mode only | no | no | network-level blocking only |
| Roku | no | no | no | network-level blocking only |
| Vidaa / Netrange / vendor Linux | no | no | no | network-level blocking only |

## Fire OS 6 and 8

Run `scripts/preflight.sh`, then `scripts/debloat.sh --dry-run`, then for real.
`PROTECTED` lines tell you what to add to `data/fireos/protected.txt` (Fire OS 8)
or `data/fireos6/protected.txt` (Fire OS 6). Anything
`absent` simply is not on that device. Do not copy lists from the internet —
package names and protections moved between releases, and TV-specific packages
(panel, tuner, backlight) exist only on televisions.

## Android TV

Roughly the same method, different targets:

- **Telemetry is Google's.** Swap the Amazon lists for a Google tracker list, or
  keep the Amazon one only if Amazon apps are installed.
- **Private DNS is usually enough**: `settings put global private_dns_mode hostname`
  plus a filtering DoT resolver, with no app to keep alive. Use personalDNSfilter
  only if you need a custom host list.
- **`cmd package set-home-activity` actually works** on most Android TV builds,
  so the accessibility hack is unnecessary.
- Fire OS settings keys (`amz_limit_ad_tracking` and friends) do nothing.

The generic Android TV bloat list is deliberately not included here. A bad list
on a TV can remove the input switcher or the settings app; build it per device
from `pm list packages` on that device.

## Everything else

For Tizen, webOS and Roku, the only thing that works without vendor cooperation
is the network: block the vendor's telemetry and ad domains at the router
(NextDNS, AdGuard Home, Pi-hole) and stop giving the TV internet if it is only
used as a display. That is outside this repository's scope but it is the honest
answer.

Catalog entries for those platforms are still welcome — the menu paths and
blocking dialogs are the hard-won part — but note that the scripts in `scripts/`
are Fire OS only today: `debloat.sh` refuses to run on non-Amazon hardware rather
than guess a package list.

## Android TV status

`data/androidtv/` exists but is deliberately empty apart from the safety list.
No package list is verified on hardware yet, so `debloat.sh` refuses to run there.

To build one safely on your own TV:

```sh
./scripts/discover.sh        # read-only: lists packages, flags ads/analytics candidates
```

It changes nothing; it writes `report/discover-<platform>-<date>.txt` with a
suggested block for `data/androidtv/packages.txt`, commented out. Verify the
candidates by hand (disable one, then check inputs, settings, app store and
streaming), commit the safe ones, and run `./scripts/contribute.sh`.

## The option that always works

Feed the TV over HDMI from something you control — a Raspberry Pi with
LibreELEC/Kodi, a cheap Amlogic box with CoreELEC, or any Android TV box — and
never connect the TV to a network. The TV becomes a monitor.
No firmware fighting, no OTA risk, nothing to re-apply after an update.
