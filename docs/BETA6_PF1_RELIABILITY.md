# Beta6 — Reliability PF1 (development only)

Base: official beta5 final (`v1.0.0-beta5`, versionCode 178), merged into beta6 through `c58cfdf94a835fa95bf8dfaf0427551bfc33ac26`.
Branch: `feature/beta6-reliability`; current development version `1.0.0-beta6-preflight1` / versionCode 179. Do not merge, tag, publish or edit
`feature/beta5-rc1` as part of this work.

## First implementation slice

- Bound the initial user-selected APK/APKM/APKS copy to 256 MiB and reject
  empty content. Delete partial temporary copies on failure.
- Bound APK-set extraction to 32 APK entries, 256 MiB per extracted APK,
  and 512 MiB total extracted APK bytes. Check streamed bytes rather than
  trusting ZIP declared sizes; clean partial APKs after any extraction error.
- Discard stale asynchronous catalog-search results, selected experiment details
  and Advanced-candidate results. A dismissed detail/Advanced panel invalidates
  its outstanding requests.
- Add JVM tests for copy limits, normal and invalid APK sets, extraction cleanup
  and latest-request invalidation.

These are changes to manager-side I/O and state handling only.
No Android Auto hooking, override eligibility, profile schema,
signing secrets or media-startup semantics change.

## Second implementation slice

- Add a shared `ProfileMutationCoordinator` around preference commit plus verified profile publication, using the existing `ProfileRefreshGate`.
- Normal and Advanced value editors now return distinct outcomes for persisted preferences and fully published profiles; failed profile publication is not presented as a successfully applied value.
- Keep complete recovery commit and profile republishing in the same locked scope, while retaining restored records if publication fails.
- Use the same coordinator and explicit publication result for VTC and Media Startup diagnostic trace controls.
- Add JVM concurrency and failure-injection tests (commit error, publish error, saved-but-not-published, ordered commits).
- Advanced pause/resume, individual and bulk restore, legacy LONG handoff, Experimental apply/pause/restore, read-only probe selection, suspended-override removal, normal JSON import and destructive reset now share the same write-and-publish gate. Bulk operations retain their restored/imported item counts.
- Media Startup arm/disarm now holds the gate from remote/local preference commits through profile publication. Route A/B and the PF4/PF6 automatic opt-ins use the same gate to avoid stale preference interleaving without adding extra profile publications.
- Manual PF3/PF6 *requests* are one-off commands delivered directly to the active runtime, not configuration mutations; they remain outside profile publication. Their session and replay guards remain unchanged.
- This still does **not** make two distinct SharedPreferences stores an atomic transaction. Failure-safe remote rollback is attempted when per-build media consent fails to persist locally, and rollback failure is surfaced explicitly.
- JVM tests cover ordered concurrent writes, commit rejection, publish failure, saved-but-unpublished outcomes, and retained item counts. Android/LSPosed integration still requires signed-device smoke.

## Third implementation slice: installed catalog and recovery verification

- Upgrade SQLite cache schema from version 7 to 8. Store catalog provenance
  (`INSTALLED`, `IMPORTED`, or migrated `LEGACY`) on every build row.
- Pin the actual installed build fingerprint persistently after both a fresh
  scan and an already-cached installed-AA scan. Offline archives cannot evict
  that pinned catalog.
- Bound the retained cache to the pinned installation, one predecessor for
  installed-AA compatibility comparison, and at most one recent offline archive
  (three build catalogs maximum with a valid pin; historical unpinned mode
  retains two).
- Exclude imported archives from the prior **installed** build used for Status
  changes and compatibility baselines. When the same APK is imported as the
  installed build, retain installed provenance.
- Add pure JVM cache-retention regression tests: repeated imports, installed
  build upgrade, missing pin, predecessor selection, and bounded fallback.
- Add Android instrumentation tests for SQLite migration 7→8, pin persistence
  across database reopen, catalog pruning and installed comparison. **These
  instrumented tests are not run by the public JVM workflow.**
- Add a JVM simulated recovery/publication integration test: corrupted bundle
  is rejected before any managed preference is changed; a failed slot pointer
  commit or corrupt readback preserves the live profile, and preserved restored
  records can be republished on retry. This uses fake storage, **not actual
  LSPosed remote preferences**.

## Verification

Public GitHub Actions: `:app:testDebugUnitTest`, `:app:lintRelease`,
`:app:assembleDebug`; follow the workflow runs on the draft beta6 PR.

The private 256 MiB fixtures and on-device signed testing have not been run
for beta6 yet. They remain required before beta6 release. A public green CI is not a release
sign-off.

## Later PF1 tasks (not yet implemented)

1. Audit remaining local display-only preferences and session command paths, and run targeted
   physical tests for Media Startup A/B opt-in and disarm on a parked head unit.
2. Execute the new Android instrumentation migration/pruning tests on an
   emulator or device, since the public JVM checks cannot execute them.
3. Reproduce integrated LSPosed disconnect/reconnect and restore failures
   on a disposable signed beta6 candidate before any release; never use real
   stored baselines as destructive test fixtures.

## Branch policy

The beta5 release is already published and its final source is now included
in this branch. Keep beta6 as a draft PR until independent release gates pass;
never change the official beta5 tag or its runtime hook implementation.
