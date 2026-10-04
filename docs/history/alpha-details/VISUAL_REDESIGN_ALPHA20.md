# AA Experiments visual redesign completion — alpha20

## Experiments

- Replaced the editor dialog with a dedicated full-screen experiment details destination.
- Removed the bottom navigation while details are open and added system/back-button handling.
- Rebuilt compact rows around horizontally scrollable metadata chips for type, resolution, modification and consumer state.
- Kept Boolean overrides explicitly tri-state: `default`, `False`, `True`.
- Added responsive two-column value rows with ellipsis for unusually long values.
- Converted Evidence, Raw identifiers and Advanced information into collapsible sections.
- Loads full method/consumer evidence only when the Evidence section is opened.
- Uses monospace typography for raw identifiers and reverse-engineering descriptors.

## Filters and catalog

- Replaced experiment and catalog filter dialogs with Material 3 modal bottom sheets.
- Added a short explanation to every experiment toggle.
- Retained database-backed complete-catalog searching, pagination and export.
- Integrated the catalog as a Diagnostics subpage with an explicit page header and back navigation.

## Status

- Added a clear title/subtitle hierarchy.
- Converted LSPosed and scope into compact environment status containers.
- Added a prominent runtime readiness banner with explicit text and icon state.
- Reduced repeated card weight and used container levels to separate primary and secondary information.

## Settings, Diagnostics and About

- Reorganized Settings with stronger section labels and compact data/tool rows.
- Added a dedicated About screen with project identity, package, runtime API and independence notice.
- Refined Diagnostics with real expand/collapse icons, copy actions and monospace presentation for paths and hashes.
- Preserved the complete catalog below Diagnostics instead of exposing it as a primary navigation tab.

## Accessibility and responsive behavior

- State is always communicated with text, never color alone.
- True remains green, False remains blue and Default remains neutral for color-vision accessibility.
- Interactive rows and icon buttons retain Material minimum touch targets.
- Back icons use the auto-mirrored variant for right-to-left locales.
- Long metadata uses scrolling, wrapping or ellipsis instead of forcing controls beyond the screen width.
- Dynamic system colors remain supported, with the AA green fallback schemes retained.

## Validation

- All 53 unit and fixture tests passed.
- The full installed Android Auto 17.8.663814 scan passed in a separately constrained 256 MiB worker.
- `assembleDebug` passed for version 119 / `1.0.0-alpha20`.
- APK Signature Scheme v2 verified.
- APK SHA-256: `2b6feaac3b4d34f5e089bcd8744efd8246db218f1ab417f9a96d109998f91003`.

Physical-device visual review is still required because this environment does not render the manager on the user's exact Samsung display configuration.
