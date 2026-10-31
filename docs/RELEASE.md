# Release process

This guide describes the release gates and the workflow configuration in this branch.
Implementation, automated checks and physical acceptance are separate evidence.
A successful build does not authorize a merge, tag, signed candidate or publication.

## Current candidate

The Beta6 implementation is maintained on `feature/beta6-reliability` (PR #13).
`maintenance/group-a-stable` (PR #14) adds cleanup and documentation on that baseline.
The application still declares `1.0.0-beta6-preflight1` / versionCode `179`;
it is not an approved stable release.

Use [candidate readiness](BETA6_RC1_READINESS.md) for recorded CI evidence and
outstanding device, private-fixture and signing gates. Historical beta3–beta5
procedures are preserved in [release history](history/RELEASE_HISTORY.md).
The [official releases](https://github.com/HighwindBR/AA-Experiments/releases)
are the authority for published artifacts; beta5 is already published.

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

[Private release gates](../.github/workflows/private-release.yml) listens for
`v*` tags, but its private and publication jobs accept only
`v1.0.0-beta3`, `v1.0.0-beta4` and `v1.0.0-beta5`.
A different tag can start public jobs while private/signing jobs are skipped;
that is not release approval.

Manual private-fixture runs currently accept only:

- `main`
- `release/1.0.0-beta3`
- `feature/beta4-getter-probe-preflight`
- `release/1.0.0-beta4`
- `feature/beta5-media-startup-preflight1`
- `feature/beta5-rc1`
- `release/1.0.0-beta5`

The signed-development-candidate job accepts only the beta4 feature branch
and the three beta5 branches above. Its version and predecessor checks are
also explicit. **Neither Beta6 nor Group A is currently allowlisted for private
gates or signed candidates.** A manual dispatch on either branch can therefore
finish public jobs without running the required private job.

Before stable promotion, a separately reviewed workflow change must reconcile
the approved source ref, final version/code, allowed tag, predecessor APK/hash,
signing comparison, release-note path and any private-delivery script guards.
Do not reuse an old tag or build an older branch to claim the candidate passed.
This documentation update does not enable those paths.

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
