# Changelog

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
