# Resolver B3 — secondary cache and runtime lineage, alpha17

## Implemented

- Added compact boolean field lineage after a proven getter/cache path.
- Added control-dependency tracking for bytecode that selects a constant through branches instead of an explicit boolean OR.
- Added `STATIC_STARTUP` when the proven reverse-call path reaches a class initializer.
- Added `RUNTIME_LATCH` and `NEXT_LIFECYCLE_START` for short field-to-field boolean lifecycle gates.
- Exposed field lineage, latch targets and lineage consumer methods in diagnostics.
- Kept obfuscated 17.8 names exclusively in temporary/reference analysis; production discovery uses literals, signatures, call edges, field types and bytecode flow.

## Messaging oracle

Both `Messaging__remove_sms_stream_item_path` and `Messaging__remove_im_stream_item_path` now recover `LIVE`, `INSTANCE_CACHE`, `STATIC_STARTUP` and `RUNTIME_LATCH`.

## Corrected alpha16 result

The former instance-cache classification for `FrameRateRestrictions__thermal_headroom_throttling_enabled` was a generic object-return collision. The stricter provenance keeps its proven live path and no longer attributes unrelated fields to it.

## Memory boundary

Lineage is retained only for short methods that read and write boolean fields. Empty lineage is represented as `null`, and static-startup evidence is derived from the existing reverse graph instead of materializing every call in every class initializer.

## Validation

- 46 unit and fixture tests passed.
- The complete installed 17.8.663814 scan passed in a separately constrained 256 MiB test worker.
- `assembleDebug` passed for version 116 / `1.0.0-alpha17`.
- APK Signature Scheme v2 verified.
- APK SHA-256: `89669d9a93cda1afb9dbd6985ebc9450b498017644333b90d5b8211f0eb6211d`.
