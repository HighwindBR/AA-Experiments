# Development

This document covers cloning or forking AA Experiments, building a debug APK, running the public
test suite, signing local builds and understanding the optional private Android Auto fixtures.

## Prerequisites

Local source builds require:

- JDK 17
- Android SDK platform 36
- Android build-tools 35.0.0
- Gradle 8.9

The repository does **not** include a Gradle Wrapper, so `gradlew` / `gradlew.bat` is not
available. Install Gradle 8.9 separately and make `gradle` available on your `PATH`.

The Android SDK must be discoverable by the Android Gradle Plugin through the normal local Android
SDK configuration. The same packages used by CI can be installed with:

```text
sdkmanager "platforms;android-36" "build-tools;35.0.0"
```

## Clone or fork

Fork the repository on GitHub if you intend to maintain your own changes, then clone your fork and
build from its project root. No proprietary Android Auto archive is required to clone or compile
the project.

## Build a debug APK

To compile only the debug application:

```text
gradle --no-daemon --max-workers=1 :app:assembleDebug
```

The resulting APK is:

```text
app/build/outputs/apk/debug/app-debug.apk
```

For the regular unit suite followed by a debug build:

```text
gradle --no-daemon --max-workers=1 :app:testDebugUnitTest :app:assembleDebug
```

The public GitHub CI also runs `:app:lintRelease`.

## Fork identity and signing

The upstream application currently uses:

```text
applicationId: io.github.highwindbr.aaxp
code namespace: io.github.aaexperiments
LSPosed target scope: com.google.android.projection.gearhead
```

These are different concepts. The `applicationId` is the installed Android package identity; the
code namespace contains the implementation classes; and the LSPosed scope identifies Android Auto
as the package the module targets.

A fork built with the same `applicationId` but a different signing key cannot be installed as an
update over an upstream APK signed with the upstream key. A fork that needs to coexist as a
separate installed application should use its own `applicationId` and maintain its own signing
identity.

Debug builds use Android Gradle Plugin's generated debug key by default. A local debug signing key
can be supplied with:

- `AAXP_DEBUG_STORE_FILE`
- `AAXP_DEBUG_STORE_PASSWORD`
- `AAXP_DEBUG_KEY_ALIAS`
- `AAXP_DEBUG_KEY_PASSWORD`

Release signing uses:

- `AAXP_RELEASE_STORE_FILE`
- `AAXP_RELEASE_STORE_PASSWORD`
- `AAXP_RELEASE_KEY_ALIAS`
- `AAXP_RELEASE_KEY_PASSWORD`

The upstream release key is not part of the public source tree. Fork maintainers are responsible
for their own release key and upgrade-signing continuity. The upstream GitHub release workflow
restores its signing material from repository secrets.

## Android Auto APKM fixtures

**Private Android Auto APKM fixtures are not required for a normal build or normal use of AA
Experiments.**

You do not need the project's frozen APKMs to:

- clone or fork the repository;
- run `:app:assembleDebug`;
- install your own debug build;
- use AA Experiments against Android Auto installed on a device; or
- import an APK/APKM/APKS archive that you provide yourself.

The frozen Android Auto archives under `fixtures/apks/` are regression fixtures used to reproduce
specific historical analyzer and resolver results. They are proprietary and are intentionally not
committed to the public repository. `.gitignore` excludes that directory and Android package
archives.

The exact fixture filenames and expected hashes used by maintainer regression gates are recorded in
`fixtures/SHA256SUMS`. Do not commit proprietary Android Auto APKs or APKMs to a public fork.

## Testing without private fixtures

The regular unit suite can be run without the private APKMs:

```text
gradle --no-daemon --max-workers=1 :app:testDebugUnitTest
```

Tests that explicitly depend on a private APKM use the project's fixture helper and are skipped
when their required archive is unavailable. The remaining public tests continue to run normally.
This makes a clean public clone buildable and testable without redistributing Android Auto.

Test oracles and frozen fixture expectations are regression evidence only. They must never be used
to inject obfuscated fixture names or mappings into production resolution logic.

## Maintainer fixture gates

The private release workflow stages the exact APKM fixtures on a self-hosted runner and verifies
their hashes before fixture-dependent gates run.

The isolated 256 MiB regression workers cover:

- installed-package-style catalog scanning;
- compatibility scanning against a frozen baseline; and
- directed deep resolve.

The full-corpus test is separately opt-in through `-Daa.full.deep=true` and uses fixture-specific
expectations. Those counts describe a frozen regression corpus, not a rule for future Android Auto
versions.

Release signing, private fixture operations and publication are documented separately in
[Release](RELEASE.md).
