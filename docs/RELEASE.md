# Release process

This guide describes the release gates and the workflow configuration in this branch.
Implementation, automated checks and physical acceptance are separate evidence.
A successful build does not authorize a merge, tag, signed candidate or publication.

## Current candidate

The stable candidate is `1.0.0` / versionCode `180` on
`maintenance/group-a-stable` (PR #14), built on the Beta6 reliability work.
The published predecessor is `1.0.0-beta5` / `178`; the tested preflight used `179`.
See [stable readiness](STABLE_1.0.0_READINESS.md) for exact evidence and remaining gates.
The [official releases](https://github.com/HighwindBR/AA-Experiments/releases)
remain the authority for publication status.

## Required gates for a public candidate

1. Run the public JVM suite, release lint, debug APK build and Android test APK
   build against the exact candidate. Compilation of the test APK is not test execution.
2. Execute the isolated SQLite instrumented tests and record the Android API,
   source commit and result. Also test a signed upgrade over the previous release
   with representative existing data; the synthetic v7 migration test alone does
   not prove a complete user-data upgrade.
3. Run all three private 256 MiB regressions (installed scan, compatibility and
   directed deep resolve) plus the frozen 17.8 full-corpus gate. Require exact
   fixture hashes and actual, non-skipped test execution.
4. Complete representative physical LSPosed/Android Auto tests: profile
   publication, hook installation, typed override returns, recovery and lifecycle.
   Check Media Startup on a parked head unit, including selected player, consent,
   route availability and one attempt per connection. Record which combinations
   were actually tested. Testing all downstream effects of every flag is not required.
5. Build a signed release APK, verify APK Signature Scheme v2, package identity,
   versionName/versionCode and upgrade-compatible certificate. Verify the previous
   official APK's SHA-256 before using it as the signing baseline.
6. Review release notes and known limitations; obtain explicit maintainer approval
   for the exact commit, version, signing baseline and publication.
7. Require the approved tag's own gates to pass before publication. Verify the
   published APK, checksum, metadata and notes afterward.

A public green run, a successful method invocation, or a saved preference does
not substitute for the other gates. Record artifact SHA-256 values for device
testing; testing one build does not prove a subsequently rebuilt APK has identical bytes.

## What the workflows currently allow

[Private release gates](../.github/workflows/private-release.yml) accepts the exact
stable tag `v1.0.0` and retains the historical beta3/beta4/beta5 tag rules.
The trusted `maintenance/group-a-stable` branch is explicitly allowed for manual
private gates and signed candidate builds (`1.0.0`, code `180`). Historical
allowlisted branches remain in the workflow; arbitrary branches and PR events
cannot access the private/signing jobs.

Both the stable candidate and tagged release must match the certificate of the
official beta5 APK, whose SHA-256 is pinned to
`bd03c9dd388b148bdda2a933949e25056eaf504579197157af8b9bda3ec135e8`.
The exact tag checks code `180`, package identity, v2 signing and
`docs/RELEASE_NOTES_1.0.0.md`. Manual candidate delivery stays private and is
guarded separately from public tag-triggered publication.

See [Private runner handoff](PRIVATE_RUNNER_CHECKLIST.md) for trusted-runner
requirements. Never run untrusted pull-request code on the private runner.

## Candidate delivery and publication

A manual private-release dispatch never publishes an APK in the public source
repository. On an allowed branch, a fully gated, explicitly requested signed
candidate can remain on the trusted runner or be delivered to an unpublished
draft in a separate private repository. Public Actions artifacts are not a
private delivery channel.

The separate [debug APK workflow](../.github/workflows/debug-apk.yml) can upload
a disposable-key debug artifact on permitted manual runs; it does not provide
an upgrade-compatible release candidate. The standard verification workflow
builds APKs without uploading them, except for SQLite diagnostic logs.

GitHub Release assets are the signed APK and its SHA-256 checksum. License and
third-party notices remain in source and inside the APK. Preserve the release
key; changing it breaks ordinary in-place upgrades.

The LSPosed catalog has a separate publication procedure described in
[Catalog maintenance](LSPOSED_CATALOG.md). Updating source documentation does
not update that catalog or authorize a release.
