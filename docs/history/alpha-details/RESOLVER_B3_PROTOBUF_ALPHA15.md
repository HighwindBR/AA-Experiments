# Resolver B3 — Protobuf foundation, alpha15

## Implemented

- Defensive, schema-free Protobuf wire decoding with byte, field and nesting limits.
- Three explicit confidence levels: `PROTOBUF_RAW`, `PROTOBUF_WIRE_PARSED` and `PROTOBUF_SCHEMA_RESOLVED`.
- Optional schema validation checks field numbers, wire types and repeated cardinality; no schema is inferred merely from a successful wire parse.
- Protobuf settings are classified independently from their physical string storage and remain read-only until field semantics are resolved.
- Decoded strings, repeated-field evidence and a compact wire summary are stored in catalog metadata and shown on demand in the evidence dialog.
- Registration analysis separates a technical key from a sibling string default while continuing to suspend conflicting keys or multiple branch-dependent defaults.
- Base64 probing runs only inside methods that also contain a Protobuf-like setting key. It is not applied to every string in the APK.

## Real 17.8.663814 regressions

- `PhoneThemeFeature__manufacturer_prefers_device_font_family` decodes to `samsung`.
- `PhoneThemeFeature__manufacturer_uses_dynamic_icon_shape` decodes to `samsung | google` and exposes a repeated field.
- `CieloFeature__default_widgets_config` and `CieloFeature__featured_widgets_config` expose nested package/provider strings without claiming a recovered schema.
- All four stay non-editable.

The test oracles use stable keys and expected semantic outcomes. Obfuscated class and method names are not production mappings.

## Editable-count change

The stable fixture now contains 940 editable entries. This is an intentional resolver-schema change from 935: registration keys that share a factory call with one string default are no longer rejected solely because two string literals reach the returned registration object. Conflicting keys and multiple scalar/string defaults remain ambiguous. Serialized Protobuf values themselves are never promoted to editable raw strings.

## Still pending in B3

- Java enum versus Protobuf enum and enum-backed numeric-domain recovery.
- Cache, runtime latch, mixed-consumption and activation-scope analysis.
- Derived decisions, source routing/precedence, reevaluation triggers, negotiated snapshots and transition side effects.
- Structured Protobuf editing. This requires a recovered and validated schema plus a safe encoder; wire parsing alone is insufficient.

## UI and branding

- Added the supplied AA Experiment Manager icon to the application manifest.
- Added a dedicated Protobuf evidence card; large catalog rows remain lightweight.

## Validation

- All 44 unit and fixture tests passed.
- The complete installed 17.8.663814 catalog passed in a separately constrained 256 MiB test worker with 940 editable entries and no scanner errors.
- `assembleDebug` passed for version 114 / `1.0.0-alpha15`.
- APK Signature Scheme v2 verified.
- APK SHA-256: `35396c61ee0f2d6f7df0598b96a1c73d3484daa4039d9072957d83678110b831`.
