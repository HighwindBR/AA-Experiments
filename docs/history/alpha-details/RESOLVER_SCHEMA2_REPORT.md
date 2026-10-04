# Resolver schema 2 report

## Scope

Schema 2 replaces the legacy nearby-field window with intraprocedural register provenance for static registration code:

`configuration literal -> factory arguments -> move-result -> sput field -> zero-argument field reader`

It does not yet traverse interface/wrapper callers or prove runtime consumer branches.

## Android Auto 17.8.663814

- Base APK SHA-256: `676205e71a75f38e0565b9be7d462c296b14490cef9a3feda3791098e5baae04`
- Identifiers: 54,384
- Editable mappings: 954
- Direct resolutions: 2,431
- Structural resolutions: 252
- Ambiguous: 221

The schema-1/shadow comparison applies the current semantic and safety policy to both algorithms so that it isolates mapping behavior:

- legacy proximity editable: 880
- schema 2 editable: 954
- confirmed by both: 871
- promoted by proven data flow: 83
- demoted after removing proximity: 9

The exact lists are stored in `outputs/discovery/resolver-schema1-to-2.json`.

## Confirmed priority cases

| Key | Type | Default | Result |
|---|---|---:|---|
| `NeoplanFeature__enabled` | LONG | 0 | Structural, editable |
| `AceFeature__actionable_insight_enabled` | BOOLEAN | false | Structural, editable |
| `AceFeature__fake_insight_generation` | BOOLEAN | false | Structural, editable |
| `AceFeature__maximum_ace_wait_time` | LONG | 10000 | Structural, editable |
| `LauncherShortcuts__enabled` | BOOLEAN | false | Structural, editable |
| `LauncherShortcuts__assistant_shortcut_enabled` | BOOLEAN | false | Structural, editable |
| `LauncherShortcuts__max_shortcuts` | LONG | 32 | Structural, editable |
| `GearSnacks__immersive_enabled` | BOOLEAN | false | Structural, editable |
| `ProjectedAppsFeature__enabled` | BOOLEAN | false | Structural, editable |
| `DeepLink__enabled` | BOOLEAN | false | Structural, editable |
| `UnifiedSmsMessagingInfo__enabled` | BOOLEAN | true | Structural, editable |
| `IndependentNightModeFeature__enabled` | BOOLEAN | false | Structural, editable |

## Type and safety corrections

- `NeoplanFeature__enabled` remains LONG rather than BOOLEAN.
- `AceFeature__mode` is structurally resolved but represented as ENUM and blocked with `ENUM_DOMAIN_UNRESOLVED`.
- Numeric defaults are now propagated from the actual registration call. This corrected `AceFeature__maximum_ace_wait_time` to 10000 and `LauncherShortcuts__max_shortcuts` to 32.
- Structural STRING mappings remain read-only because schema 2 does not yet distinguish key and default string argument roles with sufficient confidence.
- `SenderlibCertFeature` mappings remain visible but are blocked by the safety policy.

## Potential false positives prevented

`CIELO_DASHBOARD`, `CIELO`, settings telemetry labels, route labels, intent actions and derived runtime decision states cannot become editable from a nearby scalar return. Nine formerly editable schema-1 mappings were demoted because their field association was not reproduced by register data flow.

## Remaining work

- control-flow joins and interprocedural factory propagation;
- interface implementation and wrapper traversal;
- targeted reverse call graph;
- runtime consumer and branch semantics;
- string/default argument-role inference;
- enum-domain discovery;
- per-stage time and memory telemetry in the on-device worker.
