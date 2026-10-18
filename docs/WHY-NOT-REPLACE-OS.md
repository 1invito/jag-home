# Why Fire OS cannot be replaced

Short version: the hardware only runs Amazon's code, and even if it did not,
nobody has written drivers for the panel.

## The three walls

**1. A signed boot chain.** `ro.boot.verifiedbootstate=1` and `ro.secure=1` on a
`user` build. The SoC checks a signature that only Amazon holds, and the key
material is fused into the chip. There is no `fastboot flashing unlock` on Fire
TV hardware, and no vendor key to request.

**2. dm-verity.** `/proc/config.gz` shows `CONFIG_DM_VERITY=y`. `/system` and
`/vendor` are verified block by block. Writing to them — a hosts file, a deleted
system APK, a remount — fails verification and the device stops booting. This is
the documented brick path, not a rumour.

**3. No open drivers for the panel.** CoreELEC and LibreELEC support Amlogic
*set-top boxes*. A television also needs drivers for its LCD panel, backlight,
HDMI input switching and tuner. Those do not exist in the open, so even a
hypothetical unlock ends at a black screen.

## The root exploit that does exist

CVE-2026-43499 ("GhostLock") is a real kernel bug in the rtmutex/futex
priority-inheritance path, affecting kernels 2.6.39 through 7.x. Public ports
exist for several Fire TV devices, including an ARM32 / Linux 4.9.113 port aimed
at Fire TV Edition televisions.

What it gives you:

- a root shell **until the next reboot** — it patches kernel state, not storage;
- the ability to `pm disable-user` the handful of protected packages;
- nothing else. `pm disable-user` is the only operation that was blocked by
  permission rather than by dm-verity.

What it does not give you:

- a bootloader unlock, so no different OS;
- write access to `/system`, so no hosts file and no removing system APKs;
- persistence, so nothing survives a reboot.

A profile is also per-build: kernel addresses and struct offsets come from that
exact firmware. Between builds, the offsets move and the bug may be patched. So
even if a port exists for your model, treat it as a weekend experiment, not a
recipe. DNS blocking gets the same privacy with none of the risk.

## The conclusion this repo is built on

Keep Amazon's kernel. Remove Amazon's behaviour, at four levels that Amazon does
not control: package state, app-ops, network policy, and DNS. Then put a
different launcher on top. Everything in `scripts/` is that plan.
