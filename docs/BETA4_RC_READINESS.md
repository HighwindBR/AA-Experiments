# Beta4 — RC readiness and Round D sign-off

**Status:** development preflight18; **not approved for an RC or a GitHub Release**.
Branch: `feature/beta4-getter-probe-preflight`.
Baseline: official `v1.0.0-beta3`; changes in this branch are currently
not merged to `main`.

## Gate status (update with verifiable run IDs)

| Gate | Current evidence / action | Status |
| --- | --- | --- |
| Review all beta4 code against beta3 | 81 commits relative to main at beginning of Round D; review new Advanced/runtime/recovery/publication files | In progress |
| Functional regression strategy | Representative hooks, not individual behavior of 900+ flags | Adopted |
| Unit tests | New checks for paused/historical Advanced/Experimental ownership; rerun complete suite after edits | Pending on final commit |
| Release lint | Run `:app:lintRelease` on final branch | Pending on final commit |
| Debug APK build | Run `:app:assembleDebug` after unit/lint | Pending on final commit |
| Private 256 MiB gates | Manual `private-release.yml` workflow on beta4 branch, with self-hosted fixture runner | Not yet verified |
| Frozen 17.8 corpus gate | Same manual private workflow | Not yet verified |
| Physical integration | preflight14 accepted slot A and B, preflight13 typed values, preflight16 UI, preflight17 exports | Supported |
| Complete recovery bundle export | Created 20,147-byte file on preflight17; contents not inspected | Export confirmed, integrity unknown |
| Full recovery restore | Exact-build format and unit tests; isolated round-trip not performed | Pending / no live-device destructive test |
| Signing continuity | Release workflow prepared to compare beta4 cert with official beta3 APK | Not yet run |
| RC identity | Final package ID/name/code, approved commit, private delivery | Not prepared |
| Release notes | `docs/RELEASE_NOTES_1.0.0-beta4.md` draft | Maintainer review pending |

## Engineering risk review

### Addressed during Round D

- Previous full recovery checks incorrectly rejected *inactive*
  Advanced/Experimental records that coexist with a normal value.
  Changed the rule to reject current-build **active hook collisions**
  while allowing paused/historical baselines; added four unit regressions.
- Beta3-only release workflow was updated to support an explicitly
  authorized `v1.0.0-beta4` tag after unit/lint/private-fixture gates.
  The signed APK must match the expected package/version metadata and
  the official beta3 certificate SHA-256 fingerprint.
- No publishing action is triggered by the development branch changes.

### Open before RC sign-off

1. Confirm all current branch unit tests and lint pass; inspect and
   resolve warnings that indicate actual defects.
2. Run private fixture and frozen full-corpus gates on a candidate ref.
   A CI workflow limited to regular public tests is insufficient.
3. Confirm a signed artifact uses the same certificate as beta3 and
   updates in place over the test installation. Check versionCode
   monotonicity and exact package ID.
4. Exercise a complete recovery restore only with disposable,
   separately isolated preferences, especially paused/active Advanced,
   Experimental original absence, and post-commit profile publication.
   Do **not** reset the configured 100-override live profile.
5. Confirm long analysis cancel/restart, remote service reconnection,
   and profile state after AA process restart without an active drive.
6. Freeze branch and version metadata. A candidate must not silently
   diverge from the commit/tag later released. Retest any last-minute
   change to its executable code.
7. Preserve accepted limitations and obtain explicit maintainer
   approval before tagging/releasing.

## Publication controls and interpretation

- `workflow_dispatch` of private release gates **does not publish**.
- Only a push of the *exact* allowed release tag triggers the
  signed-release job after its dependencies succeed.
- A GitHub Actions artifact in a **public** repository is not
  necessarily private. Use an appropriately restricted delivery
  channel if confidentiality is required.
- Keep `1.0.0-beta4-rc1` as an internal candidate label until the
  maintainer decides final versionName/versionCode. For byte-identical
  promotion, test a candidate built with the *final release metadata*
  and preserve the exact signed APK digest.
- Do not install a locally debug-signed APK over release-signed beta3
  or preflights; the Android package manager will reject mismatched
  certificates. Preserve the original release key in all signed
  development and publication paths.

## Artifacts of Round D

- `app/build.gradle`: development version only, not final RC.
- `.github/workflows/private-release.yml`: future beta4 tagging
  guarded; no tag currently created.
- `docs/RELEASE.md`: updated publishing/RC procedure.
- `docs/RELEASE_NOTES_1.0.0-beta4.md`: draft notes.
- `docs/BETA4_RC_READINESS.md`: this sign-off checklist.
- `README.md`, `CHANGELOG.md`: current stage and limitations.

**Not done:** merge, tag, actual RC build, GitHub release or LSPosed
catalog submission.
