# Runtime

The Manager stores typed overrides through libxposed RemotePreferences and publishes profile schema
3 only for active, compatible mappings. The module validates package scope, profile schema, full
installed-build fingerprint, method descriptor and scalar type before installing a hook.

Supported ABI: BOOLEAN; byte/short/char/int-compatible INT; LONG; FLOAT; DOUBLE; STRING. The hook
uses protective exception mode and always falls back to the original implementation on failure.
The RemotePreferences listener is strongly retained for module lifetime. Updates replace one
immutable volatile snapshot, so concurrent hooks see either the complete old or complete new map.

API 101 has no structured reverse channel. `AAX_EVENT` records such as PROFILE_ACCEPTED,
HOOK_INSTALLED and OVERRIDE_RETURNED are available through LSPosed logs, not the Manager UI.
