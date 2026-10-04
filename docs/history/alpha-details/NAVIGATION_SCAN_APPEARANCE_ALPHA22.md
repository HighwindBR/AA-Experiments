# AA Experiments alpha22 — navigation, scan progress and Appearance

## Navigation and retained state

- Added an explicit back hierarchy shared by the system back action and in-app back buttons.
- Experiment details return to the same Experiments query, filters and exact lazy-list position.
- Catalog returns to Diagnostics; Diagnostics and About return to Settings; Experiments and Settings return to Status; only Status exits.
- Primary tab, Settings subpage, experiment key, query and filters use saveable state so Activity recreation does not reset them.
- Added unit regressions for every back transition.

## Real scan progress

- A modal progress surface is shown only when a catalog must actually be built: imported archives, unseen APK hashes or resolver-schema changes.
- Cached catalogs load without the long-running progress surface.
- Progress is emitted by real work stages: archive preparation, DEX reading, identifier resolution, classification, resources, catalog persistence and editable-list loading.
- DEX and database stages expose real current/total counters; inherently unbounded stages remain indeterminate rather than displaying a fabricated percentage.
- Scan errors dismiss progress and continue through the existing Status error path.

## Appearance preferences

- Replaced permanent segmented controls with two compact Material preference rows: `Theme` and `Accent color`.
- Theme choices are `Follow system`, `Light` and `Dark`.
- Accent choices are `Lab Green` and `Material You`; Lab Green remains the clean-install default.
- Theme and palette persist independently and apply immediately.
- Android versions without dynamic color temporarily render Lab Green without rewriting a saved Material You preference.
- Legacy `theme` and `colors` values are read as migration fallbacks; new writes use `theme_mode` and `accent_palette`.
- Each row opens a viewport-safe popup near the actual touch point. The current option uses a tonal container and leading check, and selection immediately applies, persists and dismisses the popup.
- Rows and popup items retain minimum touch targets and expose title, current value, action and selection state to accessibility services.

## Visual polish

- Standardized TRUE/FALSE/DEFAULT pills to 92 dp.
- Added display-only soft-break opportunities after underscores and camel-case boundaries without changing stored identifiers.
- Capitalized visible `Default` labels.
- Added status-bar-aware height and padding to Experiment details.
- Reduced the fixed details explanation and compacted monospaced Evidence descriptors.
- Replaced the filter type dropdown with compact horizontal chips.
- Reduced the Android Auto Status card and integrated scan differences into Resolver & runtime.
- The Status summary now reads `Identifiers: y editable · z modified`; the total discovery count remains available in Diagnostics.
- Previous-scan added/removed/changed counts now compare only the editable sets and mappings.

## Validation

- All 59 unit and fixture tests passed.
- The installed Android Auto 17.8.663814 scan passed in a separately constrained 256 MiB worker.
- `assembleDebug` passed for version 121 / `1.0.0-alpha22`.
- APK Signature Scheme v2 verified.
- APK SHA-256: `6c7fc38e71de432e9c6d0464d1f3d2c5683ad17182ca45448e7a52484362a8e7`.

Physical-device validation remains required for popup placement, font scaling, back gestures and Activity recreation on the target Samsung configuration.
