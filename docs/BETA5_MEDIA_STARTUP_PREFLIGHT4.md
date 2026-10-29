# Beta5 Media Startup — preflight4 (automatic Spotify opening)

**Experimental opt-in, default OFF.** Only for the exact Android Auto 18.1.164344-release.daily base APK previously verified by SHA-256. The public beta4 and PF1/2/3 remain unchanged.

## What changed from PF3
- PF3 verified with user's real HU that `nfh.a() / nfh.k(Intent,rgi)` successfully opens Spotify on the car screen when requested manually.
- PF4 adds a separate LSPosed remote preference `diagnostics.media_startup.spotify_auto_enabled`; no normal flag override, no schema change, no new exported Android component.
- Existing Media Startup trace MUST be enabled and its profile published; PF4 remains disabled by default and requires a second confirmation in Diagnostics.
- New session: after the existing `GhLifecycleService.f()` readiness callback and first `ruc.run()` projected activity callback, wait **2800 ms**. This deliberately lets the normal initial Maps/Waze activity settle.
- PF4 dispatches **at most once** and only when `mcl.l(ComponentName)` confirms **Spotify as the active media app**. No change to autoplay/audio, no attempt to select a different media app.
- If the AA internal `nfh.k(Intent,rgi)` launch route was entered after the first projected activity, PF4 treats it conservatively as an explicit navigation signal and cancels. A manual PF3 attempt also cancels auto.
- If 12 seconds pass after readiness, if Spotify is not selected, if PF4 is disabled meanwhile, or if a session ends before its scheduled attempt: **skip without retry**. The next session may try again if opted-in.
- PF3 and PF4 share the same one-attempt-per-session launch budget; both use AA main thread and pinned inspected DEX ABI.
- Disabling Media Startup trace also atomically disables PF4 in remote preferences. Existing hooks fail open. Nothing persists into unverified AA versions; both SHA and process are checked.

## Logs
- `MEDIA_PF4_SESSION_ARMED` — records AUTO ON/OFF at projection readiness.
- `MEDIA_PF4_AUTO_SCHEDULED` — first projected activity observed; delayed attempt queued.
- `MEDIA_PF4_AUTO_ATTEMPT` — about to invoke controller.
- `MEDIA_PF4_AUTO_DISPATCHED` — method returned; NOT proof the physical HU displayed Spotify.
- `MEDIA_PF4_AUTO_SKIPPED` — conservatively rejected, reason one of `DISABLED`, `STALE`, `MEDIA_NOT_SPOTIFY`, `NAVIGATION_OBSERVED`, `SESSION_CHANGED`, `NO_SESSION`, `manual_budget_used`.
- `MEDIA_PF4_AUTO_FAILED` — invocation failed (exception class only).

## Test plan (parked)
1. Install debug build `1.0.0-beta5-preflight4` versionCode 173; keep the AA 18.1 build and the usual overrides unchanged.
2. Arm Media Startup trace in Diagnostics, publish, restart Android Auto to install existing PF2/PF3 hooks; verify `MEDIA_PF3_PROOF_ARMED`.
3. Enable PF4 auto-open with its explicit confirmation **while disconnected**.
4. Connect to the car, let Maps/Waze appear, then observe whether Spotify UI opens ONCE without tapping the button or car screen. Audio autoplay remains the same.
5. Disconnect/reconnect; verify one new attempt only, no cross-session replay.
6. Repeat with rapid manual navigation after Maps loads; PF4 should skip and NOT steal focus.
7. Disable PF4 and reconnect; verify no automatic opening, with PF3 manual test still available.
8. Submit LSPosed ZIP and continuous logcat and indicate **visual HU results**; no event by itself proves UI visibility.

CI unit/lint/debug must pass before distributing. Do not merge, tag, publish release, or claim compatibility with other AA builds or head units before tests.
