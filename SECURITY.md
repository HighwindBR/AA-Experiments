# Security

Report vulnerabilities privately to the repository owner before public disclosure. Include the AA
Experiments version, Android Auto build fingerprint, LSPosed/API version and relevant logs without
account secrets.

Hooks are Android Auto-scoped, fail open, validate the complete installed build and accept only the
declared scalar ABI. Missing, ambiguous or type-changed mappings are suspended. Sensitive key-name
matches remain read-only. “Editable” means structurally hookable, not safe for arbitrary values.
There is no automatic crash attribution on API 101; recovery is Restore all overrides or disabling
the module.
