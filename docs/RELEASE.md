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
   certificate** as the SHA-256-verified previous official beta
   (beta3 for beta4, beta4 for beta5).
5. User review of release notes and known limitations, explicit approval
   to merge/tag/publish, and availability of the private fixture runner.
6. The tag's own gates must succeed before a GitHub Release is created.

The existing publication workflow is `.github/workflows/private-release.yml`.
It is **not** triggered by a feature-branch commit. Only an explicit
tag push for `v1.0.0-beta3`, `v1.0.0-beta4` or `v1.0.0-beta5`
can publish.
A manual `workflow_dispatch` runs checks but never publishes an APK
in the public source repository. An explicitly opted-in signed-candidate
job may instead attach the signed test APK to an **unpublished draft**
inside a **separate private repository**, never to public Actions artifacts.

The signed tag job uses the repository's existing release keystore
secrets. For beta4/beta5, it downloads the exact preceding official
APK from GitHub Releases, verifies its pinned SHA-256, and compares the
signing certificate SHA-256 digest against the new signed APK. Any
difference blocks publication instead of breaking in-place upgrades.

## Beta5 final — release candidate closure (not yet published)

- RC1 source baseline: `e8286335b8261d4e8e249049b27324b27a989578`.
  The maintainer reported the RC1 functional test on a parked vehicle
  successful on 2026-10-30. Do not infer that each optional route/player,
  failure scenario and second Android Auto build was independently checked.
- Source freeze is on `release/1.0.0-beta5`, branching from that validated
  RC1 commit. Final application identity is `1.0.0-beta5` /
  `versionCode=178`, with the existing package ID and signing key.
  RC1 had versionCode 177; the increased code permits normal upgrades.
- The official APK would be `AAExperiments-1.0.0-beta5.apk` with
  `AAExperiments-1.0.0-beta5.apk.sha256`. No extra LICENSE or notices
  should be attached as Release assets; both remain in source and APK.
- Obtain public unit/lint/debug CI and **all four** private fixture gates
  on the final branch. The private runner and fixed SHA-256 fixture
  manifest remain release dependencies. If needed, use the manual
  `workflow_dispatch` on that exact branch with `run_private_gates`,
  `build_signed_candidate`, and optionally
  `deliver_private_candidate` for an unpublished private draft.
- The signed candidate must have APK Signature Scheme v2 and the same
  certificate as the checksum-verified official beta4. Confirm version
  metadata and installation over the beta4/RC1 package; preserve data.
- Review [beta5 release readiness](BETA5_RELEASE_READINESS.md) and
  [beta5 release notes](RELEASE_NOTES_1.0.0-beta5.md), then obtain explicit
  maintainer approval before merging, tagging or publishing.
- An explicitly approved `v1.0.0-beta5` tag will trigger another
  standalone release gate sequence. Any failure must prevent publication.
  Verify APK, SHA-256 asset, title and release notes afterward.

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
- If away from the self-hosted machine, use
  [Private runner handoff](PRIVATE_RUNNER_CHECKLIST.md) to prepare the
  verification without uploading fixtures or secrets.
- Do not run a destructive full-restore test on a live profile;
  isolated, disposable storage is required.

### Candidate (not a published Release)

After all gates, prepare a **signed** candidate using the existing
upgrade-compatible release signing key. To minimize
differences between a tested candidate and the eventual official
APK, the final `1.0.0-beta4` package metadata should be set *before*
candidate testing. The unpublished signed test file is named
`AAExperiments-1.0.0-beta4-candidate.apk`, not RC1, with final
`versionName=1.0.0-beta4` and `versionCode=169`. The future official
asset is `AAExperiments-1.0.0-beta4.apk`.
Freeze code, dependency versions and signing settings.

The repository is **public**. GitHub Actions build artifacts are not
a confidentiality boundary. A genuinely private candidate needs a
restricted delivery channel or a private test environment; merely
skipping GitHub Release does not ensure secrecy. The manual-only
`deliver_private_candidate` workflow option requires a separate
**private** repository and restricted fine-grained credential, verifies
private visibility and the SHA-256 of the signed APK, and creates only
an unpublished private-repo draft. See
[Signed candidate device test](BETA4_SIGNED_CANDIDATE.md).
The public signed-probe upload workflow was disabled for beta4.

A candidate is for the maintainer's approval only. A fixed defect can
require a new candidate. Do not publish on the basis of the candidate
label alone. Testing an APK from one build does not prove the exact
bytes of a subsequently rebuilt tagged APK are identical. Check
source/ref, package metadata, signing certificate, version and SHA-256
artifact digest; do not claim bit-for-bit reproducibility without an
explicit reproducible-build comparison.

### Official `v1.0.0-beta4` publication

**Only after explicit user authorization:**

1. Confirm the exact approved commit and final
   `versionName=1.0.0-beta4` / `versionCode=169`.
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
