# Beta4 — RC readiness and Round D sign-off

**Status:** signed preflight22 and UI acceptance passed; maintainer accepted override preservation and waived further screenshots/exports. Final beta4 169 candidate pending signed gates; no publication.
Branch: `feature/beta4-getter-probe-preflight`.
Baseline: official `v1.0.0-beta3`; changes in this branch are currently
not merged to `main`.

## Final beta4 freeze — 2026-10-27

- **Approved:** preservation of configured overrides after the signed
  preflight22 update. The maintainer expressly dispensed with additional
  Status screenshots and normal override re-export. Earlier preflight21
  counts (100 modified normal, 3 suspended, 1 Advanced, 1 Experimental)
  remain historical evidence and are not a new exact recount.
- Signed preflight22 passed [public CI 40325455245](https://github.com/HighwindBR/AA-Experiments/actions/runs/40325455245) and [private gates 40431677743](https://github.com/HighwindBR/AA-Experiments/actions/runs/40431677743):
  all three real 256 MiB fixture tests, the non-skipped frozen 17.8
  corpus, v2 signing and the official beta3 certificate comparison.
  Subsequent delivery tooling and documentation passed
  [public CI 40524090180](https://github.com/HighwindBR/AA-Experiments/actions/runs/40524090180).
- Preflight22 screenshots verify the compact scan progress, consolidated
  Backup & recovery UI, Advanced BOOLEAN True selection and typed
  elevated-risk confirmation. Actual normal, Advanced and Experimental
  getter interceptions/returns were demonstrated separately on signed
  preflight21. Do not conflate UI evidence with fresh runtime events.
- Normal and complete restore functionality were reported successful
  on 2026-10-26; disposable-storage isolation and byte-for-byte
  post-restore equivalence were not independently verified.
- **Final metadata:** `io.github.highwindbr.aaxp`,
  `versionName=1.0.0-beta4`, `versionCode=169`; unsigned/not-yet
  produced test artifact label
  `AAExperiments-1.0.0-beta4-candidate.apk` (no RC1).
- **Pending:** new public CI, 256 MiB private fixture tests and
  non-skipped corpus gate, signed candidate package/cert validation
  on this final commit and tagged release gates. Documentation and
  candidate preparation are authorized; merge, tag and publication
  still require **separate explicit maintainer authorization**.

## Signed preflight22 screenshot acceptance — Android 17 / S24+ (2026-10-27)

User-provided screenshots show the release-signed `1.0.0-beta4-preflight22`
running on the target phone (Settings/About). They establish the
following UI and state transitions, **not** new getter-return evidence:

- **Analysis dialog**: DEX pass presents `classes.dex` and `1 of 5`
  in the same row; known-progress catalog saves show bounded continuous
  bars and `3501 of 50675` → `30751 of 50675` without reserved blank
  detail slots. Other phases (Resolving identifiers, Classifying
  experiments, Loading experiments) correctly display indeterminate
  Material3 animation. During the analysis, Status says `Preparing
  the installed Android Auto catalog` and `Preparing catalog and
  runtime profile`, rather than transient `Not found`.
- **Backup & recovery**: Settings contains one consolidated entry;
  complete export/import are primary actions; legacy normal-only and
  Danger zone sections initially collapse, and both expand to the
  expected individual actions. No destructive reset was evidenced by
  these screenshots.
- **Advanced BOOLEAN**: Screenshots of
  `VideoTimingControllerFeature__enable_video_timing_controller_parked_exp`
  show that explicit True selection enables `Review Advanced value`,
  and that the initial **Elevated-risk override — review** dialog
  displays getter key, BOOLEAN type, compiled default true, proposed
  true, and elevated risk warning. The separate **Final confirmation**
  screen displays `Type PARKED APPLY` and keeps the apply action
  disabled while the field is empty. No evidence of typing the
  expression, or applying that particular screenshot's value, is
  needed to accept the non-destructive protection UI.
- Other Advanced editor screenshots from the same device show
  `Experimental value: true · ACTIVE`, then `PAUSED` with
  `Resume`, then `Advanced baseline restored, including previous
  absence` with an unselected BOOLEAN and disabled review action.
  This supports save/pause/restore UI state, but alone does not prove
  that a runtime getter returned a changed value.
- The **Advanced information** collapsed section now states
  `Previous catalog classification · policy since updated`,
  distinguishing historical stored safety reasons from current
  Advanced eligibility.
- **Resolved by maintainer approval:** no additional Status screenshot
  or override export is required; final signed candidate verification
  and publication authorization remain separate. A tall first-review
  dialog wraps its long key and buttons; cosmetic only, not currently
  a release blocker.

## Preflight22 acceptance plan

- New development identity: `1.0.0-beta4-preflight22` /
  `versionCode=168`. Original applicationId and signing certificate
  are unchanged.
- Bundled UI corrections: dedicated **Backup & recovery** settings,
  Advanced BOOLEAN value confirmation fix, historical parked-policy
  labeling, and compact scan-progress dialog. During scanning, Status
  shows analysis in progress instead of transient **Not found**.
- Dialog no longer pre-allocates 64/20/42 dp for missing detail,
  count and cancel button. The determinate progress bar is continuous
  and represents **the current stage only**.
- **Completed for functional preflight22 source**: public CI, all four
  real WSL fixture gates, signed-certificate verification and phone UI
  acceptance. The final beta4/169 commit requires fresh checks.
- The manual WSL signed-candidate job now depends on all private
  gates; dispatch with `run_private_gates=true` **and**
  `build_signed_candidate=true`. No tag, merge or GitHub Release.

## Opt-in private device delivery (2026-10-26)

- Added `deliver_private_candidate` in the signing workflow for
  download on Android. User must create a **separate private GitHub
  repository** and configure a repository-restricted Contents write
  token as a secret and destination as an Actions variable.
- Private visibility, candidate SHA-256/name, source ref and workflow
  inputs are checked before creating any draft. Downloads are attached
  only to unpublished drafts in the private repository. **No artifact
  is uploaded to the public AA-Experiments Actions run.**
- This change is to build/delivery tooling only. Source functional
  preflight22 tests and signing from run 40431677743 remain valid for
  that SHA, and the added delivery script subsequently passed offline tests
  and public CI 40524090180. No private download has been evidenced.
- Until configured and executed, the existing verified preflight22 APK
  remains on WSL as before. No beta4 tag or public publication.

## Gate status (update with verifiable run IDs)

| Gate | Current evidence / action | Status |
| --- | --- | --- |
| Review all beta4 code against beta3 | 81 commits relative to main at beginning of Round D; review new Advanced/runtime/recovery/publication files | In progress |
| Functional regression strategy | Representative hooks, not individual behavior of 900+ flags | Adopted |
| Unit tests | Branch commit `86eadc4` passed [public CI 40186619085](https://github.com/HighwindBR/AA-Experiments/actions/runs/40186619085) and [private release suite 40189976260](https://github.com/HighwindBR/AA-Experiments/actions/runs/40189976260) | **Passed** |
| Release lint | Commit `86eadc4` passed public CI 40186619085 and private run 40189976260 | **Passed** |
| Debug APK build | Commit `86eadc4` passed public CI 40186619085 | **Passed** |
| Private 256 MiB gates | [Run 40189976260](https://github.com/HighwindBR/AA-Experiments/actions/runs/40189976260), commit `86eadc4`: fixture hashes verified and installed-scan, compatibility, directed-resolve completed under 256 MiB | **Passed (3/3)** |
| Frozen 17.8 corpus gate | [Run 40189976260](https://github.com/HighwindBR/AA-Experiments/actions/runs/40189976260) enforced JUnit XML: `tests=1, failures=0, errors=0, skipped=0`, against frozen 919-key fixture with 256 MiB | **Passed / executed** |
| Physical integration | Signed preflight21 installed in-place; screenshots show 100 modified + 3 suspended normal entries, Advanced and Experimental state retained. LSPosed logs confirm normal/Advanced/Experimental getters and typed returns | **Passed representative smoke** |
| Complete recovery bundle export | Created 20,147-byte file on preflight17; preflight18 opened and validated it in the restore preview, showing 103 normal + 1 Advanced + 1 Experimental, 2 active special overrides | Export and safe preview verified |
| Override import and full recovery restore | Maintainer reports both normal override restoration and complete recovery restoration **worked** on 2026-10-26. Test environment and exact post-restore counts were not specified; separate disposable-storage isolation is not independently evidenced | **Manual functional pass reported; isolation unverified** |
| Signing continuity | [Signed private preflight21 job 40216832745](https://github.com/HighwindBR/AA-Experiments/actions/runs/40216832745): v2 signature and pinned official beta3 artifact verified, signing certificate SHA-256 matched; signed APK installed in-place on device. Final beta4 candidate still pending | **Passed for preflight21; final pending** |
| Final candidate identity | `1.0.0-beta4` / 169 / `io.github.highwindbr.aaxp` and external candidate filename | Metadata prepared; final signing pending |
| Release notes | Consolidated beta4 final candidate notes | Prepared; publication pending |

## Manual restore acceptance (2026-10-26)

- The maintainer explicitly reported **successful restoration of normal
  overrides and successful full recovery restore**. Record these as
  functional manual passes rather than a merely opened preview.
- The report did not specify whether the exercise used isolated
  disposable storage, nor provide the exact post-restore 100/3/1/1
  record breakdown. Thus, do not claim independent proof of isolated
  destructive rollback or exact byte-for-byte restored state.
- No new APK build is required solely for this confirmation.
  Remaining device-facing changes staged on the development branch
  are **Backup & recovery** settings decluttering and the Advanced
  BOOLEAN review fix. Verify both in the next signed candidate,
  then freeze final package metadata, source and signing.

## Physical preflight21 screenshot acceptance (2026-10-25)

- Installed version screenshot reports `1.0.0-beta4-preflight21`.
  Status screenshot confirms Android Auto
  `18.0.164204-release.daily`, LSPosed connected (API 102),
  scope configured, 870 editable, **100 modified** normal flags
  and **3 suspended**. An Advanced override remains 1 active,
  1 retained and 1 published, and an Experimental BOOLEAN profile
  is published. Runtime is *ready, waiting for Android Auto*, not yet
  proven to have intercepted getter calls in a new AA session.
- Full recovery bundle preview displays **103 normal + 1 Advanced +
  1 Experimental**, with two active special records; the destructive
  `RESTORE ALL` confirmation remains disabled until typed. User
  submitted the preview only, not a destructive restore.
- The reviewed parked-mode BOOLEAN opened in the new inline
  Advanced details editor and showed the elevated warning.
  **Bug reproduced**: despite choosing True or False, the button
  `Review Advanced value` remains disabled. The cause was
  a null-coalescing expression misinterpreting valid input
  (`OverrideInputFeedback.issue == null`) as missing selection.
  Fixed for the *next* build with unit regression coverage.
- A previous catalog's `Read-only safety policy: ... parked` reason
  appears below a currently approved Advanced eligibility.
  The next build distinguishes stale stored reason from current
  SafetyPolicy, without silently changing persisted catalog data.
- Subsequently verified from 2026-10-25 LSPosed export: runtime
  normal/Advanced/Experimental getter interception and typed returns.
  On 2026-10-26 the maintainer additionally reported successful
  normal override and complete recovery restoration. Isolation of
  the write/restore environment was not specified. Remaining next-
  candidate smoke includes updated Advanced review and backup UI,
  followed by final release identity/code freeze.

## LSPosed runtime interception evidence (preflight21, 2026-10-25)

- User-provided export `LSPosed_20261025_223623.zip` reports LSPosed
  release 2.2.1 (7912) and contains current `modules_` logs spanning
  the signed preflight21 device installation. Count **modules only**;
  corresponding `verbose_` streams mirror those events.
- From **2026-10-25 14:50 to 22:06:48** (device-local log time):
  **100 `PROFILE_ACCEPTED`** events, consistently
  `mappings=100, probes=0, experimental=1, advanced=1`, and
  100 distinct `HOOK_INSTALLED` normal keys per accepted profile.
  They are process initializations, **not** 100 user sessions.
- **263 `OVERRIDE_RETURNED` callbacks across 72 distinct normal keys**,
  observed from **17:00:54 to 20:30:34** in AA `:car` and
  `:projection` processes. These establish active runtime returns,
  not just profile publication.
- In the AA `:projection` process,
  `PhoneThemeFeature__enable_dynamic_app_icon_shape_by_manufacturer_kill_switch`
  emitted **3 `EXPERIMENT_GETTER_INVOKED` +
  3 `EXPERIMENT_OVERRIDE_RETURNED(false)`** pairs at
  **17:25:59, 18:00:32 and 20:14:47**. Likewise
  `AceFeature__mode` emitted **3 `ADVANCED_GETTER_INVOKED` +
  3 `ADVANCED_OVERRIDE_RETURNED(LONG:0)`** pairs at
  **17:26:05, 18:00:35 and 20:14:51**.
- **Accepted**: representative normal, Experimental and Advanced
  getter interception and typed runtime return on signed preflight21.
  This does not establish behavioral/visual effect of every override.
- **Clarified with the maintainer (2026-10-26):** Android Auto
  was **not used after 21:11**. The 21:11:26–22:06:48 rotated
  `modules_` stream contains 38 profile acceptances/hook setups
  without getter-return events or a `:projection` process, which is
  consistent with no projection session during that interval.
  **No follow-up investigation is required for that absence.**
- No AA Experiments errors were found in the inspected current
  `modules_` logs. Separately observed ClassNotFound exceptions
  belong to an unrelated camera LSPosed module, not AA Experiments.
- The edited backup UI and fixed Advanced BOOLEAN review button are
  **not present in signed preflight21** and require device smoke on
  the next approved signed candidate. On 2026-10-26 the maintainer
  reported that normal and complete recovery restoration both worked;
  whether that occurred with disposable isolated preferences is not
  yet evidenced.

## Pending device acceptance and staged UI cleanup

- The signed `1.0.0-beta4-preflight21` APK from private job
  [40216832745](https://github.com/HighwindBR/AA-Experiments/actions/runs/40216832745)
  used source `311e5dd`, verified APK Signature Scheme v2,
  verified the official beta3 APK's pinned SHA-256, matched the
  signing certificate, and saved the private APK locally on WSL.
  The maintainer's screenshot confirms the About screen reports
  `1.0.0-beta4-preflight21`.
- Still require **device smoke evidence**: Status counts and LSPosed
  connection, preservation of normal and Advanced/Experimental records,
  Advanced candidate navigation, elevated `PARKED APPLY` editor dialog
  (cancel rather than changing timing values), and safe recovery preview.
  A non-destructive preview must not be mistaken for a full write/restore
  round trip.
- User-requested settings declutter is staged **for the next signed
  candidate only**: one `Backup & recovery` entry at Settings root;
  complete backup/export prominently available; normal-only legacy
  actions and destructive reset each in separate expandable groups.
  The existing data semantics and confirmations are unchanged.
- **Do not identify the already-installed preflight21 APK with this
  new UI commit.** Public CI on UI changes and any required private
  gates must be reviewed for the final frozen executable source before
  candidate approval. Do not build another signed development APK
  solely for settings rearrangement.

## Verified automated gate sign-off (2026-10-25)

- **Accepted tested source:** `86eadc4626250dbf37cd38f61f04745284b8b2a4`
  on `feature/beta4-getter-probe-preflight`.
- [Public run 40186619085](https://github.com/HighwindBR/AA-Experiments/actions/runs/40186619085):
  unit tests, `lintRelease` and debug APK build succeeded.
- [Private run 40189976260](https://github.com/HighwindBR/AA-Experiments/actions/runs/40189976260):
  SHA-256 fixture verification, separate 256 MiB installed-scan,
  compatibility and directed-resolve tests, plus activated
  `FullDeepResolveDistributionTest` all succeeded.
- The fourth gate's JUnit report was validated explicitly:
  `{'tests': 1, 'failures': 0, 'errors': 0, 'skipped': 0}`.
  Its assertions require completing all 919 frozen baseline keys
  and no blocking UNKNOWN callsites. No silent-skip acceptance remains.
- These successes **do not** establish a signed RC, production upgrade
  certificate continuity, full recovery on isolated state, or final
  user-device visual acceptance. No release tag was created.

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
- Public CI cannot replace private corpus/memory gates or signing-cert continuity.
  The **private memory and full-corpus gates have now passed** at
  commit `86eadc4` in run 40189976260; **certificate continuity
  remains unverified for a final signed beta4 artifact**.

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
- **Historical preflight20 note:** subsequent preflight21 private gates
  and signed-device smoke passed; consult the gate table and latest
  dated evidence above rather than this initial checkpoint.

## Private runner results and full-corpus skip guard

- [Run 40182926717](https://github.com/HighwindBR/AA-Experiments/actions/runs/40182926717)
  on commit `0725d7e` passed the three actual memory gates (installed
  scan, historical compatibility, and three-key directed resolve) with
  256 MiB JVM test workers. It also passed the release unit suite and
  lint. The public build
  [40181986952](https://github.com/HighwindBR/AA-Experiments/actions/runs/40181986952)
  passed tests, lint and debug APK.
- **Audit finding:** although the `Frozen 17.8 full-corpus release gate`
  step reported success, its JUnit test was guarded by
  `assumeTrue(archive.isFile && outputs/discovery/inventory-17.8.663814.json.isFile)`.
  The untracked `outputs/discovery/` file is not supplied by a clean
  Git checkout or fixture staging. The test also depended on
  `aa.full.deep=true` being inherited automatically by the test
  worker, which Gradle does not guarantee. No `FULL_DEEP completed=...`
  output appeared in the private logs, and the step completed without
  evidence of the 919-key scan. **Do not count this as full-corpus success.**
- The follow-up patch loads the tracked, SHA-256-verified
  `fixtures/compatibility-17.8.663814.json` as its exact 919-key
  baseline, fails closed when missing or malformed, and explicitly
  forwards `aa.full.deep` from Gradle to the forked test JVM.
  The test still requires all 919 keys to be processed without
  unknown blocking value-flow outcomes under the original 256 MiB.
- **Resolved on 2026-10-25:** private run
  [40189976260](https://github.com/HighwindBR/AA-Experiments/actions/runs/40189976260)
  at commit `86eadc4` passed the corrected full-corpus stage. Its
  post-Gradle JUnit XML verifier printed `tests=1, failures=0,
  errors=0, skipped=0`. The three separate 256 MiB gates also passed.
  RC approval remains blocked only by the remaining *non-automated*
  device, isolated restore, signing and final candidate gates.

## Compatibility 256 MiB heap failure and corrective strategy (preflight21)

- New self-hosted WSL run
  [40170439514](https://github.com/HighwindBR/AA-Experiments/actions/runs/40170439514)
  passed fixture integrity, the release unit suite, release lint and the
  installed-scan 256 MiB gate. The frozen 919+23 identity/type proof
  is therefore accepted for the *cold scan*.
- The second gate, **compatibility rebuilding against the 919-key
  historical getter baseline**, failed with `java.lang.OutOfMemoryError:
  Java heap space`. The reported stack ended inside the eager
  `enriched.toList()` in `ConsumerGraphAnalyzer.enrich` (via
  `DexCatalogAnalyzer.analyze`). This is a real memory regression,
  not a count mismatch or runner issue.
- The preflight21 change opts **only compatibility full scans** into
  in-place consumer enrichment over the independently allocated,
  already-owned rediscovery identifier list. This avoids a second
  complete 54k-item output generation while graph indices are live.
  The empty-baseline path and directed/streaming analysis remain
  unchanged. Existing per-entry enrichment and safety behavior are
  preserved; a new unit test compares the results.
- This is **not validated under 256 MiB until the private gate
  passes again**. Do not increase the limit or mark RC-ready
  prematurely. The directed-resolve and frozen full-corpus gates
  still need execution.

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

## Local signed-device candidate procedure

After the signed preflight18 full recovery export was saved, a
non-publishing workflow was added for a manual, **release-key-signed**
preflight21 APK retained only on the user's WSL host. This build
verifies v2 signature, versionCode 167, package identity, and
certificate continuity with the pinned official beta3 APK before
copying any APK to the private host destination.

See [Signed candidate device test](BETA4_SIGNED_CANDIDATE.md).
The existence of this workflow does **not** count as execution,
installation, signing acceptance, or RC approval. The job requires
explicit manual dispatch and no release/public artifact is created.

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

- `app/build.gradle`: preflight21 development version only, not final RC.
- `.github/workflows/private-release.yml`: future beta4 tagging
  guarded; no tag currently created.
- `docs/RELEASE.md`: updated publishing/RC procedure.
- `docs/RELEASE_NOTES_1.0.0-beta4.md`: draft notes.
- `docs/BETA4_RC_READINESS.md`: this sign-off checklist.
- `README.md`, `CHANGELOG.md`: current stage and limitations.

**Not done:** merge, tag, actual RC build, GitHub release or LSPosed
catalog submission.
