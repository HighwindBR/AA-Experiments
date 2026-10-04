# AA Experiments alpha26 — source routing and reevaluation

Alpha26 adds a bounded source-routing layer over the proven getter and consumer graph.

## Recorded dimensions

- Value sources: compiled default, Fenotype registry, SharedPreferences, remote capability, system state, derived value and configuration snapshot.
- Source precedence: external/negotiated gates before local stores and compiled defaults when both are structurally present.
- Reevaluation triggers: live read, preference listener, next render, next Activity, next service registration and next projection config.
- Post-getter override risk: indicates that a valid getter override can still be gated or superseded downstream.

The analyzer uses DEX method calls, referenced fields, consumer categories and the existing proven graph. Obfuscated class or method names are not production mappings. Strong semantic claims remain deferred when the graph only proves coexistence.

## UI fixes

- Dark-mode status and navigation bar icon appearance follows the active theme.
- Experiment details header and identifier use explicit theme content colors.
- Long identifiers gain visual break opportunities after underscores and camel-case boundaries without changing the stored key.

## Validation

- 67 unit and fixture tests passed.
- The installed-catalog scan passed with a 256 MiB test-worker heap.
- APK Signature Scheme v2 verified.
