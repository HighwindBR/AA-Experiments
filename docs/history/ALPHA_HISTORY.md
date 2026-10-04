# AA Experiments — Alpha development history

This chronological record preserves alpha01–40 implementation evidence. It is historical and may
describe schemas or behavior superseded by later builds. For current behavior use the canonical
documents in `docs/`.

## Topic index

- Model and oracles: alpha11
- CFG, structural graph and matching: alpha12–14
- Protobuf, enum and cache semantics: alpha15–17
- Product and visual design: alpha18–22
- Resolver precision and provenance: alpha23–27
- Compatibility and directed deep resolve: alpha28–38
- Build identity and release hardening: alpha39–40

Detailed investigations are retained in `alpha-details/`.

## Inputs preserved and used

- Original Android Auto research, Smali trees, scripts and prior reports in the workspace.
- APKM fixtures: 16.9.666314, 17.8.163744 daily, and 17.8.663814 stable.
- The attached LSPosed/API 101 project specification and the compatibility methodology developed in the prior research.

No original research artifact was overwritten or deleted. No installed Android Auto package, phone setting, root state, or device process was touched.

## Implemented

- Standalone Kotlin/Compose manager and Java libxposed entrypoint in one APK.
- Modern API 101 module metadata and Android Auto-only static scope.
- Full base/split DEX analysis for literals and configuration namespaces, scalar return types, defaults, zero-argument getters, registry field associations, instruction/opcode fingerprints, referenced fields, and invoked methods.
- Binary `resources.arsc` catalogue by stable resource name with type, compiled value, configuration count, and version-local ID.
- PackageManager manifest catalogue for components, requested permissions/features/configurations, and application metadata keys.
- Normalized inventory cache keyed by base SHA-256; method fingerprints are stored once to keep each 54k-entry fixture inventory near 22 MB.
- Version diffing for preserved, added, removed, remapped, type/default changes, registry moves, and ambiguity.
- Structural remapping that does not depend on an obfuscated class or method name remaining constant.
- Searchable Advanced catalogue of all findings, editable/read-only filters, scalar editors, system-default restoration, exact-hash backup import/export, and compatibility diagnostics.
- Dynamic API 101 profile generated from the analyzed build. The hook process verifies the installed base hash and exact method descriptor before installing a fail-open hook.
- Typed boolean, byte/short/char/int, long, float/double, and string runtime returns.
- Safety policy that keeps authentication, certificates, integrity, validation, movement, and driver-restriction identifiers visible but excludes them from executable profiles.
- Removal of the original fixed 21-control catalogue, hardcoded 17.8 mapping, and schema-1 preference path. Prior feature keys now appear through discovery like every other supported key.

## Stage 1 performance redesign (alpha03)

- Replaced the operational 22 MB JSON inventory load with an indexed SQLite catalogue keyed by APK hash. The normalized JSON format remains available for research fixtures and compatibility tests.
- Added a one-time migration path from the alpha02 JSON cache, so an already analyzed installed build does not require another DEX scan.
- The manager initially loads 100 editable rows and appends further pages on demand. It no longer places the complete 54k-entry catalogue in Compose state.
- Precomputed each identifier's remote-preference hash while importing the inventory.
- Reads remote preferences once per refresh using `SharedPreferences.all`, then resolves only active overrides through the indexed preference key.
- Generates the LSPosed runtime profile only from active overrides instead of traversing the complete inventory.
- Moved preference commits, profile publication, archive analysis, SQLite work, and APK scanning off the UI thread.
- Added visible `Saving`, `Saved`, and `Save failed` states and replaced historical full-inventory loading with compact SQL change counts.
- Adapters and the final catalogue taxonomy remain intentionally deferred to later stages.

### alpha04 catalogue correction

- Loads all editable mappings from SQLite so catalogue search always covers the complete editable set. The manager still avoids loading the roughly 54k read-only discoveries or decoding the legacy 22 MB JSON inventory at normal startup.
- Removed the 100-item pagination control, which could make valid search results appear absent until their page had been loaded.
- `assembleDebug` passed for version 103 / `1.0.0-alpha04`; the APK is debug-signed with Signature Scheme v2 and has SHA-256 `0e85c63eef19ea89d248b54bdfa3643ce1438a9e0b040c8c2159f7be914f5b1e`.

## Resolver preparation (alpha05)

- Preserved the legacy `namespace` and `confidence` fields while adding independent `kind`, value type, mapping resolution, runtime-consumer status, semantics-review and editability-reason dimensions.
- Added resolver schema versioning to JSON and SQLite. Existing alpha04 databases migrate in place; a stale semantic index is rebuilt from the preserved inventory cache when available, otherwise from the installed APK.
- Added a conservative semantic guard before editability. Enum/route literals, intent actions, telemetry events and derived runtime states are catalogue evidence rather than editable getters, even if a nearby method returns a supported scalar.
- Added mandatory negative regressions for `CIELO_DASHBOARD`, `CIELO`, `SETTINGS_SYSTEM_THEME_MODE`, `SETTINGS_NAVIGATION_THEME_MODE`, `PLAYBACK`, `BROWSE`, `GEMINI_ENABLED_KEY`, `GEARHEAD_PROJECTION_ENABLED` and the media playback intent action.
- Added positive/type regressions for `key_settings_magic_cue_enabled` and `NeoplanFeature__enabled` as a `LONG` Phenotype value.
- Cold fixture analysis of 17.8.663814 retained 884 of the previous 886 editable mappings. `GEARHEAD_PROJECTION_ENABLED` and `MENDEL_FLAG_ENABLED` were suspended as derived runtime decision states pending origin/consumer proof.
- The classifier does not yet prove registration data flow, wrappers, consumers or branch semantics. Those remain the next resolver stage; the two suspended entries are conservative candidates, not yet asserted final false positives.
- 15 unit/regression tests in 9 suites passed. The alpha05 APK is version 104, debug-signed with Signature Scheme v2, SHA-256 `f7b83eabace3d98b6e88ce77d5e4d6267fe712e60541af6e704ae066390c26ba`.

## Register data-flow resolver (alpha06)

- Replaced the legacy nearby-field heuristic with register provenance across configuration literal, factory call, result register and static registry field.
- Propagates numeric defaults from registration arguments and links registry fields to compatible readers.
- Added a shadow comparison mode used only by regression tests; runtime discovery always uses proven data flow.
- Added conservative handling for unresolved numeric modes, structural strings and certificate-related identifiers.
- On 17.8.663814, schema 2 produced 954 editable mappings. Under the same current semantic/safety policy, 871 matched the legacy resolver, 83 were promoted with proven data flow and 9 proximity-only mappings were demoted.
- Resolver schema changes now force a one-time cold analysis of the installed APK; remote overrides remain stored separately.
- 20 tests passed across the complete fixture/regression suite. Detailed evidence is in `RESOLVER_SCHEMA2_REPORT.md` and `outputs/discovery/resolver-schema1-to-2.json`.
- `assembleDebug` passed for version 105 / `1.0.0-alpha06`; the APK is debug-signed with Signature Scheme v2 and has SHA-256 `f52f42693c9761c31ee29b8f8ffe97a14074ac6bf7408bafea49057e5fd8099d`.

## Fixture results

The latest export completed without analyzer errors:

| Build | Identifiers | Editable scalar mappings |
|---|---:|---:|
| 16.9.666314 | 53,604 | 867bb |
| 17.8.163744 daily | 54,260 | 886 |
| 17.8.663814 stable | 54,384 | 886 |

For 16.9 to 17.8 stable, 773 same-key getter pairs were directly comparable: 768 changed class name, 332 changed method name, and only 11 changed instruction count. Among 22,640 same-name resources, 1,314 (5.80%) retained the numeric ID. These results support key/name-based rediscovery and reject fixed obfuscated names or resource IDs as durable identities.

The Cielo regression verifies that `CieloFeature__earth_enabled` remaps across renamed classes and that `CieloFeature__cielo_focus_enabled` changes its inferred compiled default from false to true.

## Local validation

- All 12 unit/regression tests in 8 suites passed with zero failures, errors, or skips. They cover normalized inventory round-trip, typed override validation, active-only and compact profile generation, structural matching, sensitive-key policy, fixture remapping, Cielo default changes, and three-version inventory export.
- Compilation and fixture tests run with one Gradle worker and an isolated local Gradle cache.
- `assembleDebug` passed for version 102 / `1.0.0-alpha03`, debug-signed with APK Signature Scheme v2, with SHA-256 `da24851d61372047b2451691882e1e566490347446a598d622c0ba04ea0f3ca7`.
- APK inspection confirmed API 101/101, protective exception mode, static Android Auto-only scope, the Java entrypoint, and absence of legacy `assets/xposed_init`.
- Android Lint could not run to completion in this restricted environment: its Gradle plugin and an AndroidTest dependency were not cached, while network access was unavailable. This is recorded as not run, not passed.

## Still dependent on a device

- API 101 service handshake and scope activation on the user's LSPosed installation.
- Actual hook installation/query/return events, currently visible only in LSPosed logs because the hook process has no reverse writable telemetry channel in this implementation.
- Observable effects in Android Auto, Open HeadUnit, and a vehicle.
- Server/account/head-unit negotiated features such as Gemini, Cielo, widgets, and parked video where changing a local scalar flag alone may not be sufficient.
# Alpha07 consumer graph (2026-09-29)

- Added bounded reverse-call, interface-dispatch and wrapper traversal for editable identifiers.
- Persisted consumer paths, runtime consumer status and separately reviewed semantics in SQLite schema 3 and normalized JSON.
- Validated `UxPrototype__enabled` end to end against the 17.8 stable DEX and the observed launcher behavior.
- 17.8.663814 result: 954 editable, 936 with consumer evidence, 18 conservatively dormant, no scan errors.
- Full unit suite and `assembleDebug` passed for version 106 / `1.0.0-alpha07`.
- Debug APK: `outputs/AAExperimentManager-1.0.0-alpha07-debug.apk`; APK Signature Scheme v2 verified; SHA-256 `5a25c5f7a2001ac7898b1149bd41c6cdec36cd28606e64e4048caeb6fd1d4e9e`.

# Alpha08 reviewed semantics and evidence UI (2026-09-29)

- Added a full evidence dialog for every editable entry, including all retained methods, DEX files, depths, link kinds and descriptor paths.
- Added copyable evidence and `Reviewed` / `Dormant` catalog filters.
- Added an exact-base-hash reviewed-semantics registry; entries never carry over blindly to another Android Auto build.
- SQLite schema 4 persists diagnostic metadata; resolver schema 4 triggers one rescan without deleting remote overrides.
- 17.8.663814: 54,384 identifiers, 954 editable, 937 `PRESENT` including the read-only reviewed URL, 18 editable `DORMANT`, 5 reviewed entries present, 0 errors.
- Full unit suite passed; final targeted tests and `assembleDebug` passed for version 107 / `1.0.0-alpha08`.
- Debug APK: `outputs/AAExperimentManager-1.0.0-alpha08-debug.apk`; APK Signature Scheme v2 verified; SHA-256 `7efa87815bea93e6fb876af0425454c6a6b1c375886c36dd80045004861425fb`.

# Alpha09 catalog and performance consolidation (2026-09-29)

- Removed eager getter/consumer child queries from the editable list and moved them to an on-demand background details lookup.
- Added a separate full Catalog tab with 300 ms debounced SQL search, a 500-row render cap and `UNKNOWN` hidden by default.
- Added combinable kind, value-type, resolution, consumer, editable, modified, reviewed and kill-switch filters.
- Added configurable JSON/CSV exports for editable, modified or all discoveries; optional methods/consumers use bulk joins.
- SQLite schema 5 adds a composite filter index while preserving inventories and remote overrides.
- Full tests passed after the data-layer change; final Kotlin compilation and `assembleDebug` passed after the UI/export completion.
- Debug APK: `outputs/AAExperimentManager-1.0.0-alpha09-debug.apk`; APK Signature Scheme v2 verified; SHA-256 `64cfcee88951f05f544d5836733fb79ec45bc259f81d54609233fe9559ba5bb5`.

# Alpha10 navigation and settings (2026-09-29)

- Reduced navigation to Status, Editable and Settings; nested Diagnostics and Full catalog under Settings.
- Fixed the alpha09 Diagnostics crash by removing eager-getter assumptions from lightweight catalog rows.
- Moved Editable/Catalog filters into dialogs and added a simplified editable value-type filter.
- Added 500-row catalog pagination, file-based JSON override backup/import, manager theme selection and HighwindBR credits.
- Complete tests and `assembleDebug` passed for version 109 / `1.0.0-alpha10`.
- Debug APK: `outputs/AAExperimentManager-1.0.0-alpha10-debug.apk`; APK Signature Scheme v2 verified; SHA-256 `3950163e00d8a536c5e867c7aa9e0d77aa7a17e9e5572483241d7f8ef75cf04e`.

# Alpha11 resolver B1 model and oracles (2026-09-29)

- Added per-occurrence literal roles, instruction offsets, entity kinds and local classification confidence.
- Separated runtime hook type, physical storage type and semantic value type. Numeric modes retain their real `INT`/`LONG` ABI while unresolved enum domains stay read-only.
- Added multidimensional confidence for identity, type, default, getter, consumer and semantics.
- Isolated exact-build manual annotations from automatic resolution and placed report-derived expected answers exclusively in the test/reference source set.
- Upgraded normalized inventory JSON to schema 3 and SQLite to schema 6 with backward migration.
- Exports and details now expose storage and semantic types independently.
- Full test suite and `assembleDebug` passed for version 110 / `1.0.0-alpha11`.
- Debug APK: `outputs/AAExperimentManager-1.0.0-alpha11-debug.apk`; APK Signature Scheme v2 verified; SHA-256 `3d080eb2abecc383f7feb15d36c2562d0f7fd40b1d48f10ce92b0b4ced984ee1`.

# Alpha12 resolver B2 structural engine (2026-09-29)

- Added DEX control-flow graphs, worklist register provenance and field-to-return def-use proof.
- Replaced the fixed four-level consumer traversal with a cost-bounded worklist and iterative SCC detection.
- Separated diagnostic, telemetry and test callers from runtime-consumer evidence.
- Added mechanical Boolean branch-polarity evidence without claiming audited feature semantics.
- Added one-hop subgraph similarity and conservative global one-to-one candidate rejection.
- On 17.8.663814, 935 of the B1 mappings remain editable and 19 ambiguous numeric registrations are suspended; no new mapping was promoted.
- Detailed evidence is in `RESOLVER_B2_STRUCTURAL_ENGINE.md` and `outputs/discovery/resolver-b1-to-b2.json`.
- All 31 unit and fixture regression tests passed; `assembleDebug` passed for version 111 / `1.0.0-alpha12`.
- Debug APK: `outputs/AAExperimentManager-1.0.0-alpha12-debug.apk`; APK Signature Scheme v2 verified; SHA-256 `69a15fceeddce5b7ce8fc1042eb32d1ebd9ddc08880f4c8c6a4082b02e806558`.

# Alpha13 256 MiB scan-memory fix (2026-09-30)

- Removed retained DEX instruction lists and kept only compact field-to-return evidence.
- Bounded graph scheduling before enqueue and deduplicated interface/inheritance targets.
- Released intermediate DEX structures before loading the 32k resource catalog.
- Added an installed-17.8 regression executed with a 256 MiB test heap; the complete APKM scan passes with 935 editable mappings and no scanner errors.
- Detailed cause and validation are documented in `ALPHA13_MEMORY_REPORT.md`.
- All 32 tests passed and `assembleDebug` completed for version 112 / `1.0.0-alpha13`.
- Debug APK: `outputs/AAExperimentManager-1.0.0-alpha13-debug.apk`; APK Signature Scheme v2 verified; SHA-256 `bd279c53ed19505a32509d8aa25d76fc4caa30f3c58a7a4998cbf20f5b6c2ca5`.

# Alpha14 resolver B2 subgraph completion (2026-09-30)

- Added bidirectional multi-layer method-subgraph fingerprints and contextual stable-owner anchors.
- Added bounded exact maximum-weight bipartite matching with per-edge alternative-solution margins; unresolved ties and oversized ambiguity components are suspended.
- Added class-family global matching for symmetric implementations with multi-hop external callers.
- Added real daily-to-stable regression oracles for the DND wrapper false positive and the MessagingMetaCache family. Exact obfuscated names remain test-only and are not production mappings.
- Kept the installed scan on the alpha13 compact path so cross-build analysis cannot reintroduce the 256 MiB device OOM.
- Detailed design and boundary are in `RESOLVER_B2_SUBGRAPH_ALPHA14.md`.
- The complete unit/fixture suite passed, followed by the installed 17.8.663814 scan in a separately constrained 256 MiB test worker.
- `assembleDebug` passed for version 113 / `1.0.0-alpha14`.
- Debug APK: `outputs/AAExperimentManager-1.0.0-alpha14-debug.apk`; APK Signature Scheme v2 verified; SHA-256 `ec6aaa5e3194380a2cc51e2d2a4b61cd05f05eb34f00830fcc2da4296c59a422`.

# Alpha15 resolver B3 Protobuf foundation (2026-09-30)

- Added bounded Protobuf wire inspection with distinct raw, wire-parsed and schema-resolved states.
- Recovered the real PhoneTheme manufacturer lists and nested Cielo widget configuration contents without hardcoded obfuscated mappings.
- Kept all schema-unresolved serialized values read-only and exposed their decoded evidence only in the details view.
- Scoped Base64 probing to methods containing Protobuf-like setting keys and retained conservative ambiguity for conflicting branch defaults.
- Added the supplied AA Experiment Manager launcher icon.
- Detailed behavior and remaining B3 work are documented in `RESOLVER_B3_PROTOBUF_ALPHA15.md`.
- All 44 tests passed, including the complete stable scan in a 256 MiB worker; `assembleDebug` passed for version 114 / `1.0.0-alpha15`.
- Debug APK: `outputs/AAExperimentManager-1.0.0-alpha15-debug.apk`; APK Signature Scheme v2 verified; SHA-256 `35396c61ee0f2d6f7df0598b96a1c73d3484daa4039d9072957d83678110b831`.

# Alpha16 resolver B3 enum, cache and activation semantics (2026-09-30)

- Added explicit resolved-mapping/unresolved-domain diagnostics for enum-backed numeric settings; unrestricted numeric editing remains blocked.
- Added compact invoke-result-to-field cache data flow after wrapper/interface resolution.
- Added `LIVE`, `INSTANCE_CACHE`, `STATIC_CACHE`, `MIXED` and corresponding activation scopes to catalog metadata and the details UI.
- Real regressions cover `AceFeature__mode`, Media autoplay delay, thermal frame-rate throttling and both Messaging removal paths.
- Preserved the 256 MiB scan boundary and replaced the white-disc launcher icon with the supplied transparent asset.
- Downstream cached-field lineage into static startup state and runtime latches remains deliberately pending.
- Detailed evidence is in `RESOLVER_B3_ENUM_CACHE_ALPHA16.md`.
- All 45 tests passed; `assembleDebug` passed for version 115 / `1.0.0-alpha16`.
- Debug APK: `outputs/AAExperimentManager-1.0.0-alpha16-debug.apk`; APK Signature Scheme v2 verified; SHA-256 `11250cc295f2fd8bb9d51d563216219970a53c6257017732bca511ae7119f60e`.

# Alpha17 secondary cache and runtime lineage (2026-09-30)

- Added compact boolean field lineage beyond the first proven getter/cache.
- Added control-dependency analysis for branch-selected boolean values, static-initializer detection and lifecycle latch classification.
- Added `STATIC_STARTUP`, `RUNTIME_LATCH` and `NEXT_LIFECYCLE_START`, with field lineage and latch targets in diagnostics.
- Both Messaging removal flags now recover live, instance-cache, static-startup and runtime-latch paths without production mappings tied to obfuscated names.
- Corrected the prior thermal frame-rate instance-cache result, which was a generic object-return collision; the proven live path remains.
- Kept lineage sparse and bounded so the installed 17.8.663814 catalog still completes in a 256 MiB test worker.
- Detailed evidence is in `RESOLVER_B3_LINEAGE_ALPHA17.md`.
- All 46 unit and fixture tests passed; `assembleDebug` passed for version 116 / `1.0.0-alpha17`.
- Debug APK: `outputs/AAExperimentManager-1.0.0-alpha17-debug.apk`; APK Signature Scheme v2 verified; SHA-256 `89669d9a93cda1afb9dbd6985ebc9450b498017644333b90d5b8211f0eb6211d`.

# Alpha18 AA Experiments visual redesign (2026-09-30)

- Renamed the visible product to AA Experiments and replaced placeholder navigation with Status, Experiments and Settings icons.
- Added a green identity fallback while preserving Android dynamic color on supported devices.
- Consolidated Status into Environment and Resolver/runtime hierarchy.
- Replaced inline editors with compact experiment rows and a dedicated details/editor surface.
- Preserved the boolean `System default` / `Force false` / `Force true` model without a misleading binary switch.
- Reorganized Settings and rebuilt Diagnostics as collapsible technical sections with copy actions.
- Detailed behavior is in `VISUAL_REDESIGN_ALPHA18.md`.
- All 46 unit and fixture tests passed, followed by the installed scan in a 256 MiB test worker.
- `assembleDebug` passed for version 117 / `1.0.0-alpha18`.
- Debug APK: `outputs/AAExperiments-1.0.0-alpha18-debug.apk`; APK Signature Scheme v2 verified; SHA-256 `b77bd909bc3794b32c2522f3b7fca6e25828e4fa2db496279271c750b6b28c69`.

# Alpha19 runtime observability and visual polish (2026-09-30)

- Replaced the permanently ambiguous runtime `Not observed` label with seven explicit readiness states covering target, service, API, scope, overrides and profile publication.
- Added structured `AAX_EVENT` LSPosed records for module/profile/hook/return outcomes.
- Rate-limited runtime return evidence to one event per key and value generation.
- Kept the API 101 boundary explicit: hooked processes can only read the module's remote data, so the manager does not claim reverse telemetry that it cannot receive.
- Documented that newly published mappings take effect when the Android Auto process starts again; the app never force-stops Android Auto or reboots the device.
- Shortened the overflowing visible labels to `default` and `ALL` without changing their internal semantics.
- Detailed behavior is in `RUNTIME_OBSERVABILITY_ALPHA19.md`.
- All 53 declared unit and fixture tests passed, followed by the installed scan in a 256 MiB test worker.
- `assembleDebug` passed for version 118 / `1.0.0-alpha19`.
- Debug APK: `outputs/AAExperiments-1.0.0-alpha19-debug.apk`; APK Signature Scheme v2 verified; SHA-256 `3b73887b16ec7a44ecf36b24c1c6fa9e6166a1063071dc9d178429530c495a0a`.

# Alpha20 visual redesign completion (2026-09-30)

- Replaced the experiment dialog with a dedicated details screen and removed primary navigation while editing.
- Added compact metadata chips and collapsible Evidence, Raw identifiers and Advanced information sections.
- Moved experiment and catalog filters to responsive Material 3 bottom sheets.
- Refined Status with compact environment cards and a prominent runtime readiness banner.
- Added an About screen and improved Settings, Diagnostics and catalog hierarchy.
- Added monospace technical values, auto-mirrored navigation, touch-target preservation and long-text overflow handling.
- Preserved all alpha19 resolver, profile and runtime-hook behavior.
- Detailed behavior and accessibility notes are in `VISUAL_REDESIGN_ALPHA20.md`.
- All 53 unit and fixture tests passed, followed by the installed scan in a 256 MiB test worker.
- `assembleDebug` passed for version 119 / `1.0.0-alpha20`.
- Debug APK: `outputs/AAExperiments-1.0.0-alpha20-debug.apk`; APK Signature Scheme v2 verified; SHA-256 `2b6feaac3b4d34f5e089bcd8744efd8246db218f1ab417f9a96d109998f91003`.

# Alpha21 visual polish and branded color mode (2026-09-30)

- Added `AA Experiments` and `System colors` color modes, with the green branded palette as the default and Material You available as an opt-in.
- Compacted experiment rows, reduced metadata badges, prioritized long identifiers and reduced search/list vertical density.
- Replaced the dominant Status color fill with neutral containers and concise runtime wording.
- Added the real application icon to About and tightened Settings spacing.
- Kept True green, False blue and Default neutral; red remains reserved for actual errors and incompatibility.
- Preserved all alpha20 resolver, profile and runtime-hook behavior.
- Detailed behavior is in `VISUAL_POLISH_ALPHA21.md`.
- The complete unit and fixture suite passed, followed by the installed scan in a 256 MiB test worker.
- `assembleDebug` passed for version 120 / `1.0.0-alpha21`.
- Debug APK: `outputs/AAExperiments-1.0.0-alpha21-debug.apk`; APK Signature Scheme v2 verified; SHA-256 `5432afb33a346d7a6448951fa3959ee9fa9c4b2a1e74332881f07d3f69f8ff33`.

# Alpha22 navigation, scan progress and Appearance (2026-10-01)

- Added an explicit back hierarchy and retained Experiments query, filters and exact list position across detail navigation and Activity recreation.
- Added real staged progress for uncached installed scans and imported archives, including DEX and database counters without synthetic time-based percentages.
- Rebuilt Appearance as compact `Theme` and `Accent color` preference rows with touch-anchored, viewport-safe Material popups, selected tonal items and immediate application.
- Migrated visible palette names to `Lab Green` and `Material You` while preserving legacy stored choices and independent theme/palette state.
- Completed the planned list, details, filter, Evidence and Status polish; visible `Default` is capitalized and semantic TRUE/FALSE/DEFAULT colors remain independent from the accent palette.
- Status omits the complete discovery count and previous-scan changes now describe editable mappings only.
- Preserved the alpha21 resolver, profile and runtime-hook behavior.
- Detailed behavior is in `NAVIGATION_SCAN_APPEARANCE_ALPHA22.md`.
- All 59 unit and fixture tests passed, followed by the installed scan in a 256 MiB test worker.
- `assembleDebug` passed for version 121 / `1.0.0-alpha22`.
- Debug APK: `outputs/AAExperiments-1.0.0-alpha22-debug.apk`; APK Signature Scheme v2 verified; SHA-256 `6c7fc38e71de432e9c6d0464d1f3d2c5683ad17182ca45448e7a52484362a8e7`.

# Alpha23 resolver precision and catalog hygiene (2026-10-01)

- Extracted registration defaults by invocation signature and argument position, separating registry IDs from compiled defaults and respecting wide DEX arguments.
- Added the stable Earth oracle: `earth_status` has registry ID 8 and an empty-string default; `earth_tilt` retains default 6.
- Demoted suspicious textual, dump/representation and generic-library discoveries to read-only catalog evidence without a build-specific blacklist.
- Split structural consumer reachability from semantic consumer categories; wrapper-only, dump, logging, diagnostic, telemetry and test paths no longer prove feature consumption.
- Reduced the stable 17.8.663814 editable set from 940 to 919 by removing 21 false positives.
- Simplified experiment rows, made filters build-aware, removed Dormant from primary filtering and compacted Appearance popups.
- Detailed behavior and regression policy are in `RESOLVER_PRECISION_ALPHA23.md`.
- All 62 unit and fixture tests passed, followed by the installed 17.8.663814 scan in a separately constrained 256 MiB test worker.
- `assembleDebug` passed for version 122 / `1.0.0-alpha23`.
- Debug APK: `outputs/AAExperiments-1.0.0-alpha23-debug.apk`; APK Signature Scheme v2 verified; SHA-256 `b5d9d2158dba28fb284c80a55b685879cd87ca3c39f387f6070258372c711945`.
- `lintDebug` could not complete because the restricted build environment lacked a cached AndroidX Android-test dependency and network access was denied; this occurred after the APK had assembled successfully.

# Alpha24 semantic relationships and eligibility gates (2026-10-01)

- Added bounded cross-identifier decision analysis with conservative `CO_GATED_WITH` relationships.
- Added direct SDK, system-feature, capability and configuration-snapshot gate evidence.
- Added semantic activation scopes for render, Activity and config-snapshot reconstruction.
- Rejected transitive shared-infrastructure coincidences using decision distance, consumer depth, group-size and technical-category limits.
- Added real regressions for Earth, Launcher Shortcuts and satellite network eligibility without production hardcoding of obfuscated names.
- Detailed behavior and remaining strong-relation work are documented in `RESOLVER_B3_RELATIONS_ALPHA24.md`.
- All 65 unit and fixture tests passed, followed by the installed 17.8.663814 scan in a separately constrained 256 MiB test worker.
- `assembleDebug` passed for version 123 / `1.0.0-alpha24`.
- Debug APK: `outputs/AAExperiments-1.0.0-alpha24-debug.apk`; APK Signature Scheme v2 verified; SHA-256 `d51072379b9cc009689b565139c156348031cad624c345e694254b46d5162c8a`.

# Alpha25 storage lifecycle and package identity (2026-10-01)

- Imported APKM/APKS/APK files now live in an isolated session directory and are deleted in `finally`, including failed scans.
- Legacy temporary imports are removed when the manager starts.
- The catalog database retains the active build and one predecessor for comparison; older catalogs are deleted and the database is compacted.
- Changed the installed application ID to `io.github.highwindbr.aaxp`; no data migration is attempted.
- Corrected the Experiment details background so it follows the active light/dark theme.
- All 65 unit and fixture tests passed and `assembleDebug` completed for version 124 / `1.0.0-alpha25`.
- Debug APK: `outputs/AAExperiments-1.0.0-alpha25-debug.apk`; APK Signature Scheme v2 verified; SHA-256 `3504840925838a12e10376d5533a0e38b164b96133c1a0447d0b86a496c930cb`.

# Alpha26 source routing and reevaluation (2026-10-02)

- Added structural detection of compiled, Fenotype, SharedPreferences, remote-capability, system-state, derived and configuration-snapshot sources.
- Added source precedence, reevaluation triggers and a downstream supersession warning without build-specific obfuscated mappings.
- Added regressions for preference-backed values, preference listeners and remote capability precedence.
- Corrected status/navigation bar appearance and Experiment details text colors in dark mode.
- Long detail identifiers now break at underscore and camel-case boundaries without modifying their real keys.
- All 67 unit and fixture tests passed; the installed scan also passed with a 256 MiB heap.
- `assembleDebug` passed for version 125 / `1.0.0-alpha26`.
- Debug APK: `outputs/AAExperiments-1.0.0-alpha26-debug.apk`; APK Signature Scheme v2 verified; SHA-256 `9838cc49c3417ea708f7a1123849453005f8ba8772d0b6b24ee27c235f298bc7`.

# Alpha27 provenance isolation and readable diagnostics (2026-10-02)

- Split configurable origin, compiled fallback, downstream gate, transport destination, derived input and precedence into independent dimensions.
- Removed alpha26's inferred global precedence and stopped merging evidence from unrelated consumer paths.
- Consumer-budget exhaustion now marks the analysis incomplete and suspends cross-path semantics, gates, transport claims, relationships and graph-derived cache lineage.
- Restricted preference-listener claims to the same getter and preference read.
- Deferred cross-flag relationships until a common decision region and result can be proven.
- Reorganized Experiment details into Value flow, Lifecycle and Resolver confidence; raw metadata is collapsed and remains available.
- Added readable camel-case labels and enum values while preserving the original technical keys in storage and exports.
- Incremented the resolver schema to 13 and inventory cache generation to v6, forcing alpha26 catalogs to be rebuilt while preserving overrides.
- Detailed behavior is documented in `PROVENANCE_ISOLATION_ALPHA27.md`.
- All 68 unit and fixture tests passed, followed by the installed 17.8.663814 scan in a separately constrained 256 MiB test worker.
- `assembleDebug` passed for version 126 / `1.0.0-alpha27`.
- Debug APK: `outputs/AAExperiments-1.0.0-alpha27-debug.apk`; APK Signature Scheme v2 verified; SHA-256 `f9e8209fa871681130aa7975f312cf1dd0c08eba1bec31071bcd1be0be90db5f`.

# Alpha28 compatibility rediscovery and directed deep resolve (2026-10-03)

- Added same-key compatibility rediscovery against the previous retained catalog, using global structural matching only to disambiguate the corresponding current mapping.
- Removed keys are never rebound to differently named discoveries; collisions and insufficient structural margins remain suspended.
- Added compatibility counters for preserved, structurally rediscovered, suspended and new configurable mappings.
- Kept the ordinary consumer traversal at 256 nodes and added an explicit Diagnostics action that deep-resolves one inconclusive installed-package mapping at a time with a 512-node budget.
- Directed analysis skips resource materialization and persists only the selected mapping's diagnostic result, avoiding the automatic second full pass that exceeded the 256 MiB target.
- Incremented the resolver schema to 14 and inventory cache generation to v7; overrides remain stored separately and are preserved.
- Detailed safety rules and behavior are documented in `COMPATIBILITY_DEEP_RESOLVE_ALPHA28.md`.
- All 73 unit and fixture tests passed. The normal installed scan and the real single-key directed scan also passed in separately constrained 256 MiB test workers.
- `assembleDebug` passed for version 127 / `1.0.0-alpha28`.
- Debug APK: `outputs/AAExperiments-1.0.0-alpha28-debug.apk`; package `io.github.highwindbr.aaxp`; APK Signature Scheme v2 verified; SHA-256 `95a40e3fa80bcbc37505ad6a4c836853611242d4ed223274fd4495fdea1f2e5a`.

# Alpha29 bounded classification memory (2026-10-03)

- Corrected the on-device OOM reported during `Classifying experiments` after alpha28 introduced compatibility baselines.
- Compatibility rediscovery now loads compact getter-only historical mappings; old consumers, occurrences and diagnostic metadata are not retained beside the new DEX graph.
- Unique literal getters bypass global structural scoring; only ambiguous same-key mappings enter the global matcher.
- Fixed the consumer worklist so its node budget also bounds retained evidence and graph edges. Callers rejected after the queue reaches its limit no longer allocate evidence that is later sorted.
- Added a real regression that performs two complete stable-17.8 scans sequentially in one 256 MiB worker, with the second using the first scan's compact compatibility baseline.
- Incremented resolver schema to 15 and inventory cache generation to v8 to force a clean post-alpha28 rebuild while preserving overrides.
- Detailed diagnosis is documented in `BOUNDED_CLASSIFICATION_ALPHA29.md`.
- All 74 unit and fixture tests passed. Clean scan, sequential compatibility rebuild and directed deep resolve were independently validated with a 256 MiB test-worker heap.
- `assembleDebug` passed for version 128 / `1.0.0-alpha29`.
- Debug APK: `outputs/AAExperiments-1.0.0-alpha29-debug.apk`; package `io.github.highwindbr.aaxp`; APK Signature Scheme v2 verified; SHA-256 `ea00ced96f62eacbb77d4be876526d6de72be510ea5b36f5d078e26aa99aa4aa`.

# Alpha30 bounded deep-resolve batches (2026-10-03)

- Replaced one-key deep-resolve sessions with adaptive batches of 3, 4 or 5 mappings based on the process heap.
- Each batch reads the APK/DEX files once and reuses the same bounded consumer graph for every selected mapping.
- Results are finalized and committed to SQLite individually, so cancellation, memory pressure or a later failure does not discard completed work.
- Added cancellation between mappings, real per-key progress, pending-count reporting and a preventive memory reserve.
- Already attempted mappings remain excluded from subsequent batches unless a future explicit retry operation is added.
- The graph remains session-only and is released after the batch; no large, build-sensitive graph is persisted.
- Detailed behavior is documented in `DEEP_RESOLVE_BATCH_ALPHA30.md`.
- All 75 unit and fixture tests passed. A real three-key batch and two consecutive full compatibility scans passed in separately constrained 256 MiB test workers.
- `assembleDebug` passed for version 129 / `1.0.0-alpha30`.
- Debug APK: `outputs/AAExperiments-1.0.0-alpha30-debug.apk`; package `io.github.highwindbr.aaxp`; APK Signature Scheme v2 verified; SHA-256 `b0b14053aa21adbcc677539889faca20d90d6b6476677d7b03a0564dc6db40ad`.

# Alpha31 continuous streaming deep resolve (2026-10-03)

- Removed the fixed three-key session limit. One DEX read and prepared graph now serve the complete pending deep-resolve queue.
- Converted consumer enrichment to a lazy streaming path for directed sessions. Each finalized mapping is committed and discarded instead of being accumulated in the returned inventory.
- Added compact getter-only compatibility loading for arbitrary pending-key sets.
- The session pauses only on cancellation, depleted safety reserve or completion; another run resumes from the remaining unattempted mappings.
- Completion messages report resolved, still-inconclusive and pending counts.
- Detailed behavior is documented in `DEEP_RESOLVE_STREAMING_ALPHA31.md`.
- All 75 unit and fixture tests passed. Streaming deep resolve and two consecutive full compatibility scans passed in separately constrained 256 MiB test workers.
- `assembleDebug` passed for version 130 / `1.0.0-alpha31`.
- Debug APK: `outputs/AAExperiments-1.0.0-alpha31-debug.apk`; package `io.github.highwindbr.aaxp`; APK Signature Scheme v2 verified; SHA-256 `26f343be28ca07648518ddbcb124b90d1f6ec6b3169ca858c11ef56b06c04cd7`.

# Alpha32 targeted semantic traversal (2026-10-03)

- Requeued alpha31 `STILL_INCONCLUSIVE` mappings for one explicit targeted retry without rebuilding the catalog.
- Prioritized Activity, renderer and configuration-snapshot endpoints; bounded wrapper and generic-runtime fan-out; stopped expansion through dump, logging, diagnostics, telemetry and test paths.
- Kept the 512-node budget, continuous streaming persistence, cancellation and memory reserve.
- Added honest targeted outcomes for semantic endpoint, budget exhaustion, shared fan-out, technical-only paths, no consumer, generic-only paths and multiple endpoints.
- Only a single semantic endpoint without budget exhaustion is promoted to `RESOLVED`; all other results remain inconclusive with a concrete reason.
- Added Diagnostics totals for every targeted outcome.
- Detailed behavior is documented in `TARGETED_DEEP_RESOLVE_ALPHA32.md`.
- All 77 unit and fixture tests passed; real targeted streaming passed with a 256 MiB test-worker heap.
- `assembleDebug` passed for version 131 / `1.0.0-alpha32`.
- Debug APK: `outputs/AAExperiments-1.0.0-alpha32-debug.apk`; package `io.github.highwindbr.aaxp`; APK Signature Scheme v2 verified; SHA-256 `49079f87b5b7ffbbde47670e827e017fcb89051954d9310217b067410873f61a`.

## Alpha33 — semantic corridor deep resolve

- Semantic endpoints are terminal instead of gateways into additional shared infrastructure.
- A build-derived semantic corridor prioritizes methods that can reach an Activity, renderer or configuration snapshot.
- Corridor and fallback budgets are independent and reported separately.
- Endpoint evidence is protected from the 24-item display cap.
- Only alpha32 mappings that ended in `BUDGET_EXHAUSTED` are queued for this new strategy.
- Production logic remains generic and contains no obfuscated 17.8 mapping table.
- Validation: 78 unit/regression tests passed; directed fixture scan passed with a 256 MiB test-worker heap.
- Debug APK: `outputs/AAExperiments-1.0.0-alpha33-debug.apk`; APK Signature Scheme v2 verified; SHA-256 `60234e4a1b16ec15a57f0af09ca2952be76d437c979ba19752cf601002df7465`.

## Alpha34 — interprocedural value provenance

- Directed call-graph edges now require proof that the invoked value is returned, branched on, stored, forwarded or transformed.
- Discarded invocation results cannot promote callers or semantic endpoints.
- `VALUE_FLOW_NOT_PROVEN` distinguishes unsupported/discarded flows from budget exhaustion.
- Directed scans retain identifier occurrences only for requested keys and keep provenance as sparse reused signatures.
- Alpha33 semantic-corridor exhaustions are retried once; already resolved mappings are preserved.
- Validation: 81 unit/regression tests passed; the real directed fixture passed with a 256 MiB test-worker heap.
- Debug APK: `outputs/AAExperiments-1.0.0-alpha34-debug.apk`; APK Signature Scheme v2 verified; SHA-256 `b9a9b33920f18317786fb758546ba911084ec8e39104a4156acc06e20ccf32be`.

## Alpha35 — CFG-aware value flow

- Invoke-result provenance now merges register state through the DEX control-flow graph.
- Switches, array stores, unary conversions, comparisons and scalar transformations are recognized sinks/propagation steps.
- Unproven flows are separated into discarded/unsupported, field-or-object, callback/lambda and complex-CFG categories.
- Alpha34 value-flow failures are queued once for the new analysis; resolved mappings remain untouched.
- The deep-resolve action is disabled when no actionable entries remain.
- Validation: 82 unit/regression tests passed; the real directed fixture passed with a 256 MiB test-worker heap.
- Debug APK: `outputs/AAExperiments-1.0.0-alpha35-debug.apk`; APK Signature Scheme v2 verified; SHA-256 `bc466694f3d28dd219a38774ceb319ec1a9481843dcea36951ce37f8eb28f70e`.

## Alpha36 — exact callsite outcomes

- Replaced method-wide limitation guesses with one compact outcome byte per invoke occurrence.
- Duplicate invocations of the same getter are tracked independently.
- Proven sinks now distinguish return, branch, field store, forwarded argument, receiver use, array/collection store and scalar transformation.
- Inconclusive paths distinguish uncaptured results, overwritten captures, unused captures, unsupported opcodes, ambiguous CFG joins and unknown callsites.
- Exact per-outcome counts and bounded unsupported-opcode evidence are stored in identifier metadata and summarized in Diagnostics.
- General field/object expansion remains intentionally deferred until the device distribution identifies the real dominant missing flow.
- Detailed behavior is documented in `EXACT_CALLSITE_ALPHA36.md`.
- Validation: all 83 unit/regression tests passed; directed fixture analysis passed with a 256 MiB test-worker heap.
- `assembleDebug` passed for version 135 / `1.0.0-alpha36`.
- Debug APK: `outputs/AAExperiments-1.0.0-alpha36-debug.apk`; APK Signature Scheme v2 verified; SHA-256 `1f28f3201de6ade3ae5b753e4f4ca6e840d61374810168709739eb25fe0d5dba`.

## Alpha37 — occurrence-aligned callsites

- Separated the deduplicated reverse-graph call list from the temporary instruction-ordered callsite analysis.
- Flow outcomes are computed per concrete invoke occurrence, including duplicates, then compacted per method target before entering the graph.
- `invoke-custom` does not shift ordinary method-call outcome ordinals.
- A caller is proven when any occurrence of the target reaches a proven sink; other occurrences remain independently classified.
- Alpha36 `UNKNOWN` results are automatically requeued once under the corrected strategy, including stale diagnostics on otherwise resolved entries.
- The occurrence-level representation remains directed-session-only and is not persisted with the catalog.
- Detailed behavior is documented in `CALLSITE_ALIGNMENT_ALPHA37.md`.
- Validation: all 85 unit/regression tests passed; the real directed fixture passed with a 256 MiB test-worker heap after occurrence data was compacted before graph construction.
- `assembleDebug` passed for version 136 / `1.0.0-alpha37`.
- Debug APK: `outputs/AAExperiments-1.0.0-alpha37-debug.apk`; APK Signature Scheme v2 verified; SHA-256 `76857f4631d84b943d82f36e2af69d0f29ccce7cada7ae797d120bf1fcd8243e`.

## Alpha38 — instance result origin

- Instance getter results retain the getter callsite as their own origin instead of being replaced by the receiver provider.
- Receiver use and returned scalar provenance are now independent.
- Method-local origin sets are capped at 16 and honestly become CFG-ambiguous on overflow.
- Diagnostics count only the limitation that actually blocks a value-flow resolution; secondary unproven edges remain in raw metadata.
- Added a mandatory opt-in full-corpus release gate covering all 919 editable mappings from Android Auto 17.8.663814.
- Full-corpus result: 70 semantic endpoints, 6 value-flow blockers, 5 CFG ambiguities, 1 unsupported opcode and zero blocking unclassified callsites.
- The complete 919-key directed analysis passed with a 256 MiB test-worker heap.
- Detailed behavior is documented in `INSTANCE_RESULT_ORIGIN_ALPHA38.md`.
- The regular suite completed with 86 passing tests and the opt-in full-corpus gate passed separately, for 87 declared tests total.
- `assembleDebug` passed for version 137 / `1.0.0-alpha38`.
- Debug APK: `outputs/AAExperiments-1.0.0-alpha38-debug.apk`; APK Signature Scheme v2 verified; SHA-256 `92ee256e255cfa2e8ecffc9e3181af67d9fc5a1a5626da58dfa5c518cd9ecf98`.

## Alpha39 — build, persistence and runtime integrity

- Catalog identity now covers version code, base APK, and every split APK.
- Database schema 7 stores composite identity plus the underlying artifact evidence.
- Runtime profile schema 3 is rejected unless the installed package recomputes to the same fingerprint.
- Persisted overrides are type/mapping checked; incompatible values remain stored but are suspended and not published.
- DOUBLE decoding is fixed and every supported scalar runtime type has an ABI regression test.
- The frozen 17.8 gate requires 919 editable identifiers and zero blocking UNKNOWN callsites.

## Alpha40 — reproducible release and resilient analysis

- Removed the absolute Windows `javac.exe` path and aligned CI with compileSdk 36.
- Debug and release builds no longer depend on a repository-local keystore; tagged releases use
  injected GitHub secrets and are verified with `apksigner` before publication.
- CI runs the regular suite, isolated installed/compatibility/deep-resolve gates at 256 MiB, and the
  opt-in 919-key full-corpus release gate.
- Long analyses are protected by a low-priority data-sync foreground service so transient
  screen-off Activity/Android Auto reconnections do not cancel the ViewModel job.
- Numeric zero-only resolver diagnostics are omitted from raw metadata and Diagnostics while
  meaningful status zeros remain visible.
- Compatibility classification releases obsolete catalog generations and bounds retained consumer
  paths without reducing the traversal budget or semantic-endpoint priority.
- Popup sizing now uses density-independent units; empty STRING overrides are valid; non-boolean
  pills use `CUSTOM`; stale strings and unused inventory-cache code were removed.
- Validation: 89 regular unit/regression tests passed. Installed scan, compatibility scan and
  directed deep resolve each passed in an independent 256 MiB worker. The frozen full-corpus gate
  passed with all 919 editable mappings and zero blocking UNKNOWN callsites.
- `assembleDebug` passed for version 140 / `1.0.0-alpha40`.
- Debug APK: `outputs/AAExperiments-1.0.0-alpha40-debug.apk`; APK Signature Scheme v2 verified;
  SHA-256 `300ba013d1ef7d888b770708bdc05cfa4387aaa8246a84380c67fc823fd673f3`.
