# AA Experiments visual polish — alpha21

## Identity and color

- Added a `Color theme` preference with `AA Experiments` and `System colors` choices.
- `AA Experiments` is the default and uses the green identity derived from the launcher icon in light and dark mode.
- `System colors` explicitly opts into Android dynamic colors on supported devices.
- True remains green, False remains blue and Default remains neutral. Red is reserved for errors, rejected mappings and incompatible states.

## Experiments

- Reduced row height and vertical spacing for the large editable catalog.
- Gave long technical identifiers the full primary text width and kept them readable across two lines.
- Replaced the previous metadata chip group with a compact `type · resolution · consumer` subtitle.
- Moved the modified marker and override state into a stable secondary row.
- Preserved the full-screen details destination, explicit tri-state editor, filter bottom sheet, accordions and lazy evidence loading.
- Reduced the search surface to a compact 54 dp control and tightened the surrounding header.

## Status

- Replaced the dominant filled Android Auto card with a neutral container and green success accents.
- Reduced icon, padding and supporting-text weight while preserving version, LSPosed and scope information.
- Shortened the runtime summary to `Ready — waiting for Android Auto` and moved the longer API explanation to Diagnostics.

## Settings and About

- Added the branded/system color selector without changing the existing brightness selector.
- Reduced excess top spacing.
- Added the real launcher icon to the About row and updated the visible alpha version.

## Compatibility

- Resolver, catalog, profile publication and LSPosed runtime behavior are unchanged from alpha20.
- Existing brightness and override preferences remain compatible.
- Existing installations without a color preference automatically receive the branded green theme.

## Validation

- The complete unit and fixture suite passed.
- The installed Android Auto 17.8.663814 scan passed in a separately constrained 256 MiB worker.
- `assembleDebug` passed for version 120 / `1.0.0-alpha21`.
- APK Signature Scheme v2 verified.
- APK SHA-256: `5432afb33a346d7a6448951fa3959ee9fa9c4b2a1e74332881f07d3f69f8ff33`.

Physical-device visual review remains necessary for final density and text-wrapping assessment on the target Samsung display.
