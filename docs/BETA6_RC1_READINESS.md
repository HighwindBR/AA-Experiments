# Beta6 RC1 — release-gate checklist (NOT approved)

Branch: `feature/beta6-reliability`, PR #13 (draft).
Published beta5 and `main` remain unchanged.
This document does not authorize a release, tag, merge or signing.

## Public verification (exact candidate commit)
- [ ] `:app:testDebugUnitTest`
- [ ] `:app:lintRelease`
- [ ] `:app:assembleDebug`
- [ ] `:app:assembleDebugAndroidTest` (compilation only; NOT execution)
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
      process kill, reboot, OS foreground-service timeout or the two-hour
      wakelock timeout. A durable worker/job migration is out of scope.
- [ ] Confirm streamed deep-resolve entries already committed remain after
      deliberate analysis interruption; rerunning does not duplicate records.

## Beta5 -> Beta6 upgrade / database
- [ ] Install over beta5 using the same signing identity. Preserve saved
      overrides, Advanced/Experimental baselines and Media Startup consent.
- [ ] Execute the instrumented SQLite schema 7->8 migration and
      installed-cache retention tests on emulator or phone.
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
- [ ] Full private 256 MiB fixture/frozen-corpus regression via trusted runner.
- [ ] Signed APK checksums, release notes and publish workflow checks.
- [ ] Explicit release approval before merging PR #13 or tagging beta6.

## Known residual risk
A foreground service plus partial wakelock is a targeted **screen-off CPU
continuity improvement**, not durable background execution. The actual scan
coroutine remains owned by the ViewModel. Android battery policy, task removal,
force stop or process death can end the job. A wake lock lasts at most two
hours; the app intentionally releases it as soon as analysis ends.
