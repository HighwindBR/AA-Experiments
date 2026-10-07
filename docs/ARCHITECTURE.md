# Architecture

## Overview

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

## Resolver

The resolver models identifiers by occurrence, separates physical storage from semantic type and
tracks independent confidence for mapping, type, consumer and semantics. Registration data flow
proves literal/default arguments through fields to zero-argument getters. CFG-aware def-use tracks
branches, switches, returns, stores, forwarded arguments, receivers and scalar transformations.

A bounded reverse graph classifies consumers, caches, activation scope and callsite outcomes.
Structural fingerprints and global one-to-one matching rediscover mappings across obfuscation
changes. Directed deep resolve reuses one DEX graph and streams each result to SQLite.

Production rules contain no obfuscated fixture names. Manual references and regression oracles can
describe expected evidence but cannot create an editable mapping. Precision is preferred over
recall: ambiguous, shared-infrastructure, unsupported object/callback and incomplete enum/Protobuf
paths remain read-only or inconclusive.

## Runtime

The Manager stores typed overrides through libxposed RemotePreferences and publishes profile schema
3 only for active, compatible mappings. The module validates package scope, profile schema, full
installed-build fingerprint, method descriptor and scalar type before installing a hook.

Supported ABI: BOOLEAN; byte/short/char/int-compatible INT; LONG; FLOAT; DOUBLE; STRING. The hook
uses protective exception mode and always falls back to the original implementation on failure.
The RemotePreferences listener is strongly retained for module lifetime. Updates replace one
immutable volatile snapshot, so concurrent hooks see either the complete old or complete new map.

API 101 has no structured reverse channel. `AAX_EVENT` records such as `PROFILE_ACCEPTED`,
`HOOK_INSTALLED` and `OVERRIDE_RETURNED` are available through LSPosed logs, not the Manager UI.

## Compatibility and rediscovery

Catalog identity covers Android Auto versionCode, the complete base APK and every split APK. A
profile is rejected when any artifact changes. Compatible saved overrides remain active; missing,
ambiguous or type-changed mappings stay stored but are suspended and excluded from publication.

Rediscovery compares the same stable key against the previous build. Unique literal mappings are
preserved, structural candidates are globally matched one-to-one, ambiguity is suspended and
removed keys are never rebound to unrelated keys.

Imported APKM/APKS files are read-only. Their current synthetic split filenames can make equivalent
archives with different ZIP ordering receive different fingerprints; this conservative false
incompatibility does not affect the installed runtime profile.

## Historical design notes

Historical design decisions and implementation milestones live in
[Alpha history](history/ALPHA_HISTORY.md) and `history/alpha-details/`.
