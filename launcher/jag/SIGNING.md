# V2 development signing and update continuity

For the separate V3 production package `com.jag.home`, use
[stable/SIGNING.md](stable/SIGNING.md). Its dedicated owner certificate must
differ from the preview certificate documented here.

The retained v1 cloud signing key is available at
`launcher/build/jag-home-dev.keystore` (ignored by Git). It matches the original
cloud-built v1 APK. V2 retains **`com.jag.home.dev`**, the same certificate and
alias, and increases versionCode from **1 to 2** (`0.2-dev`). A v1-to-v2 update
with this certificate is tested on the disposable Android 9 emulator.

Public certificate SHA-256, without separators:

```text
3d61b2c1d0e1ae94dd0dd1fe76c7508ea8c9a8897257be81fbe4d893c0f18939
```

This identifies the **cloud v1 artifact**, not every APK the old CI may have
produced. V1 CI generated ephemeral keys. A downloaded CI APK might have another
certificate; the cloud cannot inspect your installed APK and does not contact
your Fire Stick. Do not assume that installed package has this signer.

## Check your installed APK manually before updating

Use your own Windows Git Bash/ADB connection. Keep pulled files under the ignored
`report/` directory and never publish raw device paths or identifiers:

```bash
mkdir -p report
installed_path=$(adb -s "$ADB_SERIAL" shell pm path com.jag.home.dev | tr -d '\r' | sed -n 's/^package://p' | head -n 1)
test -n "$installed_path" || { echo 'JAG development package not found'; exit 1; }
MSYS_NO_PATHCONV=1 adb -s "$ADB_SERIAL" pull "$installed_path" report/jag-installed.apk
```

Using Android SDK `apksigner` in WSL, inspect that pulled file and the new APK:

Run from the WSL-mounted Windows checkout, or copy `report/jag-installed.apk`
into the WSL checkout first, so these paths refer to the same pulled file.

```bash
"$ANDROID_HOME/build-tools/34.0.0/apksigner" verify --print-certs report/jag-installed.apk
"$ANDROID_HOME/build-tools/34.0.0/apksigner" verify --print-certs launcher/jag/build/jag-home-dev.apk
```

Compare **Signer #1 certificate SHA-256 digest**, not the whole-file APK hash.
Matching certificates permit an update that retains the package's private
settings. If they differ, **stop**: keep both working launchers installed. Do not
uninstall, clear data or try to override Android's signature verification.
Recover the installed APK's signing key if it was retained. If that key is lost,
a separately named v2 development variant would be needed for side-by-side
testing; it cannot automatically inherit the old package's private settings.
That alternate variant is not the signed update produced by this branch.

## Repeatable local builds

The builder no longer generates replacement keys or embeds passwords. Supply the
retained key and credentials from a private local environment, never from a
committed `.env`, script, PR, log or chat. In a WSL shell:

```bash
export JAG_DEV_KEYSTORE='/path/to/your/secure/retained-v1.keystore'
export JAG_KEY_ALIAS=jagdev
read -rsp 'Retained keystore password: ' JAG_STORE_PASSWORD; printf '\n'
export JAG_STORE_PASSWORD
# If the private-key password is the same:
export JAG_KEY_PASSWORD="$JAG_STORE_PASSWORD"
# Otherwise read it separately with read -rsp and export JAG_KEY_PASSWORD.
./launcher/jag/build.sh
unset JAG_STORE_PASSWORD JAG_KEY_PASSWORD
```

The default key path remains the ignored retained cloud key. `apksigner` reads
passwords through environment variables rather than command-line values. The
builder verifies the signature and expected v1 certificate **before** replacing
the output artifact. Missing keys/passwords or another certificate fail without
creating a new signing identity or replacing the last valid signed APK.

Privately back up the retained keystore, alias, password and public certificate
fingerprint using secure storage outside Git. A cloud snapshot should not be the
only copy. No private signing material is attached to the PR. The v1 development
password was historically embedded in its script; v2 removes it from current
build/test scripts, but cannot erase Git history. This is a development identity;
do not use it to sign production. Keystore protection can be changed offline
without changing its key/certificate, after making a secure backup.

## CI and unsigned validation

```bash
./launcher/jag/build.sh --unsigned
```

Produces **`launcher/jag/build/jag-home-dev-unsigned.apk`**, an aligned compilation
artifact that **cannot be installed** until correctly signed. CI uses this mode
and names the artifact `jag-home-v2-unsigned-validation`. It does not generate or
publish a fresh key masquerading as an update for `com.jag.home.dev`.

For future signed automation, use an explicitly configured protected release
job with the retained keystore in a secure secret/file store, masked environment
passwords, manual release approval and certificate verification against the
published fingerprint. Never run that signing job for untrusted PR code or
upload its key as an artifact. The present workflow deliberately has no signing
credentials and produces only the clearly named unsigned artifact.

The eventual stable `com.jag.home` package needs a separate owner-controlled
production certificate and backup policy. See [HOME migration plan](HOME_MIGRATION_V2.md).

## Returning to v1 behavior

Press Home to use the existing dumbtv binding at any time. Updating JAG does not
change that binding. Ordinary release APK downgrades to versionCode 1 may be
refused by Fire OS; do not uninstall/clear JAG to work around it. If the old JAG
UI is needed with settings retained, prepare a reviewed forward update containing
the old UI and a higher versionCode, signed with this same certificate. No such
rollback deployment is performed here.
