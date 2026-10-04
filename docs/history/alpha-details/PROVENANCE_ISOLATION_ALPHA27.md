# AA Experiments alpha27 — provenance isolation and readable diagnostics

## Purpose

Alpha27 corrects the semantic overreach exposed by the alpha26 diagnostics. A value origin, a compiled fallback, a downstream eligibility gate and a transport destination are now independent concepts. Evidence found on unrelated consumer paths is not merged into one conclusion.

## Resolver changes

- `valueOrigin` is derived only from the identifier registry or from reads proven inside its getter.
- `compiledFallback` is reported separately and is never a value source.
- SDK checks, system features and negotiated capabilities are downstream gates, not origins.
- Configuration snapshots are transport destinations, not origins.
- `sourcePrecedence` remains `NOT_PROVEN` unless an explicit same-value fallback/selection flow is proven. Alpha27 deliberately does not infer a global precedence order.
- Cross-flag relationships remain deferred until both flags are proven to control the same decision region and result.
- Generic SharedPreferences listeners do not establish reevaluation. The listener must be associated with the same getter that reads the preference.
- When consumer traversal reaches its budget, the result is marked `INCOMPLETE_CONSUMER_BUDGET`. Cross-path semantics, downstream gates, transport claims, semantic relationships and graph-derived caches are then suspended.
- Activation and reevaluation remain separate dimensions.

## Diagnostic presentation

The experiment details screen now groups the result into:

- Value flow: origin, fallback, downstream gates, transport, derived inputs and precedence.
- Lifecycle: read behavior, reevaluation and activation.
- Resolver confidence: getter, consumer, semantics, analysis state and cross-path confidence.

Internal metadata is still available under a collapsed raw section. Camel-case keys and enum values are rendered as readable labels while the original technical keys remain unchanged in storage and exports. Known acronyms such as SDK, DEX, APK and ID are preserved.

## Schema and cache

- Resolver schema: 13.
- Inventory cache generation: `inventory-r3-v6`.
- Alpha26 catalogs are rebuilt automatically.
- Overrides remain separate and are preserved.

## Regression coverage

The suite verifies:

- `CieloFeature__earth_status`: Fenotype origin, registry ID 8 and empty-string fallback.
- no inferred SharedPreferences origin or cross-family relationship for Earth.
- Launcher Shortcuts relationships remain deferred without decision-region proof.
- configuration snapshots are transport rather than origin.
- capability checks are downstream gates.
- a synthetic preference-backed getter establishes SharedPreferences origin.
- a listener is accepted only when linked to the same preference read.
- consumer-budget exhaustion removes untrusted relationships, gates and cache lineage.
- shared transitive infrastructure does not join unrelated feature families.

## Validation

- 68 unit and fixture tests passed.
- The installed 17.8.663814 regression scan passed.
- The installed scan passed in a test worker limited to 256 MiB.
- Runtime behavior on the physical Android Auto process remains device validation, separate from these static and local tests.
