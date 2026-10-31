# Beta6 RC1 — release-gate checklist (NOT approved)

Implementation baseline: `feature/beta6-reliability`, PR #13.
Maintenance candidate: `maintenance/group-a-stable`, PR #14.
Application metadata remains `1.0.0-beta6-preflight1` / versionCode `179`.
Stable promotion is proposed, not approved. This document does not authorize
a release, tag, merge or signing.

## Recorded automated evidence

[Run #275](https://github.com/HighwindBR/AA-Experiments/actions/runs/41240255586)
completed successfully on 2026-10-31 for PR head
`07affb739fd5141bffe4ea25d83436f2c3068726` against main
`705c5ac6bbab24cc4af894713e91eade3388a3af`.
The runner checked out GitHub's merge commit
`4f9ab3fd239e6152c5e5eab23cc85e5dba8ab90d`.

| Job | Recorded result | Evidence scope |
| --- | --- | --- |
| `unit-tests` | Passed | JVM tests and offline delivery-guard tests; unavailable private fixtures can be skipped |
| `lint-release` | Passed | Android release lint |
| `debug-apk` | Passed | Application and instrumented-test APK compilation |
| `beta6-sqlite-emulator` | Passed | Android 15/API 35 x86_64; log reports `OK (3 tests)` |

The three instrumented tests cover catalog pruning/checkpoint, pin/predecessor
retention across imports and database reopen, and a synthetic v7-to-v8
`builds` table migration. They do not exercise a full beta5 installation with
real overrides, all relational catalog data or LSPosed storage.

The [SQLite artifact](https://github.com/HighwindBR/AA-Experiments/actions/runs/41240255586/artifacts/13056654605)
contains the instrumentation and emulator logs. The script calls `am instrument`
directly; the upload contained three log files, not Gradle connected-test XML.

This evidence belongs to the source/merge commits above. The checklist below
is for the final candidate and remains open until its own run URLs and device
results are recorded; later commits do not inherit an exact-commit certification.

## Final candidate automated verification
- [ ] `:app:testDebugUnitTest`
- [ ] `:app:lintRelease`
- [ ] `:app:assembleDebug`
- [ ] `:app:assembleDebugAndroidTest` (compilation only)
- [ ] Execute the three SQLite instrumented tests on the final candidate;
      record the source/merge SHA, Android API and passing log.
- [ ] All relevant historical 17.8/18.0 resolver fixtures pass without eligibility regressions.

## Real device background/screen-off reliability
- [ ] Installed-AA cold launch: shows **Checking for Android Auto…**,
      never "Not found" until a completed unsuccessful scan.
- [ ] Start a cold DEX rescan. While in progress, switch to another app and
      return using the ongoing analysis notification. The same scan should
      progress rather than restart.
- [ ] Repeat with the screen locked/off for a meaningful scan interval.
      Compare result counts/fingerprint against the screen-on control.
- [ ] Verify the partial CPU wakelock is held only during a foreground DEX
      scan; it is released at completion, failure or cancellation, and the
      notification disappears. Check battery behavior.
- [ ] Confirm normal task switch does not destroy the ViewModel scan job.
      **Do not** expect continuation after user force-stop, task removal,
      process kill, reboot, OS foreground-service timeout or the five-minute
      wakelock timeout. A durable worker/job migration is out of scope.
- [ ] Confirm streamed deep-resolve entries already committed remain after
      deliberate analysis interruption; rerunning does not duplicate records.

## Beta5 -> Beta6 upgrade / database
- [ ] Install over beta5 using the same signing identity. Preserve saved
      overrides, Advanced/Experimental baselines and Media Startup consent.
- [ ] Verify the signed upgrade preserves real catalog and override data;
      use the isolated instrumented tests as supporting evidence.
- [ ] Test repeated archive imports without evicting installed/previous
      installed catalog; check installed-AA comparison baseline.
- [ ] Exercise restore with simulated LSPosed disconnect and retry.
      Never run destructive restoration against unbacked-up user preferences.

## Media Startup and UI
- [ ] On a parked head unit, verify A+B, A-only, B-only route selection,
      manual launch, and one-attempt-per-connection behavior.
- [ ] Confirm automatic opt-in requires unique projected-activity trigger,
      a navigation observer and build-specific consent. AA updates must not
      silently inherit authorization.
- [ ] Check Status for media-only setup and for profile-not-published state.
- [ ] Check full-backup scope copy, Settings tab reentry, color themes,
      enlarged font, landscape, TalkBack and notification return behavior.

## External/private gates
- [ ] Review and enable the exact candidate in the private workflow allowlist;
      neither Beta6 nor Group A is currently accepted (see [Release](RELEASE.md)).
- [ ] Full private 256 MiB fixture/frozen-corpus regression via trusted runner.
- [ ] Signed APK checksums, release notes and publish workflow checks.
- [ ] Reconcile final version/code, signing predecessor, tag allowlist,
      private-delivery guards and release notes before publication is enabled.
- [ ] Explicit maintainer approval for the exact candidate before merge/tag/release.

## Known residual risk
A foreground service plus partial wakelock is a targeted **screen-off CPU
continuity improvement**, not durable background execution. The actual scan
coroutine remains owned by the ViewModel. Android battery policy, task removal,
force stop or process death can end the job. A wake lock lasts at most five
minutes without renewal; the app intentionally releases it as soon as analysis ends.
Confirm that scans exceeding five minutes remain usable with the display on
and that the wake lock is no longer held past the timeout.
