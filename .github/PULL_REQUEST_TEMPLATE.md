# Pull request

## What this adds

<!-- one line: the TV, or the fix -->

## Checklist

- [ ] One device file under `catalog/devices/`, or a code/doc change — not both mixed
- [ ] `python3 catalog/validate.py` passes
- [ ] No IP address, MAC address, serial number, Wi-Fi name or account email anywhere in the diff
- [ ] `status` is honest (`tested` only if the full pass ran and `verify.sh` exited 0 on this device)
- [ ] The blocking prompts say what actually dismissed them, not just that one appeared
- [ ] Package lists, if changed, come from this device's `pm list packages`, not from another TV

## Evidence

<!-- for a device entry: what did verify.sh print? for a code change: what did you run? -->
