# Contributing

The scarce thing here is not code. It is **what happened on your TV** — the menu
path, the dialog that blocked you, the package that refused to disable. That is
what saves the next person an evening.

## Two ways to submit

**1. From the device (what an agent should do at the end of a session)**

```sh
./scripts/contribute.sh
```

It reads brand, model, Android version, build and kernel from the TV, then asks
only what a robot cannot know:

- how developer mode and ADB were enabled
- where the IP address was shown
- which dialogs blocked automation, and what got past each one
- anything surprising worth warning the next person about

It writes `catalog/devices/<id>.yaml`, runs the validator, and prints the exact
`git` / `gh` commands to open a pull request. Flags exist for unattended runs:
`--id`, `--platform`, `--status`, `--developer-mode`, `--ip-location`,
`--prompt "name|fix"`, `--quirk "text"`, `--models "A,B"`, `--no-device`.

**2. From a browser, no git**

Open an issue with the "Device report" template. Someone (or an agent) will turn
it into a YAML file. Same questions, same result.

## What never goes in a submission

- IP addresses, MAC addresses, serial numbers
- Wi-Fi network names, account emails, Amazon/Google account ids
- Screenshots with any of the above visible

The script refuses input that looks like an IP or MAC. CI also fails a PR that
contains a private address. If you paste an IP into an issue, delete it.

## What makes a good entry

- The **status** field is honest: `tested` means the full pass ran and
  `verify.sh` exited 0 on that device. Use `partial` or `untested` otherwise.
- Blocking prompts are the most valuable field. "A dialog appeared" is useless;
  `name|exact command or action that dismissed it` is the useful form.
- Quirks should be things that would confuse a newcomer — blank screens during
  boot, a package that stays running after being disabled, a button that lies.
- Do not paste a package list from another device. If the package lists differ
  for your TV, say so in the entry and open a second PR with the lists.

## Review

A PR is merged when:

1. `python3 catalog/validate.py` passes.
2. It contains one device file, no identifiers, and the fields are specific.
3. Claims about what worked match what the entry says about status.

Disagreements are settled by evidence from a device, not by confidence.

## Licence

Code in this repository is MIT (`LICENSE`). The catalog in `catalog/` is
CC BY-SA 4.0 — see `catalog/LICENSE`. By submitting a device entry you agree it
can be published under those terms.
