# Release

Beta publication requires the regular suite, release lint, three isolated 256 MiB gates, the frozen
full-corpus gate, a physical smoke test and a tag. GitHub restores the configured experimental
release key, builds `assembleRelease`, verifies APK Signature Scheme v2, publishes the APK and a
SHA-256 sidecar.

For `1.0.0-beta3`, the automated release gates and physical smoke test must pass before the tag is
pushed. Screen-off during an active scan or deep analysis remains an explicitly accepted,
non-blocking known limitation: keep the device awake until analysis completes. See
`docs/KNOWN_LIMITATIONS.md`.

Before merging and tagging beta3, run **Actions → Private release gates → Run workflow** with
branch `release/1.0.0-beta3` and the `run_private_gates` input enabled. This explicitly
runs the fixture-dependent validation against the beta3 candidate, without starting
`signed-release`. Review the three 256 MiB tests and the frozen 17.8 full-corpus gate;
all must pass. This manual preflight does **not** publish an APK.

Before pushing `v1.0.0-beta3`, the self-hosted runner carrying the
`aa-private-fixtures` label must be online and the four release-signing secrets must be configured:
`RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` and
`RELEASE_KEY_PASSWORD`.

Pushing the exact `v1.0.0-beta3` tag reruns the release unit tests, release lint, all private
fixture gates and the frozen 17.8 full-corpus gate. Only after those jobs succeed does the
`signed-release` job build, verify and publish the signed APK and checksum using
`docs/RELEASE_NOTES_1.0.0-beta3.md`.

Public releases use the repository's configured GitHub signing secret; loss or replacement of that
key breaks normal upgrade continuity and must be disclosed.


GitHub Releases attach only the signed APK and its SHA-256 checksum. The project
[`LICENSE`](../LICENSE) and [`THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md) remain
available in the source repository and are not uploaded as duplicate release assets.
Both the GPL license text and third-party notices are also bundled inside the APK
(`assets/LICENSE.txt` and `assets/THIRD_PARTY_NOTICES.txt`).
