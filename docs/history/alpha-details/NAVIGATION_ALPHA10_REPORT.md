# Navigation and settings alpha10

## Changes

- Bottom navigation reduced to **Status**, **Editable** and **Settings**.
- Diagnostics moved into Settings; Full catalog is nested under Diagnostics.
- Fixed the Diagnostics crash caused by calling `single()` on getter lists intentionally omitted from alpha09 lightweight rows.
- Diagnostics now uses stored summary data and never assumes details are preloaded.
- Editable and Catalog filters are hidden behind a search-field filter icon.
- Editable type filter exposes Boolean, Integer, Long, Float and Text/String plus All.
- Full catalog supports **Load 500 more**, preserving complete-database search and resetting naturally when query/filter state changes.
- Override backup/import moved to Settings and uses Android JSON files rather than the clipboard.
- Restore-all moved to Settings.
- Added System, Light and Dark manager themes, persisted locally.
- Added About credit: **Developed by HighwindBR**.

## Validation boundary

- Complete unit/fixture suite passed.
- `assembleDebug` passed for version 109 / `1.0.0-alpha10`.
- Navigation, document picker, theme persistence and long-list behavior still require physical-device UI validation.
