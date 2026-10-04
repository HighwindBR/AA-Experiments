# Architecture

The Compose Manager discovers the installed Android Auto package set or imports an archive. The
scanner reads every DEX, builds typed identifier/occurrence records and passes them through the
resolver. SQLite schema 7 stores catalogs under a composite build fingerprint containing version
code, base APK hash and ordered split artifact hashes.

Active typed preferences are validated against the current catalog and serialized into profile
schema 3. The libxposed API 101 module independently recomputes the installed fingerprint, installs
only zero-argument mappings from a matching profile and reads overrides from an immutable atomic
snapshot. Every invalid or missing state executes the original getter.

Long analysis runs in `viewModelScope` while a low-priority foreground data-sync service pins the
process. The service lifetime is protected by `try/finally`; it improves screen-off resilience but
does not promise survival after force-stop, reboot or process termination.

See RESOLVER, RUNTIME and COMPATIBILITY for subsystem details. Historical design decisions live in
`history/ALPHA_HISTORY.md` and `history/alpha-details/`.
