# AA Experiments alpha39 — build and runtime integrity

Alpha39 binds every catalog and runtime profile to one canonical identity made from the Android Auto version code, the SHA-256 of `base.apk`, and the ordered `(split name, split APK SHA-256)` set. A change to any split now creates a different catalog identity and prevents an old runtime profile from being installed.

The SQLite schema is version 7. The legacy `base_sha` key column remains for a compact migration, but stores the composite build fingerprint; the actual base APK hash, version code, and package artifact hashes are retained separately as evidence. Resolver profiles use schema 3 and the hook independently recomputes the fingerprint from the installed base and splits before accepting mappings.

Persisted preferences are no longer assumed compatible. Before publication, the Manager validates that the identifier still exists, remains editable, has exactly one getter, and retains the stored value type. Incompatible values are preserved in remote preferences but excluded from the active profile and reported as suspended.

The runtime scalar ABI now tests BOOLEAN, the INT-compatible byte/short/char/int family, LONG, FLOAT, DOUBLE, and STRING. In particular, `DOUBLE:x` is accepted only by a `double` getter; the previous erroneous `FLOAT` check was removed.

The frozen 17.8 release gate requires exactly 919 editable identifiers and zero unresolved `UNKNOWN` value-flow callsites. This count is fixture-specific and is not imposed on later Android Auto versions.
