---
name: firetv-debloat
description: Debloat an Amazon Fire TV over ADB and lock down its telemetry without root — disable ad/tracking/Alexa/OTA packages, deny background work to the protected ones, install an on-device DNS filter, and replace the home screen with the repo's own zero-permission launcher (dumbtv home). Use when asked to remove Amazon ads, tracking, bloatware or snooping from a Fire TV, or when a user wants a clean launcher on one. Not for Tizen, webOS or Roku, which expose no shell.
---

# Fire TV debloat

Repository: this directory. Scripts are self-contained; read `AGENTS.md` at the
repository root before running anything, and treat its hard rules as binding.

## Preconditions

```sh
adb connect <TV_IP>:5555
adb devices                                  # must end in "device", not "unauthorized"
adb shell getprop ro.product.manufacturer    # "Amazon" = Fire OS
adb shell getprop ro.build.version.sdk       # 28 = Fire OS 7
```

If the device is not Amazon hardware, stop: see `docs/OTHER-TVS.md`.

## Run

```sh
./scripts/preflight.sh      # read-only, always run first
./scripts/all.sh            # everything; asks for confirmation, reboots twice
./scripts/verify.sh         # exit 0 means blocked/allowed hosts and home are right
```

Single steps: `debloat.sh`, `lockdown.sh`, `dnsfilter.sh`, `launcher.sh`.
All accept `--dry-run` and `-y`.

## What to report back

- the summary line from `debloat.sh` (removed / disabled / protected / suspect),
- the table from `verify.sh`,
- the home activity and the number of disabled packages.

Never paste `report/` anywhere: it contains the device IP and package list.

## Guardrails

- Never write to `/system`, never `adb remount`, never `adb root`. dm-verity.
- Never factory-reset the TV.
- Never disable a package in `data/fireos/never-disable.txt` — that list is the
  launcher, settings, TV input, device control and Bluetooth pairing.
- Never `am force-stop dnsfilter.android`: it breaks DNS filtering until reboot.
- Never disable `com.amazon.vizzini`: it leaves an undismissable overlay.
- **Never touch the YouTube app unasked.** It is off by default (`YOUTUBE=0`), the
  interview question defaults to no, and deleting it with `--remove` is the only
  step here that the command line cannot undo. If they want it, disable it; say
  the Appstore is the way back before deleting anything.
- Add new package names only from the device's own `pm list packages` output.

## Recovery

| Symptom | Fix |
|---|---|
| Amazon's home screen is back, or shows for ~5 s on HOME | `./scripts/launcher.sh -y` |
| Nothing is blocked, VPN exists | reboot the TV (never force-stop the filter) |
| "Unable to Detect Your Remote" overlay | `adb shell pm enable --user 0 com.amazon.vizzini`, reboot |
| Start over | `./scripts/restore.sh -y` |
