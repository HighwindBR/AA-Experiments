# AA Experiments 1.0.0-beta3

This beta improves custom-value feedback, fixed-height resolver progress, and suspended-override auditing while retaining the installed package ID and existing override keys.

## Changes

- Category filters were returned to the Filters sheet and disappear when no entries exist.
- Added/Removed/Changed open actual editable-catalog build comparisons with previous/current details.
- Suspended overrides can be hidden from All until the Android Auto build changes, without deleting or activating them; they remain accessible through Suspended and the audit.
- Code packages and namespace now match io.github.highwindbr.aaxp.
- Feature Catalog updated to cover Android Auto 17.8, 17.9 and 18.0 daily.
- LONG, STRING and DOUBLE Apply feedback clears focus and briefly confirms a successful save, while errors retain the editable field.
- CUSTOM rows use a Lab Green chip and left-side indicator.
- The resolver dialog reserves three lines and breaks long identifiers at underscores.
- Suspended overrides are visibly distinct, searchable and removable after confirmation; flags are not force-reapplied.

## Validation

Release build, unit tests, private fixture gates and a real-device smoke test must pass before publication.

## Known limitations

The app may display a SHA-based identifier for older suspended overrides if no historical flag name can be recovered. Keep the device awake during long scans.

AA Experiments is GPL-3.0-or-later software. See the root LICENSE and THIRD_PARTY_NOTICES.md.
