# Release process

This document distinguishes *development preflight*, *private candidate*
and *official GitHub Release*. Building or testing a candidate does not
authorize merging, tagging or publishing it.

## Shared release gates

A public beta requires:

1. `:app:testDebugUnitTest` and `:app:lintRelease`.
2. Three **256 MiB** private-fixture regression gates (installed scan,
   compatibility and directed resolve), plus the frozen 17.8 full-corpus gate.
3. A representative physical-device LSPosed / Android Auto smoke test
   of profile publication, hook installation and typed override returns.
   Individual downstream effects of 900+ flags are *not* a release gate.
4. A signed `assembleRelease` APK with v2 signature verification,
   expected package name/versionCode/versionName and the **same signing
   certificate** as the official beta3 package (for beta4).
5. User review of release notes and known limitations, explicit approval
   to merge/tag/publish, and availability of the private fixture runner.
6. The tag's own gates must succeed before a GitHub Release is created.

The existing publication workflow is `.github/workflows/private-release.yml`.
It is **not** triggered by a feature-branch commit. Only an explicit
tag push for `v1.0.0-beta3` or `v1.0.0-beta4` can publish.
A manual `workflow_dispatch` runs checks but never publishes an APK.

The signed tag job uses the repository's existing release keystore
secrets. For beta4, it also downloads the official beta3 APK from
GitHub Releases, verifies its pinned SHA-256, and compares the signing
certificate SHA-256 digest with the new signed APK. Any difference
blocks publication instead of breaking in-place upgrades.

## Beta4 pre-release workflow

### Round D: quality audit (development branch)

Current development branch: `feature/beta4-getter-probe-preflight`.
Do not create a tag or GitHub Release during this round.

- Finish code/API/regression review and unit/lint build.
- Use **Actions → Private release gates → Run workflow**, select
  the beta4 feature branch, enable `run_private_gates`, and ensure
  the self-hosted `aa-private-fixtures` runner is online.
- Review the `SHA256SUMS` fixture checks, three 256 MiB tests
  and frozen corpus result.
- Complete the checklist in [Beta4 RC readiness](BETA4_RC_READINESS.md).
- Do not run a destructive full-restore test on a live profile;
  isolated, disposable storage is required.

### Candidate (not a published Release)

After all gates, prepare an **unsigned? No — signed** candidate
with the existing upgrade-compatible release signing key. To minimize
differences between a tested candidate and the eventual official
APK, the final `1.0.0-beta4` package metadata should be set *before*
candidate testing. The candidate may be identified externally as
`rc1` without using a different final package name/version.
Freeze code, dependency versions and signing settings.

The repository is **public**. GitHub Actions build artifacts are not
a confidentiality boundary. A genuinely private candidate needs a
restricted delivery channel or a private test environment; merely
skipping GitHub Release does not ensure secrecy.

A candidate is for the maintainer's approval only. A fixed defect can
require a new candidate. Do not publish on the basis of the candidate
label alone.

### Official `v1.0.0-beta4` publication

**Only after explicit user authorization:**

1. Confirm the exact approved commit and expected final
   `versionName=1.0.0-beta4` with increasing versionCode.
2. Review [draft beta4 release notes](RELEASE_NOTES_1.0.0-beta4.md).
3. Merge approved changes to `main`, or tag an explicitly approved
   commit following the repository's branch policy.
4. Create/push `v1.0.0-beta4` *only after* gates and maintainer
   authorization. This tag automatically runs tests, private fixtures
   and the signed-release job. Failed gates cannot publish.
5. Check the resulting APK and SHA-256 sidecar, certificate continuity,
   release notes and downloads.
6. Update the LSPosed catalog separately if approved and applicable.

GitHub Releases attach only the signed APK and its SHA-256 checksum;
the root `LICENSE` and `THIRD_PARTY_NOTICES.md` remain accessible in
source and are included inside the APK, not duplicated as assets.
Do not mark the beta release as a GitHub pre-release unless the
maintainer explicitly changes that existing publishing convention.

## Existing beta3 historical process

The tagged beta3 was published from `v1.0.0-beta3` and
`docs/RELEASE_NOTES_1.0.0-beta3.md`. Its tagged release job remains
supported. Development branch `release/1.0.0-beta3` remains an allowed
manual private-fixture gate target for historical reproducibility.

The source signing keystore is never included in the repository.
Lost/changed signing keys break normal upgrade continuity and must be
disclosed. Keep the phone awake during long scans/deep resolve;
screen-off completion remains an accepted limitation.
