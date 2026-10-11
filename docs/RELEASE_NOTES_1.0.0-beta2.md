# AA Experiments 1.0.0-beta2

Second public beta of AA Experiments, an Android Auto experiment inspector and runtime override
manager for LSPosed Modern API 101.

## Fixed

- The first APK import no longer reports a SQLite query error after successfully saving a catalog.
- Catalog retention now checkpoints the SQLite WAL through the query API before vacuuming.

## Release readiness

- Adds regression coverage for pruning old catalogs while retaining the active catalog and its
  predecessor.
- Keeps existing runtime overrides and their preference storage unchanged.
- Retains the Modern Xposed API 101 entry point, static Android Auto scope and protective exception
  mode used by the first beta.

## Known issue

**Keep the device awake while a scan or deep analysis is running.** Screen-off can still cause an
active analysis to stall or fail to complete. This accepted beta limitation does not invalidate
completed analysis results or runtime overrides generated from a completed profile.

Additional limitations and recovery guidance are documented in `docs/KNOWN_LIMITATIONS.md`.

## Privacy and licensing

AA Experiments is released under `GPL-3.0-or-later`. Third-party notices are distributed with the
release and packaged in the APK. Scanning and resolver analysis run locally; the app has no
developer-operated backend, analytics, advertising or tracking.

See `PRIVACY.md`, `LICENSE`, and `THIRD_PARTY_NOTICES.md` in the source repository for details.
