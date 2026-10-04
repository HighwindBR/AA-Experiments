# Resolver B1 — domain model, occurrences and regression oracles

## Implemented

- Literal classification is now recorded per occurrence, including instruction offset, local role, entity kind and classification confidence. Equal text in different bytecode locations is no longer forced to have one local role.
- Runtime/hook type remains available as `DiscoveredType`, while `StorageValueType` and `SemanticValueType` model physical representation and meaning independently.
- Numeric enum-backed values remain `INT` or `LONG` in storage. They are marked `ENUM_BACKED_INT` or `ENUM_BACKED_LONG` semantically and stay read-only until their domain is proven.
- Entity kinds now distinguish Java enum literals, protobuf enums, enum-backed numerics and capability names.
- Confidence is multidimensional: identity, storage type, semantic type, default, getter, runtime consumer and semantics no longer borrow certainty from each other.
- Inventory JSON moved to schema 3 / normalized encoding v2, with backward reading of schema 2.
- SQLite moved to schema 6 and persists storage type, semantic type, evidence confidence and semantics provenance.
- JSON/CSV exports and item details expose storage and semantic types separately.
- Exact-build manual semantics are isolated in `ManualReferenceLayer`. They may annotate a result, but cannot select a getter, upgrade mapping resolution or enable editing.
- Human-audited examples from the reports are stored only in the test source set as `ResolverRegressionOracles`.

## Regression oracles currently represented

- `CIELO_DASHBOARD`: Java enum literal, catalog only.
- `NeoplanFeature__enabled`: `LONG` storage, not a Boolean merely because its name ends in `enabled`.
- `AceFeature__mode`: `LONG` storage with unresolved enum-backed semantics; not editable.
- `BROWSE`: route-state expectation, while the occurrence model permits other roles for the same literal elsewhere.
- Cross-version method remap references for `CieloFeature__earth_enabled` and the known subgraph-wrapper false-positive are test/reference data only.

## Deliberately deferred to B2

- CFG/basic blocks, phi-like merge handling and complete def-use proof.
- Field-to-return proof for getter candidates.
- Worklist consumer traversal without the current fixed depth.
- SCC handling, decision regions and polarity auditing.
- Subgraph fingerprints and global one-to-one matching.

These omissions mean B1 improves the truthfulness and durability of the data model, but does not claim that the current structural resolver can already solve every oracle.

## Validation

- B1 model, semantic classifier, normalized inventory, consumer graph and 16.9/17.8 fixture regression tests pass together.
- A first full-suite run reached the memory ceiling in the pre-existing three-fixture export test after executing the additional fixture scan. The duplicate B1 fixture scan was subsequently removed and its assertions were folded into the existing fixture regression pass.
