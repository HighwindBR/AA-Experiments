# AA Experiments beta5 — Preflight 6.1

Experimental two-route dynamic Media Startup lab. The previously field-tested PF4 auto-open remains unchanged for AA 18.1 when the PF6.1 lab is disarmed.

## Implementation
- PF5 DEX scanner now proves an auxiliary `(Intent)V` method calls the matched `startCarActivity(Intent,Options)V` and invokes the expected `Options.<init>()V`; matching an opcode count/name alone is insufficient.
- Both execution paths derive their controller class, method, ABI and options from the installed APK. Route A: internal controller auxiliary `open(Intent)`. Route B: direct controller method and dynamically resolved options constructor.
- The live mapping is published inside the existing **fingerprint-bound, verified-slot** dynamic profile as `mediaStartupLab`, `mode=MANUAL_ONLY`. Runtime validates these method signatures by reflection before installing any experimental hook.
- One explicit user request per projection session. Both routes use the same `SHOW_MEDIA_PLAYBACK` Intent as the successful PF4, but with the full selected media `ComponentName` observed from AA rather than a hardcoded Spotify service.
- Preferred route is an LSPosed remote preference, default **A**. It may be changed at any time; no automatic fallback or double-launch is attempted.
- While the PF6.1 lab is armed, the legacy PF2/PF3/PF4 media hooks are not installed. PF4 auto preference is turned off when arming the lab. Disarm and restart AA to revert to verified PF4 when needed. Non-media flag overrides are unaffected.
- All new hooks are limited to AA `:projection`, retain original behavior through `chain.proceed()`, and are disabled on ABI or fingerprint mismatch.

## Parked physical test
1. Install `1.0.0-beta5-preflight6-1` versionCode 175; open Diagnostics → Media Startup → PF6.1 Resolve and arm dynamic routes.
2. Confirm proof report includes all eight roles. Restart Android Auto to install the four experimental observation hooks.
3. Connect to vehicle with Spotify already selected as media app, and Maps visible; choose **A · Auxiliary** and tap PF6.1 Test once.
4. Record visual outcome and `MEDIA_PF6_*` events. Reconnect, choose **B · Direct**, test once. No event alone proves HU visibility.
5. Disarm, restart AA and verify the PF4 legacy path still works when explicitly re-enabled.
6. Try an older installed AA base only if practical; matching static proof alone does not prove runtime behavior or media Intent semantics.

Do not merge/tag/release. PF6.2 will cover dynamic automatic startup and session ActivityTracker discovery.
