# Changelog

## 1.0.0-beta4-preflight16 — visual QA follow-up (CI/device pending)

- Fixed nearly invisible dark-theme back arrow in Experiment details by
  explicitly using `MaterialTheme.colorScheme.onBackground`.
- Armed read-only Developer tools are automatically revealed, but the
  section can now be collapsed and reopened while a diagnostic remains
  armed. The previous unconditional visibility made the toggle inert.
- Documented visual validation of sixteen user-provided light/dark
  screenshots; no personal screenshot assets are committed.

No LSPosed runtime, profile, persisted override or catalog behavior changed.
This remains development work, not an RC or release.

## 1.0.0-beta4-preflight15 — Round B Compose UX and accessibility (CI/device pending)

- Responsive Experiments title and counts, avoiding collisions on small displays and larger fonts.
- Theme-aware status colors and accessible, 48dp+ filter toggle rows.
- Clarified `Configured value` versus independent LSPosed runtime verification.
- Validated normal and Advanced scalar inputs inline with shared typed logic,
  including finite DOUBLE/FLOAT, signed bounds and explicit Advanced BOOLEAN selection.
- Preserved valid empty-string overrides; STRING editing supports multiline input.
- Replaced long-lived Experiments success/error banners with transient Snackbars;
  the detail editor still displays operation-specific failure feedback.
- Kept Advanced opt-in prominent and grouped the VTC and historical DOUBLE
  probes in an optional Developer tools subsection.

No normal/Advanced preference schema, runtime hook, catalog mapping or
LSPosed profile was modified by Round B. **Preflight15 is not an RC or release.**
Full CI, signed artifact and device UI smoke checks remain pending.

## 1.0.0-beta4-preflight14 — Round A reliability hardening (CI/device pending)

- Dual-slot profile publication with verified read-back and durable pointer commit; legacy profile fallback remains available.
- Serialized profile refreshes and build-scoped UI state handoffs prevent stale asynchronous publication.
- Single-owner catalog/DEX analysis gate prevents overlapping foreground-service users.
- Remote preference disconnection no longer publishes an empty profile; stale service-death callbacks are ignored.
- Added injected write/read/commit failure tests, refresh concurrency tests and analysis ownership tests.
- Restored loading/progress UI after interrupted analysis and published `PROFILE_SOURCE` diagnostic event.

**Development preflight only**: full Android Gradle tests, release lint, signed APK build and LSPosed on-device smoke test have not yet been executed for this revision.

## 1.0.0-beta4-preflight13 — development build (validation pending)

### Runtime hardening and coverage audit
- Hardened normal BOOLEAN, INT byte/short/char, FLOAT and DOUBLE decoding.
- Redacted returned normal STRING content from LSPosed module events.
- Added normal getter ABI/range compatibility checks and suspended colliding getter aliases.
- Restricted backup import to current editable, compatible normal targets.
- Required typed double confirmation for every Advanced override.
- Added mechanism-level regression tests and `docs/BETA4_MECHANISM_AUDIT.md`.

This is **not** a published release. The full Gradle test/lint/signing
pipeline and representative installed-device regressions are still pending.

## 1.0.0-beta3 — release candidate (validation pending)

### Fixed after on-device beta3 testing
- Added, Removed, and Changed now list individual differences between successive editable catalogs, with previous/current metadata; they no longer describe suspended-override reasons.
- Experiment category filters live alongside Value type in the Filters sheet and categories with no entries are hidden.
- Suspended overrides can be retained while hidden from All for the current Android Auto build, automatically appearing again if suspended on a later build.

### Added
- Inspect, filter and safely discard suspended overrides while preserving their stored values and recoverable names.
- Historical Android Auto 18.0 coverage in the feature catalog, including startup optimizations.

### Changed
- Aligned code namespace and internal package names with the unchanged application ID.
- Clearer saved-value feedback for scalar Apply actions and Lab Green CUSTOM indicators.
- Stable resolver dialog height with underscore-aware wrapping.
- Documentation of update-resilient flag discovery.

### Not included
- Force-reapply of incompatible overrides is postponed until after beta3.

### Known limitations
- Previously stored overrides may lack a recoverable name if all historical mappings have been pruned.
- Complete CI and physical-device validation is required before publishing.

## 1.0.0-beta2 — published 2026-10-12

### Fixed
- First APK import no longer reports a SQLite query error while pruning old catalogs.

### Changed
- Version metadata and in-app version labels now identify the beta2 catalog candidate.

## 1.0.0-beta1 — pending publication

### Added
- GPL-3.0-or-later project licensing, bundled third-party notices, and explicit copyright attribution.
- Privacy and AI-assisted-development documentation covering local processing, lack of developer/OpenAI runtime data collection, and manual physical/head-unit validation.
- Atomic immutable runtime override snapshots and strongly retained preference listener.
- Fixture hash gates, release lint and published APK checksum.
- Canonical documentation with indexed alpha history.

### Changed
- Long-analysis foreground-service lifetime is cancellation-safe.
- Automated private release gates and the physical smoke test have passed for beta1.
- Version remains frozen at `1.0.0-beta1` / versionCode 141 for publication.

### Known issues
- Active scans and deep analyses may stall or fail to complete if the device screen turns off. Keep the device awake until analysis completes. This is an accepted non-blocking beta1 limitation.

## Pre-beta development

Alpha01–40 established the Manager, API 101 runtime, typed overrides, full DEX resolver, structural
rediscovery, Protobuf/enum safety, cache and activation analysis, directed deep resolve, composite
build identity, 256 MiB gates and reproducible release workflow. See `docs/history/ALPHA_HISTORY.md`.
