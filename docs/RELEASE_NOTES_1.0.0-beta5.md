# AA Experiments 1.0.0-beta5

> Application ID: `io.github.highwindbr.aaxp`. Version:
> `1.0.0-beta5` (`versionCode=178`).
> Release asset: `AAExperiments-1.0.0-beta5.apk` and its `.apk.sha256` checksum.

Beta5 introduces optional **automatic media-app opening** when Android Auto
projection starts. The current catalog, normal/Advanced overrides and recovery
tools continue to work independently of this feature.

## What's new

- **Media startup in Settings:** access the new Media startup page directly
  from **Settings → Media**, rather than digging through Diagnostics.
- **Your selected player:** try to open the player that Android Auto already
  has selected (such as Spotify or another compatible media app). It does not
  choose an arbitrary package and it never starts audio playback.
- **Two opening routes:** choose the preferred dynamically resolved route:
  **A — Auxiliary** or **B — Direct**. The module makes a single attempt per
  projection session and does **not** automatically fall back to the other
  route.
- **Conservative compatibility:** validates a build-specific structural
  profile and the observed media component before dispatch. Ambiguous or
  incompatible Android Auto builds are rejected instead of guessing
  obfuscated method names. Automatic startup is disabled if its first-activity
  role cannot be verified.
- **Opt-in and recovery:** automatic opening is OFF by default for every
  Android Auto build. Older Spotify-only experimental consent is not silently
  carried over. The previous pinned PF4 Spotify fallback and manual
  diagnostics remain available in the optional technical tools.
- **Session safeguards:** cancels on disconnect, changed session or
  detected manual navigation; waits briefly for projection to settle and
  avoids repeated launches.

## Setup

1. Install the APK using the same signing identity as previous official
   betas. Root and LSPosed (module API 101) are required; keep Android Auto
   in the module scope.
2. While parked, open **Settings → Media → Media startup**. Resolve the
   installed Android Auto build and restart Android Auto when prompted.
3. Select route A or B, then explicitly enable automatic media opening.
   Select your preferred media app inside Android Auto before the next
   connection. The setting affects subsequent projection sessions.

## Validation and limitations

- The maintainer reported that beta5-RC1 worked during a parked-vehicle
  test on 2026-10-30. Static and runtime safeguards were also covered
  through preflight testing and public unit/lint/debug CI.
- This is **not** a guarantee that every player, route, Android Auto version
  or head unit was independently tested. The user's selection, Android
  Auto's controller, the vehicle interface and other eligibility checks can
  still prevent a visible switch.
- Opening media is **not** audio autoplay and does not bypass driving,
  vehicle or content restrictions. Do not configure or test while driving.
- Existing limitations of scans, full catalog, LSPosed runtime telemetry
  and exact-build backup/restore still apply. See
  [known limitations](KNOWN_LIMITATIONS.md).
- This feature is independent of the normal flag editor. It does not
  unlock every Android Auto/server/vehicle capability.

AA Experiments is independent, GPL-3.0-or-later software. It is not
affiliated with Google, Android Auto or LSPosed.
