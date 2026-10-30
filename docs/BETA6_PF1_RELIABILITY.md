# Beta6 — Reliability PF1 (development only)

Base: frozen beta5 RC1 `e8286335b8261d4e8e249049b27324b27a989578`.
Branch: `feature/beta6-reliability`. Do not merge, tag, publish or edit
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

The beta5 release candidate is frozen. This branch temporarily includes
its unmerged code. Once the beta5 final release is approved and merged,
reconcile beta6 against that exact release before claiming the beta6 baseline
is official. Do not merge a stacked beta6 PR into main while beta5 is pending.
