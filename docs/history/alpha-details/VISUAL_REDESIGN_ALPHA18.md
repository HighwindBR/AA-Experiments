# AA Experiments visual redesign — alpha18

## Direction

The manager now follows a Material 3 inspector/tuner direction: concise by default, with reverse-engineering evidence one interaction away.

## Identity and navigation

- Renamed the visible product to **AA Experiments**.
- Added the supplied app mark to the top bar, Status and About.
- Replaced placeholder navigation letters with icons and the labels Status, Experiments and Settings.
- Kept the complete Explorer/catalog under Settings → Diagnostics.
- Added a green fallback color system based on the app icon while preserving Android dynamic color on Android 12 and newer.

## Status

- Consolidated installation, LSPosed and scope into a primary Environment card.
- Consolidated APK, inventory and runtime status into a Resolver & runtime card.
- Shortened runtime telemetry wording and moved technical context to Diagnostics.

## Experiments

- Replaced tall inline editors with compact experiment rows.
- Rows expose the key, value type, resolution and unambiguous `DEFAULT`, `FALSE` or `TRUE` state.
- Boolean overrides remain tri-state. A binary switch is never used as an override editor.
- String and numeric inputs moved to the dedicated experiment editor.
- Evidence and advanced metadata moved out of the primary list.
- `TRUE` uses green, `FALSE` uses blue and `DEFAULT` uses a neutral container, always with a text label so state is not communicated by color alone.

## Settings and Diagnostics

- Reorganized Appearance, Data, Tools and About.
- Converted backup/restore actions into compact rows.
- Rebuilt Diagnostics as collapsible Installation, DEX, Resolver, Runtime and Remote preferences sections.
- Added copy actions for long paths and hashes.

## Validation

- 46 unit and fixture tests passed.
- The complete installed 17.8.663814 scan passed with a 256 MiB test heap.
- `assembleDebug` passed for version 117 / `1.0.0-alpha18`.
- APK Signature Scheme v2 verified.
- APK SHA-256: `b77bd909bc3794b32c2522f3b7fca6e25828e4fa2db496279271c750b6b28c69`.
