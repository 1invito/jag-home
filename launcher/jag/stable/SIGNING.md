# Owner-controlled stable signing

The stable application ID is **`com.jag.home`** (versionCode 3, `0.3-home`, API 28).
It does not overwrite `com.jag.home.dev` or `tv.dumbtv.home`. Its Java UI namespace
remains `com.jag.home.dev` to reuse the tested v2 source; Android package identity,
UID, private storage, manifest and HOME component are separate.

No production private key is generated in CI or by `build-stable.sh`. The cloud
tests use a disposable validation certificate, not an owner release identity.
Only the **unsigned** stable validation APK is delivered as a CI artifact. Build
an owner-signed APK before installing on your Fire Stick.

## Create the owner key once, locally

Use the JDK 17 / SDK 25 / build-tools 34.0.0 WSL setup in the parent README. Keep
the key outside all Git checkouts. These are owner-operated commands, not cloud
deployment steps:

```bash
set -euo pipefail
umask 077
mkdir -p "$HOME/.local/share/jag-home-signing"
keytool -genkeypair -storetype PKCS12 \
  -keystore "$HOME/.local/share/jag-home-signing/owner-release.p12" \
  -alias jag-home -keyalg RSA -keysize 3072 -validity 10000 \
  -dname 'CN=JAG Home Owner'
```

Let keytool prompt for a strong password. Never put it in Git, command-line
arguments, PRs, screenshots or chat. Privately back up the key, alias, password
and public fingerprint to secure storage. Losing the key prevents normal updates
to that installed stable package. Do not use the old development certificate.

## Build a signed stable APK

From the repository on `feat/jag-home-home-integration`:

```bash
export JAG_STABLE_KEYSTORE="$HOME/.local/share/jag-home-signing/owner-release.p12"
export JAG_STABLE_KEY_ALIAS=jag-home
read -rsp 'Stable keystore password: ' JAG_STABLE_STORE_PASSWORD; printf '\n'
export JAG_STABLE_STORE_PASSWORD
export JAG_STABLE_KEY_PASSWORD="$JAG_STABLE_STORE_PASSWORD"
export JAG_STABLE_CERT_SHA256="$(keytool -exportcert \
  -keystore "$JAG_STABLE_KEYSTORE" -alias "$JAG_STABLE_KEY_ALIAS" \
  -storepass:env JAG_STABLE_STORE_PASSWORD | sha256sum | cut -d' ' -f1)"
./launcher/jag/build-stable.sh
sha256sum launcher/jag/build/jag-home-stable.apk
printf 'Public stable certificate SHA-256: %s\n' "$JAG_STABLE_CERT_SHA256"
unset JAG_STABLE_STORE_PASSWORD JAG_STABLE_KEY_PASSWORD
```

`set -euo pipefail` makes a failed certificate export fail the pipeline. The
exported DER certificate is public; its digest is the expected owner identity.
If the private-key password differs, read/export it separately. `apksigner`
receives passwords via environment names, and verifies both signature and the
supplied expected certificate before replacing the final output.

The signed output is `launcher/jag/build/jag-home-stable.apk`. Retain its public
certificate digest for the migration tool's `--owner-cert`. Keep the private key
on the owner's machine or protected signing service, never in a PR artifact.

Compilation-only validation needs no key:

```bash
./launcher/jag/build-stable.sh --unsigned
```

This produces `build/jag-home-stable-unsigned.apk`, which cannot be installed.
Do not confuse a test-signed cloud APK with a production release. This task does
not have or request the owner's production key, and does not deploy either APK.

## Secure updates

Keep the same package, key and certificate for future stable releases, and
increase versionCode. The owner tool verifies the candidate APK's package,
minimum SDK, actual signature and expected certificate, and checks any existing
stable APK before `install -r`. It will not uninstall a conflicting package or
replace a signing identity. Finish a rollback before updating an active stable
handover; then install and run a new controlled trial. Neither the development
app nor dumbtv must be removed or cleared.

CI continues compiling both variants without production credentials. Any future
signed release job should be protected, owner-approved and isolated from
untrusted PR code. Never upload keystores, passwords or signing environment files.
