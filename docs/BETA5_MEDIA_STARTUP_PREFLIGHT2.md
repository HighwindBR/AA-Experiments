# Beta5 Media Startup — preflight2: targeted projection observation

**Status:** research-only build, not the automatic Spotify launcher. Use while parked.

## New evidence from logcat (2026-10-28)
- Projection process: `com.google.android.projection.gearhead:projection` (not :car / :shared).
- 18:55:06.080: `GH.GhLifecycleService.onProjectionReady()`.
- 18:55:07.605: `GH.MediaModel.onMediaAppChanged` points at Spotify, **before** its projected UI is opened.
- 18:55:24.590: `GH.IntentProcessor` processes `SHOW_MEDIA_PLAYBACK`.
- 18:55:24.602: `GH.ProjectionContext.startCarActivity` is logged.
- 18:55:24.622: `GH.ActivityTracker` records projected start.
- 18:55:24.655: `updateGsaWithNewApp` switches to Spotify.
- Repeated Waze/Spotify/Maps switches follow the same pattern.

## Method mapping verified against supplied AA 18.1 classes.dex
- `GhLifecycleService.f()V` — projection readiness callback.
- `new.g(Intent, rgi)V` — Intent processor.
- `new.h(Intent)V` — stored Intent target.
- `nfh.k(Intent, rgi)V` — projected startCarActivity.
- `mcl.l(ComponentName)V` — media app change (not UI focus).
- `ruc.run()V` — ActivityTracker projected activity start request.

All hooks are **observation-only**, fail open, install only for the exact previously inspected base APK SHA-256 and the projection process, independently check their ABI before installation, and do not inspect or log Intent extras, application identifiers, account names or media data. Each stage is bounded to 32 log entries per process. The original methods always execute through `chain.proceed()`.

The existing Settings → Diagnostics → Developer tools → Media Startup toggle arms these hooks; defaults to off. The older pf1 hooks are not installed in pf2.

## Test
1. Install debug-signed `1.0.0-beta5-preflight2` (`versionCode=171`) on the device using your established update method. No public release.
2. Disconnect projection, arm **Media Startup · preflight2** under Diagnostics, force-stop Android Auto and reconnect.
3. While parked, let projection open automatically, then select **Spotify → Waze/Maps → Spotify → launcher → Spotify**.
4. Export **LSPosed logs** and preferably simultaneous Android Auto `logcat`, noting whether projection was automatic or manual.
5. Check `MEDIA_PF2_INSTALLATION_COMPLETE installed=6` and time order of `MEDIA_PF2_STAGE_ENTER`, `MEDIA_PF2_STAGE_RETURN`. Any rejected target requires investigation, not forced action.
6. Disable observation after testing.

These events prove entry into selected call paths, **not** physical head-unit display readiness, app foreground status or automatic launch capability.

## Next gate (only after physical test)
Investigate the safe owner/context and exact Intent synthesis for a one-shot, explicitly manually triggered launch test. There is **no automated or synthetic launch** in this preflight, and no vehicle controls, no driver-distraction bypass, no dashboard override. Do not promote to main or publish without approval.
