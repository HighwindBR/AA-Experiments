# Beta4 — RC readiness and Round D sign-off

**Status:** development preflight20 private-fixture reconciliation; **not approved for an RC or a GitHub Release**.
Branch: `feature/beta4-getter-probe-preflight`.
Baseline: official `v1.0.0-beta3`; changes in this branch are currently
not merged to `main`.

## Gate status (update with verifiable run IDs)

| Gate | Current evidence / action | Status |
| --- | --- | --- |
| Review all beta4 code against beta3 | 81 commits relative to main at beginning of Round D; review new Advanced/runtime/recovery/publication files | In progress |
| Functional regression strategy | Representative hooks, not individual behavior of 900+ flags | Adopted |
| Unit tests | Preflight18 passed [40113675594](https://github.com/HighwindBR/AA-Experiments/actions/runs/40113675594); preflight19 active-only restore checks passed in [40117982320](https://github.com/HighwindBR/AA-Experiments/actions/runs/40117982320) | Passed for latest code |
| Release lint | Preflight18 passed 40113675594; preflight19 passed 40117982320 | Passed for latest code |
| Debug APK build | Preflight18 passed 40113675594; preflight19 debug APK build passed in 40117982320 | Passed for latest code |
| Private 256 MiB gates | [Run 40142250027](https://github.com/HighwindBR/AA-Experiments/actions/runs/40142250027) reproduced 942 vs expected 919 in installed-scan 256 MiB gate; SHA-256 fixtures OK, no OutOfMemoryError reported. Exact 919+23 fixture reconciliation pending WSL rerun | Failed old baseline; rerun needed |
| Frozen 17.8 corpus gate | Skipped after the earlier private installed-scan gate failed | Not yet run |
| Physical integration | preflight14 accepted slot A and B; preflight13 typed values; preflight16 UI; preflight17 exports; preflight18 upgrade and preserved profile verified on device | Supported |
| Complete recovery bundle export | Created 20,147-byte file on preflight17; preflight18 opened and validated it in the restore preview, showing 103 normal + 1 Advanced + 1 Experimental, 2 active special overrides | Export and safe preview verified |
| Full recovery restore | Exact-build format and unit tests; isolated round-trip not performed. Static audit added active-only current-getter revalidation, retaining inactive and historical baseline metadata | Pending / no live-device destructive test |
| Signing continuity | Release workflow prepared to compare beta4 cert with official beta3 APK | Not yet run |
| RC identity | Final package ID/name/code, approved commit, private delivery | Not prepared |
| Release notes | `docs/RELEASE_NOTES_1.0.0-beta4.md` draft | Maintainer review pending |

## CI validation provenance

- [Actions run 40113184991](https://github.com/HighwindBR/AA-Experiments/actions/runs/40113184991):
  `unit-tests`, `lint-release`, `debug-apk` **all passed** for an
  intermediate Round D commit after the paused-record regression fix.
- The normal backup input-size cap shipped in signed preflight18 and
  was covered by successful public CI run 40113675594.
- The preflight19 static-review fix, selecting **only current-build
  active** Advanced/Experimental records for runtime revalidation,
  passed unit tests and release lint in run 40117982320.
  The debug APK job also completed successfully. This was a regular
  CI debug artifact, not a release-signed RC or an APK installed on
  the user's phone.
- Public GitHub tests do not replace private corpus/memory gates or
  signing-cert continuity, which have not run yet.

- [Actions run 40113675594](https://github.com/HighwindBR/AA-Experiments/actions/runs/40113675594):
  final preflight18 branch commit `44413c1a` passed unit tests,
  release lint and debug APK build.
- Three user-provided screenshots from the signed preflight18 show:
  exact `1.0.0-beta4-preflight18` About version, Android Auto
  `18.0.164204`, LSPosed service connected with configured scope,
  100 modified normal overrides, 3 suspended, 1 Advanced
  active/retained/published, an Experimental BOOLEAN profile published
  and the normal dynamic profile published.
- The preflight17 full recovery file (20,147 bytes) was opened
  without committing it; its schema/checksum and installed-build
  match passed preflight18's decoder, which displayed a confirmation
  preview of **103 normal stored records = 100 active + 3 suspended**,
  1 Advanced, 1 Experimental, 2 active special records. The dangerous
  confirmation remained disabled until text entry and the user was
  instructed to cancel. No stored preferences were replaced.
- This validates safe *preflight parsing*, not persistence after
  full restoration or recovery-profile publication. Keep isolated-only
  destructive recovery testing as an unmet gate.

## Frozen AA 17.8 count reconciliation (preflight20)

- Private WSL run
  [40142250027](https://github.com/HighwindBR/AA-Experiments/actions/runs/40142250027)
  staged the proprietary fixtures, passed SHA-256 verification,
  unit tests and lint, and failed the **installed-scan 256 MiB gate**
  on `expected 919, actual 942`. No OOM was reported; the three
  remaining private gates were skipped.
- A one-time diagnostic run reported **919 baseline keys all retained,
  23 added, 0 removed**. The 23 additions are explained by three
  separately reviewed policy/classification fixes; the exact list
  and root-cause evidence are in
  [17.8 editable delta](BETA4_17_8_EDITABLE_DELTA.md).
- Both installed-scan and compatibility tests now require the
  **complete exact union** of baseline 919 plus 23 reviewed keys,
  as well as the expected types and resolution confidence for each
  new key. This does not broaden the gate to accept future unknown
  additions or disappearances.
- The 12 parked timing knobs become normal-editable under the exact
  whitelist. A safety review found that the normal editor had no
  corresponding elevated-risk confirmation; preflight20 now requires
  typing `PARKED APPLY` before applying a value through that UI.
  The Advanced warning remains unchanged.
- **Not yet verified**: public CI on the final preflight20 commit,
  rerun of four WSL private gates, and signed-device visual smoke.

## Additional static-review findings (preflight19)

- The `FullRecoveryBundle` import accepted inactive/historical records,
  but `ManagerViewModel.restoreCompleteRecoveryJson` still revalidated
  every special record from the same build, including paused ones. This
  could reject a valid recovery file whose **inactive** getter became
  ineligible. Selection and tests now require current-build getter
  revalidation only for records marked **active**. Paused and older-build
  records stay preserved without activating hooks.
- The current Advanced entry point is the **Diagnostics → Advanced
  Overrides** card and Advanced-tagged entries in Experiments, not
  the historical seven-tap unlock under Full Catalog. Current usage
  documentation has been reconciled.
- Prior known-limitations text incorrectly left large-font/zoom QA
  and signed dual-slot smoke untested. It now distinguishes completed
  visual/LSPosed smoke evidence from remaining landscape, TalkBack
  and interruption-injection gaps.
- The WSL/private runner workflow, required labels, fixtures and
  manual non-publishing trigger are documented in
  [Private runner handoff](PRIVATE_RUNNER_CHECKLIST.md).

Code and docs changes above are **not** themselves signed-device
validation of preflight19; public CI and release-fixture gates must
still be checked before RC approval.

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
- Legacy normal backup import now uses the same 2 MiB capped document
  reader as complete recovery, rather than unbounded `readText()`.
  The normal schema and stored values are unchanged.

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

- `app/build.gradle`: preflight20 development version only, not final RC.
- `.github/workflows/private-release.yml`: future beta4 tagging
  guarded; no tag currently created.
- `docs/RELEASE.md`: updated publishing/RC procedure.
- `docs/RELEASE_NOTES_1.0.0-beta4.md`: draft notes.
- `docs/BETA4_RC_READINESS.md`: this sign-off checklist.
- `README.md`, `CHANGELOG.md`: current stage and limitations.

**Not done:** merge, tag, actual RC build, GitHub release or LSPosed
catalog submission.
