# Documentation

Current guides describe the source in the branch you are reading. A development
branch can differ from the latest published APK. Use the
[official releases](https://github.com/HighwindBR/AA-Experiments/releases) for
published binaries and version-specific release notes.

## User and maintainer guides

- [AA Experiments](../README.md)
- [Advanced Overrides](ADVANCED_OVERRIDES.md)
- [Media Startup](MEDIA_STARTUP.md)
- [Architecture](ARCHITECTURE.md)
- [Development](DEVELOPMENT.md)
- [Known limitations](KNOWN_LIMITATIONS.md)
- [Release process](RELEASE.md)
- [Private fixture runner handoff](PRIVATE_RUNNER_CHECKLIST.md)
- [LSPosed catalog maintenance](LSPOSED_CATALOG.md)
- [Contributing](../CONTRIBUTING.md)
- [Privacy](../PRIVACY.md)
- [Security](../SECURITY.md)
- [Third-party notices](../THIRD_PARTY_NOTICES.md)

## Current candidate and implementation records

[Candidate readiness](BETA6_RC1_READINESS.md) records automated evidence by
commit/run and keeps pending physical, private-fixture and signing gates explicit.
Implementation records explain changes; they are not release approval.

- [Beta6 — Reliability PF1 (development only)](BETA6_PF1_RELIABILITY.md)
- [Beta6 — PF2: Media Startup UX and legacy diagnostics cleanup](BETA6_PF2_POLISH.md)
- [Beta6 RC1 — release-gate checklist (NOT approved)](BETA6_RC1_READINESS.md)

## Changelog, release notes and distribution

The [changelog](../CHANGELOG.md) contains version history. The main README
contains no beta changelog. Release notes describe their named version.

- [AA Experiments](../distribution/lsposed/README.md)
- [AA Experiments 1.0.0-beta4](../distribution/lsposed/RELEASE_NOTES_169-1.0.0-beta4.md)
- [AA Experiments 1.0.0-beta1](RELEASE_NOTES_1.0.0-beta1.md)
- [AA Experiments 1.0.0-beta2](RELEASE_NOTES_1.0.0-beta2.md)
- [AA Experiments 1.0.0-beta3](RELEASE_NOTES_1.0.0-beta3.md)
- [AA Experiments 1.0.0-beta4](RELEASE_NOTES_1.0.0-beta4.md)
- [AA Experiments 1.0.0-beta5](RELEASE_NOTES_1.0.0-beta5.md)

## Historical beta research and acceptance

The records below preserve original screen locations, experimental paths,
checklists and evidence. Pending statements describe that stage; a historical
passing test does not certify a later candidate. Use the current guides above
for installation and operation.

- [Advanced Overrides Mode (beta4)](ADVANCED_OVERRIDES_BETA4.md)
- [Frozen Android Auto 17.8 editable delta — beta3 → beta4](BETA4_17_8_EDITABLE_DELTA.md)
- [AA Experiments beta4 — Override mechanism coverage audit](BETA4_MECHANISM_AUDIT.md)
- [AA Experiments — Deep pre-RC code and UI review](BETA4_PRE_RC_DEEP_REVIEW.md)
- [Beta4 — RC readiness and Round D sign-off](BETA4_RC_READINESS.md)
- [Beta4 — Round A reliability implementation](BETA4_ROUND_A_IMPLEMENTATION.md)
- [Beta4 — Round B UI and accessibility implementation](BETA4_ROUND_B_IMPLEMENTATION.md)
- [Beta4 — Visual QA from user screenshots](BETA4_ROUND_B_VISUAL_QA.md)
- [Beta4 — Round C data, exports and recovery](BETA4_ROUND_C_IMPLEMENTATION.md)
- [Beta4 — signed device candidate, without publication](BETA4_SIGNED_CANDIDATE.md)
- [Beta5 Media Startup: preflight1 read-only investigation](BETA5_MEDIA_STARTUP_PREFLIGHT1.md)
- [Beta5 Media Startup — preflight2: targeted projection observation](BETA5_MEDIA_STARTUP_PREFLIGHT2.md)
- [Beta5 — preflight3: explicitly requested Spotify open proof](BETA5_MEDIA_STARTUP_PREFLIGHT3.md)
- [Beta5 Media Startup — preflight4 (automatic Spotify opening)](BETA5_MEDIA_STARTUP_PREFLIGHT4.md)
- [Beta5 preflight5 — semantic Media Startup resolver, fail-closed](BETA5_MEDIA_STARTUP_PREFLIGHT5.md)
- [AA Experiments beta5 — Preflight 6.1](BETA5_MEDIA_STARTUP_PREFLIGHT6_1.md)
- [AA Experiments beta5 — Preflight 6.2](BETA5_MEDIA_STARTUP_PREFLIGHT6_2.md)
- [AA Experiments beta5 RC1 — validation and acceptance](BETA5_RC1.md)
- [Beta5 final — evidence and publication checklist](BETA5_RELEASE_READINESS.md)
- [Experimental BOOLEAN override — beta4 controlled test](EXPERIMENTAL_BOOLEAN_PREFLIGHT.md)
- [Read-only getter-probe preflight (beta4 development)](GETTER_PROBE_PREFLIGHT.md)
- [Historical release procedures](history/RELEASE_HISTORY.md)

## Historical feature and alpha research

[Feature catalog](../FEATURE_CATALOG.md) documents research on particular Android
Auto versions. It does not feed the resolver or guarantee that an entry remains
editable or effective on an installed build.

- [AA Experiments — Alpha development history](history/ALPHA_HISTORY.md)
- [Alpha13 — 256 MiB scan memory fix](history/alpha-details/ALPHA13_MEMORY_REPORT.md)
- [Alpha04 resolver baseline](history/alpha-details/BASELINE_ALPHA04.md)
- [AA Experiments alpha29 — bounded classification memory](history/alpha-details/BOUNDED_CLASSIFICATION_ALPHA29.md)
- [AA Experiments alpha39 — build and runtime integrity](history/alpha-details/BUILD_INTEGRITY_ALPHA39.md)
- [AA Experiments alpha37 — occurrence-aligned callsites](history/alpha-details/CALLSITE_ALIGNMENT_ALPHA37.md)
- [Catalog and performance alpha09](history/alpha-details/CATALOG_PERFORMANCE_ALPHA09_REPORT.md)
- [AA Experiments alpha35 — CFG-aware value flow and limitation taxonomy](history/alpha-details/CFG_VALUE_FLOW_ALPHA35.md)
- [AA Experiments alpha28 — compatibility rediscovery and directed deep resolve](history/alpha-details/COMPATIBILITY_DEEP_RESOLVE_ALPHA28.md)
- [Consumer graph alpha07](history/alpha-details/CONSUMER_GRAPH_ALPHA07_REPORT.md)
- [AA Experiments alpha30 — bounded deep-resolve batches](history/alpha-details/DEEP_RESOLVE_BATCH_ALPHA30.md)
- [AA Experiments alpha31 — continuous streaming deep resolve](history/alpha-details/DEEP_RESOLVE_STREAMING_ALPHA31.md)
- [AA Experiments alpha36 — exact callsite outcomes](history/alpha-details/EXACT_CALLSITE_ALPHA36.md)
- [AA Experiments alpha38 — instance result origin](history/alpha-details/INSTANCE_RESULT_ORIGIN_ALPHA38.md)
- [Navigation and settings alpha10](history/alpha-details/NAVIGATION_ALPHA10_REPORT.md)
- [AA Experiments alpha22 — navigation, scan progress and Appearance](history/alpha-details/NAVIGATION_SCAN_APPEARANCE_ALPHA22.md)
- [AA Experiments alpha27 — provenance isolation and readable diagnostics](history/alpha-details/PROVENANCE_ISOLATION_ALPHA27.md)
- [AA Experiments alpha40 — reproducible build and resilient analysis](history/alpha-details/REPRODUCIBLE_RELEASE_ALPHA40.md)
- [Resolver B1 — domain model, occurrences and regression oracles](history/alpha-details/RESOLVER_B1_MODEL_AND_ORACLES.md)
- [Resolver B2 — structural engine, alpha12](history/alpha-details/RESOLVER_B2_STRUCTURAL_ENGINE.md)
- [Alpha14: multi-layer subgraph and global matching](history/alpha-details/RESOLVER_B2_SUBGRAPH_ALPHA14.md)
- [Resolver B3 — enums, cache and activation, alpha16](history/alpha-details/RESOLVER_B3_ENUM_CACHE_ALPHA16.md)
- [Resolver B3 — secondary cache and runtime lineage, alpha17](history/alpha-details/RESOLVER_B3_LINEAGE_ALPHA17.md)
- [Resolver B3 — Protobuf foundation, alpha15](history/alpha-details/RESOLVER_B3_PROTOBUF_ALPHA15.md)
- [AA Experiments alpha24 — semantic relationships and eligibility gates](history/alpha-details/RESOLVER_B3_RELATIONS_ALPHA24.md)
- [AA Experiments alpha23 — resolver precision and catalog hygiene](history/alpha-details/RESOLVER_PRECISION_ALPHA23.md)
- [Resolver schema 2 report](history/alpha-details/RESOLVER_SCHEMA2_REPORT.md)
- [Reviewed semantics and evidence UI alpha08](history/alpha-details/REVIEWED_SEMANTICS_ALPHA08_REPORT.md)
- [Runtime observability — alpha19](history/alpha-details/RUNTIME_OBSERVABILITY_ALPHA19.md)
- [AA Experiments alpha33 — semantic corridor deep resolve](history/alpha-details/SEMANTIC_CORRIDOR_ALPHA33.md)
- [Legacy Smali-to-hook matrix — Android Auto 17.8.163744 daily](history/alpha-details/SMALI_TO_HOOK_MATRIX.md)
- [AA Experiments alpha26 — source routing and reevaluation](history/alpha-details/SOURCE_ROUTING_ALPHA26.md)
- [AA Experiments alpha32 — targeted semantic traversal](history/alpha-details/TARGETED_DEEP_RESOLVE_ALPHA32.md)
- [AA Experiments alpha34 — interprocedural value provenance](history/alpha-details/VALUE_PROVENANCE_ALPHA34.md)
- [AA Experiments visual polish — alpha21](history/alpha-details/VISUAL_POLISH_ALPHA21.md)
- [AA Experiments visual redesign — alpha18](history/alpha-details/VISUAL_REDESIGN_ALPHA18.md)
- [AA Experiments visual redesign completion — alpha20](history/alpha-details/VISUAL_REDESIGN_ALPHA20.md)
