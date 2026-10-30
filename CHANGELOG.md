# Changelog

## 1.0.0-beta5-rc1 — validation candidate (not released)

- Generalize dynamically resolved Media Startup A/B routes to the **currently selected Android Auto media app**, rather than Spotify only. The media-selection callback provides the actual component; full package and class must still match at dispatch. No arbitrary app/package entry or autoplay.
- Keep automatic startup OFF by default for every AA build. Preserve 2800 ms settle, 12-second readiness cutoff, one attempt per session, navigation suppression, and explicit route preference without automatic fallback.
- Consolidate the Diagnostics card: primary automatic media startup controls; historical pinned PF3/PF4 and manual test tools are secondary. Pinned PF4 remains Spotify-only.
- Preserve exact base-APK SHA and build-fingerprint linkage, resolver ambiguity rejection, and manual-only degradation when first projected activity is not uniquely resolved. No downgrade to inferred method names.
- Add tests for other selected media packages, package/class mismatch, null or incomplete components, and shared one-attempt launch budget.
- Candidate identity: `1.0.0-beta5-rc1`, versionCode 177. This branch has **not** passed Android Auto 18.1 physical/head-unit acceptance for non-Spotify media or independent second-build execution. No merge, tag, signed release or official publication authorized. See [RC1 validation](docs/BETA5_RC1.md).

## 1.0.0-beta4 — release

- Freeze final metadata as `1.0.0-beta4` / versionCode 169,
  retaining application ID `io.github.highwindbr.aaxp`. Private
  candidate filename is `AAExperiments-1.0.0-beta4-candidate.apk`;
  official release asset will be `AAExperiments-1.0.0-beta4.apk`.
- Consolidate beta4 Advanced BOOLEAN review, parked-mode risk
  confirmation, compact scanning UX, consolidated Backup & recovery,
  exact-build full recovery, paging exports, safe runtime profiles
  and frozen 17.8 corpus corrections.
- Preflight22 passed public/private signed gates and visual UI
  acceptance. Actual normal, Advanced and Experimental runtime
  getter-return evidence comes from signed preflight21 logs.
- The maintainer accepted override preservation and waived additional
  Status screenshots and exports; normal and complete restore were
  reported successful, without separately proven isolated/byte-for-byte
  restoration.
- Final code/metadata candidate passed [public CI 40528898122](https://github.com/HighwindBR/AA-Experiments/actions/runs/40528898122)
  and [private signed gates 40532709266](https://github.com/HighwindBR/AA-Experiments/actions/runs/40532709266).
  On 2026-10-27 the maintainer confirmed successful installation
  and operation and authorized official GitHub publication. Tagged
  release checks must independently pass before APK publication.
  LSPosed catalog submission remains separate. Older preflight
  entries below preserve their point-in-time "pending" language.

## Beta4 — optional private GitHub delivery for signed preflight22 (CI only)

- Added `deliver_private_candidate` opt-in to the manually invoked
  private-gated signing workflow. A signed APK may be uploaded as an
  **unpublished draft** in a **separate PRIVATE repo** for phone
  download. The public source repository receives no APK artifact,
  release, tag or merge; default behavior still keeps the APK on WSL.
- Restricted delivery to the exact beta4 branch and source identity;
  verifies SHA-256 sidecar and destination `private=true` from GitHub.
  Private destination credential is short-lived and limited to that repo.
  Upload aborts on errors and tries to remove partial drafts.
- Offline Python tests verify private-repo filtering, exact APK name
  and checksum, and run-specific draft identity. These tests run in
  both normal and private release unit-test jobs.
- Disabled the legacy manual signed-probe APK upload to **public**
  GitHub Actions artifacts in `debug-apk.yml`.
- No Android application, versionCode, hook logic or backup-format
  change; this is **not** a new beta4 signed build or official release.

## 1.0.0-beta4-preflight22 — scan-dialog refinement and combined beta4 UI updates

- Replaced fixed-height placeholders (64 dp detail, 20 dp count, 42 dp
  optional cancel) in the **Analyzing Android Auto** dialog with a
  compact progress track and a single detail/count row. Optional
  controls only occupy space when visible.
- Determinate stage progress uses one continuous rounded track rather
  than the visually detached Material3 stop indicator; unknown work
  remains indeterminate and no false overall percentage is shown.
- Temporary **Not found** and **Profile not published** messages are
  replaced with scanning-state wording while analysis is active.
- Includes already staged **Backup & recovery** UI, Advanced BOOLEAN
  explicit selection fix, and historical parked-policy labeling.
  Existing catalog/override runtime algorithms and backup formats
  remain unchanged.
- `versionCode=168`, `versionName=1.0.0-beta4-preflight22`.
  Signing is gated on the three 256 MiB fixture tests plus frozen
  full-corpus gate; no tag or public release.
- Candidate validation remains pending until CI, signed WSL build
  and device screenshots are accepted.

## Beta4 Advanced BOOLEAN and legacy-verdict UI fix — next signed candidate

- Fixed an Elvis/null validation bug in `AdvancedOverrideEditor`:
  for a correctly selected BOOLEAN, the codec returned null (no issue)
  but the UI fell through to `Choose True or False before reviewing`,
  disabling `Review Advanced value` permanently.
- A new BOOLEAN Advanced override now starts without a selected value
  instead of implicitly treating the compiled default as the user's
  choice. Explicitly selecting True or False enables review; the
  existing two-step/typed confirmation and elevated `PARKED APPLY`
  protection remain mandatory.
- Added regression tests for missing, valid and invalid scalar inputs.
- A stale saved catalog's former parked-substring safety reason is
  labeled **historical evidence**, not the current Advanced eligibility
  verdict. No stored catalog or override records are rewritten.
- These changes are staged for the next signed candidate, **not**
  the preflight21 already on the device.

## Beta4 UX staging — next signed candidate (not yet installed)

- Consolidated five Data actions into one **Backup & recovery**
  Settings entry. The new subpage prioritizes the full recovery
  export/restore pair, retains both existing normal-only JSON
  operations behind an expandable legacy group and isolates the
  destructive reset behind an expandable Danger zone.
- No changes to the backup data formats, full-recovery typed
  acknowledgement, build/fingerprint validation, preference storage,
  LSPosed hooks or runtime override profiles.
- Navigation regression checks now cover BACKUPS returning to Settings.
- The current **signed preflight21** was built and validated from
  source `311e5dd`. This UI commit is **not included** in that
  installed APK and will ship only in the next approved signed
  candidate. No special APK was requested just for this change.

## beta4 full-corpus gate activation — post-preflight21 audit

- Verified private run 40182926717 passes all **three real 256 MiB**
  fixture gates, including prior compatibility OOM remediation.
- Found the fourth `FullDeepResolveDistributionTest` could be
  **silently skipped**: it depended on a non-versioned
  `outputs/discovery/` inventory and an unforwarded Gradle system
  property. Its green Actions step is not adequate evidence.
- Replaced the private baseline with the frozen, tracked and
  manifest-verified 919-key compatibility reference; a missing
  baseline now fails rather than skips. Forwarded `aa.full.deep`
  to forked JUnit workers.
- The full-corpus gate is **not verified yet**. No RC, merge, tag or
  release has been authorized.

## 1.0.0-beta4-preflight21 — compatibility memory hardening (not an RC)

- The signed-fixture WSL run
  [40170439514](https://github.com/HighwindBR/AA-Experiments/actions/runs/40170439514)
  passed the installed-scan 256 MiB gate with **942 exactly reviewed**
  editable keys, confirming the preflight20 cohort fix.
- The 256 MiB **compatibility** gate then failed with
  `OutOfMemoryError: Java heap space`. Its stack pointed to
  `ConsumerGraphAnalyzer.enrich` retaining another 54k-identifier
  generation while producing the reverse-call-graph results.
- Compatibility analysis now replaces entries **in the already-owned
  rediscovery list**, rather than constructing another complete
  result list while indexes are live. No traversal budgets, key-set
  allowlists, getter confidence rules or heap gates were relaxed.
  Normal cold scans and directed deep resolve retain their previous
  behavior.
- Added unit checks comparing the in-place result with the existing
  non-mutating implementation and guarding against misuse in
  cancelled/directed streaming sessions.
- **Pending:** public CI and WSL/private 256 MiB/full-corpus rerun.
  This is a candidate memory fix, not a measured pass yet.

## 1.0.0-beta4-preflight20 — frozen 17.8 fixture reconciliation (not an RC)

- The private Android Auto 17.8.663814 memory gate exposed **942**
  currently editable mappings instead of its beta3 expectation of 919.
  All 919 beta3 reference keys were preserved. The 23 additions are
  12 exact elevated-risk parked-mode media timing keys, 3 WirelessLatency
  false SSL token matches, and 8 BOOLEAN config/manufacturer flags
  previously misclassified as possible Protobuf.
- Replaced the stale literal 919 expectation in both private regression
  tests with an **exact key-set union**: immutable beta3 919-key
  fixture plus a reviewed 23-key beta4 delta. Any further
  undocumented addition, loss, type, resolver or safety change
  continues to fail the test.
- Applied the elevated-risk warning and typed `PARKED APPLY`
  confirmation to the **normal** Experiment editor too. Without this,
  the 12 newly normal-editable parked timing knobs bypassed the
  warning previously limited to Advanced editing. Reset-to-default
  remains a one-step recovery action.
- Added an evidence ledger and updated release readiness.
- Public CI and all private gates are **pending verification for this
  preflight**. No signed RC, merge, tag or publication was made.

## 1.0.0-beta4-preflight19 — static pre-RC review (not a release)

- Corrected full-recovery validation so that current-build **active**
  Advanced/Experimental hooks must pass current getter checks, while
  preserved inactive and historical records do not need to be runnable.
- Added tests for active versus paused/historical record selection.
- Updated current Advanced navigation instructions, the known-limitations
  evidence, private-runner handoff checklist, and beta4 release guidance.
- No changes to saved preference schema, live overrides, runtime hook
  installation, profile publication, or Android Auto package scope.
- No RC, tag, merge, GitHub Release, or LSPosed catalog submission.

**Static review code is pending its final CI/device acceptance.** Do
not replace a working preflight18 merely for these documentation changes.

## 1.0.0-beta4-preflight18 — Round D release hardening (not an RC)

- Fixed full-recovery ownership validation: paused/historical
  Advanced and Experimental records can coexist with a normal value;
  active current-build hook conflicts remain prohibited.
- Added four recovery regression cases covering paused, historical and
  current-build ownership.
- Limited legacy normal-backup file import to 2 MiB to avoid unbounded
  reads from user-selected document providers; errors are reported in-app.
- Updated release gates for an eventual `v1.0.0-beta4` tag:
  build+lint, private fixture regressions, tagged APK version checks,
  and signing-certificate comparison against the official beta3 APK.
- Prepared beta4 draft notes, RC readiness gates and private-candidate
  handling guidance. No tag, release or merge was created.

**Signed development build and complete CI still require verification
for this commit.** Full-restore device testing is isolated-only;
do not reset the user's configured Android Auto overrides.

## 1.0.0-beta4-preflight17 — Round C data recovery (CI and device pending)

- Export discoveries directly to SAF documents using 250-record SQLite pages;
  no 50,000-row JSON/CSV strings are held in the Compose ViewModel.
- Export per-page getter/consumer evidence using key-constrained queries.
- Added separate **full recovery bundle** with normal active/suspended,
  Advanced and Experimental records, including original absence/baselines.
- Full restore is build-fingerprint + versionCode pinned, checksummed,
  type/ownership validated and atomically replaces only managed preference keys.
- Current-build special getter records must pass their runtime safety and
  structural matcher again before restoration can commit.
- Requires a `RESTORE ALL` typed confirmation with a preview of counts
  and active special records; legacy normal-only backup import remains unchanged.
- Added paged export and recovery bundle regression tests.

Recovery files contain raw STRING values and should be kept private.
**Do not test complete restoration on the user's 100-override live profile.**
This is not an RC or public release; full signed-device/CI validation pending.

## 1.0.0-beta4-preflight16 — visual QA follow-up (CI/device pending)

- Fixed nearly invisible dark-theme back arrow in Experiment details by
  explicitly using `MaterialTheme.colorScheme.onBackground`.
- Armed read-only Developer tools are automatically revealed, but the
  section can now be collapsed and reopened while a diagnostic remains
  armed. The previous unconditional visibility made the toggle inert.
- Documented visual validation of sixteen user-provided light/dark
  screenshots; no personal screenshot assets are committed.

No LSPosed runtime, profile, persisted override or catalog behavior changed.
This remains development work, not an RC or release.

## 1.0.0-beta4-preflight15 — Round B Compose UX and accessibility (CI/device pending)

- Responsive Experiments title and counts, avoiding collisions on small displays and larger fonts.
- Theme-aware status colors and accessible, 48dp+ filter toggle rows.
- Clarified `Configured value` versus independent LSPosed runtime verification.
- Validated normal and Advanced scalar inputs inline with shared typed logic,
  including finite DOUBLE/FLOAT, signed bounds and explicit Advanced BOOLEAN selection.
- Preserved valid empty-string overrides; STRING editing supports multiline input.
- Replaced long-lived Experiments success/error banners with transient Snackbars;
  the detail editor still displays operation-specific failure feedback.
- Kept Advanced opt-in prominent and grouped the VTC and historical DOUBLE
  probes in an optional Developer tools subsection.

No normal/Advanced preference schema, runtime hook, catalog mapping or
LSPosed profile was modified by Round B. **Preflight15 is not an RC or release.**
Full CI, signed artifact and device UI smoke checks remain pending.

## 1.0.0-beta4-preflight14 — Round A reliability hardening (CI/device pending)

- Dual-slot profile publication with verified read-back and durable pointer commit; legacy profile fallback remains available.
- Serialized profile refreshes and build-scoped UI state handoffs prevent stale asynchronous publication.
- Single-owner catalog/DEX analysis gate prevents overlapping foreground-service users.
- Remote preference disconnection no longer publishes an empty profile; stale service-death callbacks are ignored.
- Added injected write/read/commit failure tests, refresh concurrency tests and analysis ownership tests.
- Restored loading/progress UI after interrupted analysis and published `PROFILE_SOURCE` diagnostic event.

**Development preflight only**: full Android Gradle tests, release lint, signed APK build and LSPosed on-device smoke test have not yet been executed for this revision.

## 1.0.0-beta4-preflight13 — development build (validation pending)

### Runtime hardening and coverage audit
- Hardened normal BOOLEAN, INT byte/short/char, FLOAT and DOUBLE decoding.
- Redacted returned normal STRING content from LSPosed module events.
- Added normal getter ABI/range compatibility checks and suspended colliding getter aliases.
- Restricted backup import to current editable, compatible normal targets.
- Required typed double confirmation for every Advanced override.
- Added mechanism-level regression tests and `docs/BETA4_MECHANISM_AUDIT.md`.

This is **not** a published release. The full Gradle test/lint/signing
pipeline and representative installed-device regressions are still pending.

## 1.0.0-beta3 — release candidate (validation pending)

### Fixed after on-device beta3 testing
- Added, Removed, and Changed now list individual differences between successive editable catalogs, with previous/current metadata; they no longer describe suspended-override reasons.
- Experiment category filters live alongside Value type in the Filters sheet and categories with no entries are hidden.
- Suspended overrides can be retained while hidden from All for the current Android Auto build, automatically appearing again if suspended on a later build.

### Added
- Inspect, filter and safely discard suspended overrides while preserving their stored values and recoverable names.
- Historical Android Auto 18.0 coverage in the feature catalog, including startup optimizations.

### Changed
- Aligned code namespace and internal package names with the unchanged application ID.
- Clearer saved-value feedback for scalar Apply actions and Lab Green CUSTOM indicators.
- Stable resolver dialog height with underscore-aware wrapping.
- Documentation of update-resilient flag discovery.

### Not included
- Force-reapply of incompatible overrides is postponed until after beta3.

### Known limitations
- Previously stored overrides may lack a recoverable name if all historical mappings have been pruned.
- Complete CI and physical-device validation is required before publishing.

## 1.0.0-beta2 — published 2026-10-12

### Fixed
- First APK import no longer reports a SQLite query error while pruning old catalogs.

### Changed
- Version metadata and in-app version labels now identify the beta2 catalog candidate.

## 1.0.0-beta1 — pending publication

### Added
- GPL-3.0-or-later project licensing, bundled third-party notices, and explicit copyright attribution.
- Privacy and AI-assisted-development documentation covering local processing, lack of developer/OpenAI runtime data collection, and manual physical/head-unit validation.
- Atomic immutable runtime override snapshots and strongly retained preference listener.
- Fixture hash gates, release lint and published APK checksum.
- Canonical documentation with indexed alpha history.

### Changed
- Long-analysis foreground-service lifetime is cancellation-safe.
- Automated private release gates and the physical smoke test have passed for beta1.
- Version remains frozen at `1.0.0-beta1` / versionCode 141 for publication.

### Known issues
- Active scans and deep analyses may stall or fail to complete if the device screen turns off. Keep the device awake until analysis completes. This is an accepted non-blocking beta1 limitation.

## Pre-beta development

Alpha01–40 established the Manager, API 101 runtime, typed overrides, full DEX resolver, structural
rediscovery, Protobuf/enum safety, cache and activation analysis, directed deep resolve, composite
build identity, 256 MiB gates and reproducible release workflow. See `docs/history/ALPHA_HISTORY.md`.
