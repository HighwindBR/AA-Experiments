# Reviewed semantics and evidence UI alpha08

## Result

Alpha08 makes the alpha07 consumer graph inspectable and separates automatic evidence from manually reviewed behavior.

## UI

- Every editable identifier has a **View evidence** action.
- The dialog shows runtime-consumer status, all retained consumer methods, DEX origin, link kind, depth and each step in the descriptor path.
- Evidence can be copied as plain text.
- The catalog can be filtered to reviewed or dormant entries.
- A dormant result explicitly warns that reflection, native code, generated dispatch or deeper paths may still consume the value.

## Build-safe review registry

Reviewed descriptions are keyed by the complete base APK SHA-256. They are never transferred automatically to another Android Auto build. The 17.8.663814 review set contains behavior confirmed by static inspection and/or the user's device tests:

- `UxPrototype__enabled`
- `UxPrototype__url`
- `Coolwalk__dashboard_show_3p_notifications_with_actions_enabled`
- `Coolwalk__use_phone_primary_color`
- `HeroFeature__use_new_media_ui`

`Coolwalk__use_light_dark_theme` remains documented in the registry for the audited port, but it is absent from the stable 17.8 catalog and therefore is not labeled in that inventory.

The registry stores the effective `true` and `false` meaning when proven, the evidence note and the exact audited hash. Gemini/Kitt, Cielo, LauncherShortcuts, ACE/Magic Cue, CoreMaps and parked-video gates retain automatic consumer evidence but are not labeled semantically until their branch behavior is audited.

## 17.8.663814 fixture

- 54,384 identifiers.
- 954 editable.
- 937 with a consumer or reviewed diagnostic path.
- 18 editable entries with no bounded static consumer found.
- 5 semantics-reviewed entries present in this build.
- 17,219 retained consumer paths.
- 0 discovery errors.

The total `PRESENT` count includes the read-only reviewed `UxPrototype__url`, so it is one greater than the count limited to editable entries.

## Storage and migration

- SQLite schema 4 persists metadata and reviewed evidence instead of reconstructing it only in memory.
- Resolver schema 4 triggers a one-time rescan of an existing catalog.
- Remote overrides are stored separately and remain intact.
