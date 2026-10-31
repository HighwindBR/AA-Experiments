# Media Startup

Media Startup can open the media app already selected in Android Auto when
projection starts. It may not start audio playback, launch arbitrary packages
or bypass driving restrictions. Configure it while parked and outside active
projection; verify the result on a parked head unit.

This guide describes the current maintenance branch. Historical beta5 preflight
and release evidence is indexed in [Documentation](README.md).

## Set up automatic opening

1. Enable **AA Experiments** in LSPosed with Android Auto in scope, then let the
   manager scan the installed Android Auto build.
2. Open **Settings → Media → Media startup** and select
   **Check compatibility & enable opening**.
3. For a previously validated build with the required structural proof, setup
   can enable automatic opening after successfully publishing the profile.
   On an unreviewed build, successful compatibility proof leaves automatic
   opening off until the separate explicit confirmation is accepted.
4. Read the resulting status. If only manual operation is supported, or the
   profile was saved but not published, automatic opening is unavailable.
5. Restart Android Auto as instructed, select the desired media app in Android
   Auto, and test the next connection while parked.

Automatic consent belongs to the exact installed build fingerprint, including
splits. An Android Auto update requires another scan, compatibility check and
opt-in. A normal flag-override backup does not transfer this consent.

## Routes and advanced controls

**Advanced options and tests** contains route selection, compatibility recheck,
disarm and manual test controls.

- **A — Auxiliary:** invoke a structurally proven one-Intent helper.
- **B — Direct:** invoke a structurally proven Intent/options method.

One proven route is sufficient. When both are available, the saved preference
is retained; when only one is available, it is selected and the unavailable
button is disabled. A failed dispatch is not retried on the other route.
Manual and automatic attempts share a one-attempt budget for the connection.

Automatic operation additionally requires a uniquely proven projected-activity
trigger and navigation observer. Session changes, navigation and changed
selected components can suppress an attempt.

**Turn off automatic opening** disables future queued attempts through the live
preference. **Disable dynamic routes** removes the saved dynamic setup and
publishes the change; follow the restart instruction to unload existing hooks.
Check any save/publication failure message before assuming a change reached runtime.

The legacy Spotify-only controls are separate, exact-build diagnostic paths.
They are not a universal fallback for unsupported builds.

## Interpret the result

A saved configuration is not a published profile. A published profile is not
proof of hook installation. A returned launch invocation is not proof that the
head unit displayed the player. Use LSPosed events and direct observation for
physical acceptance.

Known-build evidence covers particular tested combinations, not every route,
media player, Android Auto version or head unit. Current candidate checks are
listed in [readiness](BETA6_RC1_READINESS.md).

## Backup scope

Complete override recovery includes normal, Advanced and Experimental values
and baselines. Media Startup route preferences and consent are excluded.
Keep this setup separate from flag backup/restore.
