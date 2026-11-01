# AA Experiments 1.0.0

Application ID: `io.github.highwindbr.aaxp`. Version: `1.0.0` / versionCode `180`.

AA Experiments is an independent Android manager and LSPosed module for editing
Android Auto experimental flags and enabling supported unreleased or test features.
An editable flag does not guarantee server, player or head-unit support.

## Included in this version

- Installed and imported Android Auto analysis with conservative typed getter resolution.
- Normal and explicitly confirmed Advanced overrides, exact-build runtime profiles,
  suspended values and baseline recovery.
- Optional opening of the Android Auto-selected media app using independently
  verified A/B routes, explicit per-build consent and one attempt per connection.
- Bounded archive import, catalog retention and SQLite migration improvements.
- Foreground analysis and a five-minute partial CPU wake lock for screen-off scans.
- Complete Green Lab light/dark colors, Material You on supported systems, clearer
  override states, scrollable confirmations/filters and accessible appearance menus
  positioned near the touch.
- Backup export error reporting and document writes outside the UI thread.

## Upgrade and limitations

The official APK must preserve the published beta5 signing certificate. Code `180`
is higher than beta5 (`178`) and beta6 preflight (`179`). Keep a complete override
backup before upgrading. A disposable-key debug APK is not an official signed upgrade.

The maintainer reported successful beta6 checks of screen-off scanning, preservation
of data/overrides and automatic media opening in the vehicle. This is device-specific
acceptance, not proof of every Android Auto/player/route/head-unit combination.
Exact automated/signing evidence is tracked in [stable readiness](STABLE_1.0.0_READINESS.md).

Analysis is not a durable background job: force stop, process death, task removal
or OS limits can end it. The wake lock is bounded and is not renewed automatically.
Media Startup requests opening; audio playback/resumption depends on Android Auto
and the player. It does not bypass driving restrictions. Configure only while parked.
See [known limitations](KNOWN_LIMITATIONS.md).

GPL-3.0-or-later. Third-party notices remain included in the source and APK.
The project is not affiliated with Google, Android Auto or LSPosed.
