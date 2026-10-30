# AA Experiments beta5 RC1 — validation and acceptance

**Status:** source candidate only; not yet approved for publication. Branch
`feature/beta5-rc1`, based on the physically accepted PF6.2 (AA 18.1
Chevrolet head unit), Android app versionCode **177**.

## Settings layout

- Media startup is available directly from **Settings → Media** and no longer
  requires navigating through Diagnostics or developer tools.
- The dedicated Media startup page opens with the selected-media controls
  expanded. Compatibility reports, manual tests and pinned legacy PF3/PF4
  remain behind an optional technical-tools expander on this page.
- Confirm Settings → Media navigation and system Back return to Settings;
  verify status/route labels after turning auto ON/OFF and switching routes.

## Design boundary

- Dynamic A (auxiliary) and B (direct) launch the **component observed from
  Android Auto's selected-media callback**, not an arbitrary user-specified
  package or activity. Both component package and class must still match the
  live session on dispatch. No other apps are targeted.
- Runtime must prove the eight controller/lifecycle/media roles before manual
  routes are available; the independent ninth first-activity role is mandatory
  for automatic launch. Missing/ambiguous/ABI-incompatible roles fail closed.
- Exact APK base SHA-256 and full build fingerprint are required. No cross-build
  profile or opt-in is reused implicitly. Resolving/arming clears auto opt-in.
- Auto is independently OFF by default, with explicit approval per build. RC1
  introduces a distinct selected-media opt-in key and saved preference namespace;
  PF6.2 Spotify-only consent is neither inherited nor sufficient. It
  waits 2800 ms after first activity, requires readiness within 12 seconds,
  permits one attempt per session, cancels on user/manual navigation or
  disconnect, never retries another route, and never starts playback.
- PF4 remains a separately switchable, pinned Spotify-only historical fallback
  when dynamic hooks are disarmed. Disarming takes effect on the next AA restart.
- The Android Auto controller makes the final decision. A returned method is
  **not evidence** that the media UI appeared on the head unit.

## Required CI gates

- [ ] `gradle --no-daemon --max-workers=1 :app:testDebugUnitTest`
- [ ] `gradle --no-daemon --max-workers=1 :app:lintRelease`
- [ ] `gradle --no-daemon --max-workers=1 :app:assembleDebug`
- [ ] Offline Python workflow-guard tests.
- [ ] Verify dynamic and legacy preference keys remain separated.
- [ ] Verify the 17.8 frozen editable cohort and existing beta4 regressions.

A green build does not replace hands-on vehicle validation.

## Parked physical acceptance: AA 18.1

- [ ] Open app after updating beta4 → RC1: confirm previous normal and Advanced
  override records, LSPosed scope and recovery behavior are unchanged.
- [ ] Resolve current installed AA, verify all nine roles and profile publication,
  restart Android Auto, and observe `MEDIA_PF6_LAB_ARMED` and
  `MEDIA_PF6_AUTO_HOOKS_READY`.
- [ ] With Spotify selected: A auto on fresh connection, B auto on another fresh
  connection. Verify exactly one visible app switch, with no playback started.
- [ ] With **another compatible media app** (e.g. YouTube Music) selected inside
  Android Auto: test A and B independently on fresh sessions. Success requires
  actual head-unit UI visibility, not only `MEDIA_PF6_AUTO_DISPATCHED`.
- [ ] Negative tests: auto OFF, no selected media component, media change to
  null, manually opened navigation, second projected activity before settle,
  rapid disconnect/reconnect, delayed callback from previous session,
  invalid preferred route, manual attempt before auto, and repeated requests.
- [ ] Confirm route selection persistence across restart, and that per-build
  auto opt-in cannot be carried into a different build.
- [ ] Disarm, restart AA, verify pinned PF4 Spotify-only fallback. No dynamic
  or legacy hook set should run together.
- [ ] Confirm override flags, UI navigation, backup/recovery, and normal
  Android Auto media selection are unaffected. Save LSPosed logs and visual
  vehicle outcome for failed attempts.

## Cross-version resilience: evidence, not backward compatibility promises

- [ ] Static resolver uniqueness checks against archived AA 14.2 and 15.0
  plus available 17.x, 18.0 and 18.1 APKs; **archive bytes and SHA-256 evidence
  must be supplied separately** and are not in the public CI fixtures.
- [ ] A second independently installed Android Auto build passes all nine
  semantic roles and real A/B launch on a stationary head unit. Otherwise limit
  physical compatibility claims to the confirmed 18.1 build.
- [ ] Negative control: intentionally ambiguous/missing ninth role must leave
  manual routes available where verified, with automatic startup disabled.

## Release gate

The private signing workflow now allows ONLY the exact RC1 feature ref
`feature/beta5-rc1` with `1.0.0-beta5-rc1` / versionCode 177, and compares
the signing certificate against the verified published beta4 APK. This gated,
manual workflow was **not invoked** by preparing the branch.

After public CI passes, the maintainer may manually invoke the private
fixture/signing workflow on the exact RC1 branch to build a signed **test**
candidate. Keep it locally or in an unpublished draft in the separate private
delivery repository; verify the APK signature, version and SHA-256 before
installing it for the parked physical tests above. A signed RC1 **is not**
a public release.

After the physical tests, update/rollback checks and any necessary fixes pass,
finalize release notes and separately request authorization to merge, tag and
publish. Do not expose signing secrets or release-signed APKs as public Actions
artifacts.
