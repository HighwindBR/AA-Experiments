# Beta5 — preflight3: explicitly requested Spotify open proof

**Development-only / branch-only.** The public beta4, preflight1 and preflight2 are unchanged.

## Evidence and scope
- AA base SHA-256 must match `f3e90ea898469991785dbf91500e1d50db4c637e0e67483065ee0813e3b59c73`.
- Prior PF2 field logs showed `GhLifecycleService.f()`, `ruc.run()` and `nfh.k(Intent, rgi)` on the real projected UI path.
- Inspected 18.1 DEX also establishes `nfh.a(): nfh` as a static dependency-injection getter and `rgi.<init>()` as an empty options constructor.
- `GhLifecycleService.d()V` contains `onProjectionEnd()` and `h()V` contains `onProjectionTearDown()`: both are hooked to revoke the ready state.
- Keep eight targeted PF2 hooks as observations. The PF3 call targets only Spotify, never arbitrary apps or car functions.

## Manual command path
1. Enable Media Startup under Diagnostics and **restart Android Auto** so the pinned hooks install. A profile gate prevents installation on other AA builds.
2. Start projection, allow onProjectionReady() to return and observe at least one projected activity start.
3. While PARKED, explicitly tap **Open Spotify once (test)** and confirm in the second dialog.
4. AA Experiments writes `v1|<elapsedRealtime>|<random UUID>` to LSPosed remote preferences using `commit()`. There is no exported broadcast, receiver, new system permission or public HTTP endpoint.
5. The module sees the preference listener or checks it in a one-second poll while the projection session is ready. It enforces a 12-second TTL, monotonic clock, session readiness, activity-start evidence, and one attempt per session. A persisted request is ignored on process startup.
6. On the Android Auto MAIN thread, it resolves `nfh.a()` and calls `nfh.k(Intent, rgi)` with action `androidx.car.app.media.action.SHOW_MEDIA_PLAYBACK` and the fixed Spotify CarApp service `com.spotify.music/com.spotify.carapplibrary.androidauto.AndroidAutoService`. The existing AA controller retains its ordinary policy and dispatch.
7. Activity end or teardown revokes readiness, cancelling subsequent attempts. No background auto-start is implemented.

## Interpretation
- `MEDIA_PF3_PROOF_ARMED`: reflected ABI present. NOT evidence of UI launch.
- `MEDIA_PF3_REQUEST_ACCEPTED`: fresh user request accepted in the active projection session.
- `MEDIA_PF3_REQUEST_DISPATCHED`: reflective method returned. NOT proof Spotify appeared.
- `MEDIA_PF3_LAUNCH_FAILED`: method invocation failed (logs only the exception class).
- `MEDIA_PF3_REQUEST_REJECTED`: e.g. NOT_READY / NO_ACTIVITY / ALREADY_ATTEMPTED / EXPIRED.
- `MEDIA_PF2_STAGE_ENTER` and Android Auto `GH.IntentProcessor` / `GH.ProjectionContext` / `GH.ActivityTracker` should be correlated to prove routing; visually confirm on HU.

No changes to audio playback, video gating, vehicle controls, normal overrides, or automated startup. The proof may not open Spotify if this version requires additional Intent extras or a different routing context: retain logs for a follow-up, do not retry continuously.

## Test checklist
- [ ] Public unit, lint and assembleDebug CI gates.
- [ ] PF3 installation: 8/8 hooks, `MEDIA_PF3_PROOF_ARMED`.
- [ ] With AA projected (Maps visible), press the manual button only while parked.
- [ ] Inspect LSPosed and continuous logcat for acceptance/dispatch and resulting selected app.
- [ ] Confirm a second press is rejected during the same projection session.
- [ ] Disconnect AA and verify no delayed/replayed request.
- [ ] Validate offline/missing Spotify failure and preserved beta4 overrides where practical.
- [ ] Do not merge, tag or publish a Release without authorization.
