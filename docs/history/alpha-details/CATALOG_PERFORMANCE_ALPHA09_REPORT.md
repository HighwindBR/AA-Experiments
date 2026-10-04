# Catalog and performance alpha09

## Implemented

- Editable-list rows are lightweight. Getter and consumer child tables are no longer queried for every one of the 954 cards.
- **View evidence** performs one background details lookup on demand.
- A separate **Catalog** tab searches the complete installed-build database while rendering at most 500 matching rows.
- Search uses a 300 ms debounce and SQL `LIKE` over the complete dataset, so unloaded rows cannot disappear from search results.
- `UNKNOWN` value types are hidden by default and can be included explicitly.
- Combinable filters: kind/source, value type, resolution, runtime consumer status, editable, modified/has override, reviewed semantics and kill-switch name.
- Added a composite SQLite filter index and database schema 5 migration.
- Added JSON and CSV export scopes: editable, modified and all discoveries.
- Export options include defaults, overrides, getters and consumers.
- Method/consumer export uses two bulk joins instead of two queries per identifier.
- Exports are written through Android's system document picker; no storage permission is requested.

## Preserved behavior

- The existing editable screen still loads all editable rows, so its search remains complete.
- Runtime profiles still contain only active overrides.
- Getter details required to publish hooks are loaded in the background from the database.
- Existing remote overrides and scanned inventories survive the schema migration.

## Validation

- The complete unit/fixture suite passed after the lazy-row and SQL-query changes.
- A final Kotlin compilation and `assembleDebug` passed after adding all advanced filters and bulk export.
- Static validation cannot measure Android-device SQLite, Compose rendering or document-picker latency; those remain device checks for alpha09.

## Remaining catalog work

- Device timing instrumentation for initial load, details lookup, filtered search and override commit.
- Optional paging controls or infinite scrolling beyond the first 500 full-catalog matches. Search always operates over the complete database already.
- Effective runtime values remain unavailable because the API 101 hook process has no reverse telemetry channel in this implementation.
