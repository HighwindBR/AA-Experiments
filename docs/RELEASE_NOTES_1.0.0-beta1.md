# AA Experiments 1.0.0-beta1

First public beta of AA Experiments, an Android Auto experiment inspector and runtime override
manager for LSPosed Modern API 101.

## Highlights

- Installed Android Auto discovery and structural re-resolution without hardcoded obfuscated production names.
- Typed, safety-gated scalar overrides with incompatible or ambiguous mappings suspended before publication.
- Runtime override snapshots with live preference refresh and fail-open behavior.
- Composite installed-build fingerprinting across the base APK and splits.
- Release regression coverage against private Android Auto fixtures, including three isolated 256 MiB gates.
- Frozen Android Auto 17.8 full-corpus gate covering all 919 editable keys with zero blocking UNKNOWN callsites.
- Restore-all recovery path and explicit diagnostics for runtime profile acceptance and hooks.

## Known issue

**Keep the device awake while a scan or deep analysis is running.** Screen-off can still cause an
active analysis to stall or fail to complete. This is a known, accepted beta limitation and does not
invalidate already completed analysis results or runtime overrides generated from a completed
profile.

Additional limitations and recovery guidance are documented in `docs/KNOWN_LIMITATIONS.md`.
