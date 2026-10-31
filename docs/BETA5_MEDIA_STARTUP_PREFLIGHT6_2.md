# AA Experiments beta5 — Preflight 6.2

Based on physically confirmed **both** PF6.1 dynamic launch routes (A auxiliary, B direct controller) on Android Auto 18.1 in the Chevrolet head unit. Branch is a draft, not a release.

## Discovery and execution
- PF6.2 adds the first projected activity `Runnable.run()` as a ninth semantic role. Identify it by the invoked controller **factory** and `Intent.getComponent`, `Intent.setComponent`, `ComponentName.getPackageName`, `SystemClock.elapsedRealtime`, plus `Runnable` ABI. Do not require an obfuscated class name or exact opcode size.
- Existing eight PF6.1 manual roles remain supported independently. If tracker is ambiguous or missing, **automatic startup is unavailable**, but manual route tests remain possible. Any active lab profile is linked to current APK base SHA and full build fingerprint.
- PF6.2 installs two further read-only interception hooks for the resolved tracker and direct launch route. Navigation suppresses automatic focus changes. Any required hook installation failure disables dynamic auto while allowing the manual path where safe.
- Dynamic auto is **OFF by default per AA build**. Opt-in stored in `diagnostics.media_startup.pf6_auto_enabled`, separate from PF4's pinned auto setting; preference restored/synchronized by build fingerprint.
- After readiness and the first projected activity, wait PF4's 2800ms; launch once only if Spotify selected, no manual/user navigation, still in same session, within 12s, and preference enabled both at readiness and at dispatch. Resolved preferred route A or B chosen from per-build preference; no automatic fallback or duplicate launches.
- PF6.1 manual request and PF6.2 automatic startup share one launch budget. The internal Android Auto controller enforces all existing vehicle and user restrictions; no audio autoplay.
- PF4 code remains unchanged. Arming PF6.2 explicitly disables PF4 and resets PF6.2 auto to OFF. Disarming disables pending auto and requires restarting Android Auto to unload the dynamic hooks.

## Physical test gates
1. Install debug versionCode 176, enable LSPosed module in Android Auto, open Diagnostics → Media Startup, tap **PF6.2 Resolve and arm dynamic routes**, confirm `activity.firstProjected` appears in semantic report, restart AA.
2. Confirm `MEDIA_PF6_LAB_ARMED` + `MEDIA_PF6_AUTO_HOOKS_READY` in LSPosed logs. If `MEDIA_PF6_AUTO_UNAVAILABLE`, collect report; do NOT enable auto.
3. With vehicle parked and Spotify already selected, explicitly toggle PF6.2 auto on; reconnect AA. Observe exactly one visible Spotify auto-open after first projected activity, record `MEDIA_PF6_AUTO_SESSION`, `MEDIA_PF6_AUTO_SCHEDULED`, `MEDIA_PF6_AUTO_ATTEMPT`, `MEDIA_PF6_AUTO_DISPATCHED`.
4. Disconnect/reconnect; test switching preferred route A↔B and ensure each works in a fresh session.
5. Negative tests: auto OFF, media other than Spotify, manual route test preceding auto, user navigation before settle, disconnect before settle, rapid reconnect, delayed command from prior session, unresolved tracker (manual-only).
6. Disarm lab, restart AA; optionally reenable and test PF4. No claim of cross-build physical compatibility until a second AA build is exercised.

## Automated gates
`testDebugUnitTest`, `lintRelease`, `assembleDebug`. Matrix of old archived Android Auto APKs should be measured independently to ensure the new structural matcher is unique. CI does not include private APK archives.
