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
- Add JVM concurrency and failure-injection tests (commit error, publish error, saved-but-not-published, ordered commits).
- This is **incremental**: remaining Advanced pause/restore, Experimental, reset, import and Media Startup preference paths still need to be migrated or explicitly audited for competing operations. Do not claim global write serialization yet.

## Verification

Public GitHub Actions: `:app:testDebugUnitTest`, `:app:lintRelease`,
`:app:assembleDebug`; follow the workflow runs on the draft beta6 PR.

The private 256 MiB fixtures and on-device signed testing remain deferred
until the beta5 runner is available. A public green CI is not a release
sign-off.

## Later PF1 tasks (not yet implemented)

1. Coordinate shared-preference writes with publication and expose an explicit
   publication outcome; avoid success messages after publication failure.
2. Keep the installed catalog pinned independently of read-only imported
   archives and add instrumentation regressions for pruning and restoration.
3. Exercise complete recovery against simulated failures and reconnects,
   without destructive tests on real user preferences.

## Branch policy

The beta5 release is already published and its final source is now included
in this branch. Keep beta6 as a draft PR until independent release gates pass;
never change the official beta5 tag or its runtime hook implementation.
