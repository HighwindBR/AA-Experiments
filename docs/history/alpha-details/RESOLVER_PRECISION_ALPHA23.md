# AA Experiments alpha23 — resolver precision and catalog hygiene

Alpha23 tightens the boundary between a real Android Auto configuration and a literal that only happens to resemble one. No build-specific obfuscated class or method name is used by the production resolver.

## Positional registration arguments

Factory calls are decoded by their declared parameter order, receiver and DEX register width. Registry IDs and compiled defaults are stored independently. Empty strings remain valid defaults.

The stable 17.8.663814 regression oracle now proves:

- `CieloFeature__earth_status`: registry ID `8`, compiled default `""`;
- `CieloFeature__earth_tilt`: registry ID `9`, compiled default `6`.

The positional pass runs only for configuration namespaces so the full scan remains within the manager's 256 MiB process budget.

## Suspicious discoveries

Formatting fragments, representation-only strings, generic library properties and Protobuf guesses without a proven registration-key occurrence are retained as diagnostic evidence but receive:

- `editable = false`;
- `resolution = CATALOG_ONLY`;
- `editabilityReason = SUSPICIOUS_DISCOVERY`;
- a machine-readable `suspiciousReason`.

This is evidence-based demotion, not a build-specific blacklist. The stable fixture now exposes 919 editable mappings instead of 940; the 21 removed entries are textual or library artifacts.

## Consumer semantics

Structural reachability and semantic usefulness are now separate. Consumer evidence is classified as runtime, renderer, activity, configuration snapshot, wrapper-only, dump, logging, diagnostics, telemetry or test code. Wrapper-only and technical paths remain visible in details but do not prove that a feature has a functional runtime consumer.

## Interface polish

- Theme and accent popups use content-sized widths.
- Experiment rows show only value type and modification state; resolution and consumer evidence remain in details.
- Type filters appear only when the current build contains that editable type.
- Reviewed semantics appears only when exact-build reviews exist.
- Dormant was removed from the primary editor filter.

The separate build-specific human review layer remains intentionally deferred.
