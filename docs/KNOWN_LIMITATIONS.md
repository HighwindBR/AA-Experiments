# Known limitations

- Static proof does not guarantee that a visible feature will activate. Server eligibility, account state, negotiated capabilities, SDK/hardware gates, head-unit support and remote services can still prevent an effect.
- LSPosed API 101 has no reverse telemetry channel. Runtime confirmation still requires `PROFILE_ACCEPTED`, `HOOK_INSTALLED` and `OVERRIDE_RETURNED` in LSPosed logs.
- Automatic crash-loop attribution is not implemented. Hooks fail open, profiles are tied to the complete installed-build fingerprint, and incompatible overrides are suspended before publication.
- Deep value-flow analysis intentionally suspends ambiguous, shared-infrastructure and unsupported object/callback paths instead of guessing.
- Numeric enums and Protobuf messages remain read-only until their complete accepted domain or schema is proven.
- Resources are catalogued for diagnostics but are not directly editable through the modern API 101 runtime path.
- A foreground data-sync service protects an active scan against screen-off and transient Activity recreation. A force-stop, reboot or OS process termination still interrupts work; catalog writes and directed results already committed remain preserved.
- Release signing requires the CI secrets documented in the workflow. Debug builds use an automatically generated disposable debug key.
