# dumbtv

Turn an Amazon Fire TV into something that behaves: no ads, no watch tracking, no
OTA, a clean launcher — all applied over ADB from another machine, no root.

Tested end to end on a Fire TV Edition television running Fire OS 7.7.1.7
(Android 9). Fire OS 7/8 streaming devices behave the same way; Android TV keeps
most of it but needs a different package list — see `docs/OTHER-TVS.md`.

**What it will not do:** replace Fire OS, unlock the bootloader, or survive a
factory reset. None of that is possible on these devices, and pretending
otherwise is how people brick their TVs. See `docs/WHY-NOT-REPLACE-OS.md`.

## It asks what you want to keep

Nothing is decided for you:

```sh
./scripts/configure.sh
```

It goes through the apps this repo would remove (`data/fireos/user-apps.txt`,
with readable names from `data/apps.txt`) and asks, one by one, whether to remove
each. Answer "no" and that app is never touched. It also asks whether you watch
Prime Video — Prime stays installed either way, but if you say yes its video CDN
is left out of the blocklist so playback keeps working — and whether you want the
launcher replaced and the DNS filter installed.

The answers go into `dumbtv.conf` (git-ignored). Re-run it any time to change
your mind; the scripts read that file, so nothing has to be edited by hand.

Ads, telemetry, experiments and Alexa are removed regardless. This step is only
about apps a person might actually watch.

## What it changes

| Layer | Result |
|---|---|
| Packages | ~65 disabled: ACR (screen fingerprinting), ad-ID service, sponsored rows, watch-time analytics, A/B experiment engines, Alexa/mic app, promo screensaver, OTA helpers. Ad-funded streaming apps uninstalled. YouTube and VLC kept. |
| Protected packages | The handful Amazon refuses to disable get their background execution and background data removed instead. |
| Settings | Ad tracking limited, interest-based ads off, app/device usage reporting off, wake word and voice off, promo autoplay off. |
| DNS | personalDNSfilter runs on the TV: Amazon telemetry, Alexa endpoints and the OTA servers answer `127.0.0.1`. YouTube and the app store still resolve. |
| Launcher | Projectivy replaces Amazon's home screen — no promo tiles, no sponsored rows. |

Every step is reversible with `scripts/restore.sh`.

## Requirements

- A machine with `adb` (macOS, Linux, Windows via WSL/Git Bash).
- The TV on the same network, with **Developer Options → ADB debugging** on
  (Settings → My Fire TV → About → click the device name 7×, then back to
  My Fire TV → Developer Options).
- ~10 minutes. The scripts reboot the TV twice.

## Quickstart

```sh
adb connect <tv-ip>:5555
adb devices                      # must show "device", not "unauthorized"

./scripts/preflight.sh           # read-only: device facts + what would change
./scripts/configure.sh           # asks what to keep, writes dumbtv.conf
./scripts/all.sh                 # the full pass, asks before touching anything
./scripts/verify.sh              # proof: blocked vs still-working domains
```

If anything looks wrong later, `scripts/restore.sh` puts it back.

## Run one piece at a time

| Script | Does | Reboot needed |
|---|---|---|
| `preflight.sh` | Prints device facts and the plan. Changes nothing. | no |
| `configure.sh` | Asks what to keep and writes `dumbtv.conf`. | no |
| `debloat.sh` | Uninstalls/disables packages, applies privacy settings. | yes |
| `lockdown.sh` | App-ops + data-saver rules for protected packages. | no |
| `dnsfilter.sh` | Installs and configures the on-device DNS filter. | yes |
| `launcher.sh` | Installs Projectivy and takes over the HOME button. | no |
| `verify.sh` | Checks what is blocked and what still works. | no |
| `restore.sh` | Undoes packages, app-ops, network rules and settings. | yes |
| `all.sh` | Runs the above in order. | yes |

Every script takes `--dry-run` to print its commands without running them, and
`-y` to skip confirmation.

## Safety rules the scripts enforce

- A hard-coded **never-disable** list (launcher, settings, TV input/routing,
  device control, Bluetooth pairing, system UI). Disabling those is how a TV ends
  up with a black screen and no working remote.
- Nothing writes to `/system`. No `adb remount`, no `pm uninstall` without
  `--user 0`, no hosts-file editing — Fire OS verifies `/system` with dm-verity
  and modifying it is the documented brick path.
- The DNS filter only runs if the app's VPN consent was pre-granted, and it is
  verified after a reboot before the script reports success.

## Documentation

- `docs/HOW-IT-WORKS.md` — the five mechanisms and why each is needed
- `docs/GOTCHAS.md` — everything that went wrong while building this
- `docs/OTHER-TVS.md` — Fire OS 6/7/8, Android TV, Tizen, webOS, Roku
- `docs/WHY-NOT-REPLACE-OS.md` — the bootloader/verity wall, and the root exploit
- `docs/FAQ.md` — factory reset, updates, Prime Video, "is this illegal" (it isn't)

## For AI agents

`AGENTS.md` is the machine-readable contract: preconditions, exact ordering,
guardrails, verification and recovery. Point an agent at this repository and it
can run the whole thing without improvising.

There is also a skill file at `skills/firetv-debloat/SKILL.md` for agents that
load skills.

## Credits and licences

Scripts and docs: MIT (`LICENSE`).

Installed but not bundled: [projectivy launcher](https://github.com/spocky/miproja1)
and [personalDNSfilter](https://github.com/IngoZenz/personaldnsfilter) (GPL). Both
are downloaded from their upstream releases at run time. The Amazon tracker list
is fetched from [HaGeZi DNS blocklists](https://github.com/hagezi/dns-blocklists)
at run time; nothing is vendored, so the blocklist stays current.

Nothing in this repository is Amazon code.
