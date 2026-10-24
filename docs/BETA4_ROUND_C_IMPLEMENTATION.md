# Beta4 — Round C data, exports and recovery

**Version:** `1.0.0-beta4-preflight17` · `versionCode 163`
**Branch:** `feature/beta4-getter-probe-preflight`.
No main merge, tag, release or changes to the installed user's preferences.

## Decisions

1. Preserve the existing **normal-only schema 3 backup** and its import
   restrictions. Previously exported files remain supported.
2. Add a **separate complete recovery bundle** rather than silently changing
   the meaning of the familiar normal-backup buttons.
3. Complete restoration is deliberately stricter than import of normal
   preferences. It is an exact-build recovery operation, never a migration,
   and needs explicit confirmation.

## Streaming full-catalog export

The previous Export discoveries path constructed one unbounded in-memory
`JSONArray` or CSV string (possibly 50k+ rows) and stored it in
`ManagerUiState.exportPayload`. The new sequence is:

1. The user chooses scope, JSON/CSV and optional columns.
2. Storage Access Framework asks for a destination.
3. `CatalogExportWriter` writes directly to a buffered stream
   through SQLite pages of at most **250** identifiers.
4. Methods/consumers are fetched only for keys on each page.
5. A build identity change aborts the export; failures attempt to
   delete the partial document, otherwise it must be discarded.

JSON preserves the historical array of row objects and CSV retains
the original columns and quoting. O(250) row object memory is used
instead of O(total identifiers). Query count and total elapsed time
still depend on device/storage performance.

## Complete recovery envelope

- `kind: AA_EXPERIMENTS_COMPLETE_RECOVERY`, `schemaVersion: 1`
- Android Auto package, versionCode and exact build fingerprint.
- `entries`: raw manager-owned preference strings for
  `override.v2.*`, `override.name.*`, `advanced.v1.*` and
  `experimental.preflight1.snapshot`. These include the original
  normal preference value **or absence** captured before an Advanced
  or Experimental takeover.
- Canonical SHA-256 content checksum to detect accidental corruption.
- Limits: **2 MiB**, **4,096** records. Neither limit should be
  approached with the current ~100 normal overrides.

**Not included:** video/probe diagnostic state, hidden/filter UI state,
LSPosed scope, developer settings, profile files or any global device
configuration. The bundle can contain sensitive STRING values; it is
plaintext and not tamper-proof against a malicious editor.

### Guarded full restore

1. Open the document, enforcing the 2 MiB limit before parsing.
2. Require the exact package, schema, fingerprint and versionCode.
3. Verify checksum, recognized preference key namespace and typed values.
4. Reject conflicting normal and Advanced/Experimental hook ownership.
5. Show counts and a warning, then require typing **RESTORE ALL**.
6. Revalidate current-build Advanced/Experimental method fingerprints,
   policies and LONG handoff baselines against the installed catalog.
7. Replace *only* managed override keys through one RemotePreferences
   editor commit. Do not touch profile slot pointers or diagnostic state.
8. Recompute/publish the normal and special profiles. An AA process
   restart may be required to install or remove hooks.

A failed pre-commit validation leaves existing remote records unchanged.
A failure to republish after the commit must be investigated from
Diagnostics/LSPosed; it does not roll back a successful preferences commit.

## Tests and evidence

Regression tests:
- `CatalogExportWriterTest`: zero rows, CSV schema/escaping,
  511 records spread across three pages of <=250, valid JSON.
- `FullRecoveryBundleTest`: normal empty STRING round-trip,
  exact-build rejection, tampered checksum, exclusion of unrelated
  settings, Advanced/Experimental original absence round-trip,
  and ownership collision rejection.

**Still pending:** verified Gradle unit tests and lint against the
final preflight17 commit, signed build/installation, a real 50k-row
export with JSON/CSV parse checks, and full-restore testing only on an
isolated profile. The user's existing override configuration must
not be reset merely to satisfy QA.

## Next step

After the final CI checks pass, create/install a signed development
preflight17. On the device, first test **export only**:
- Full catalog → Export discoveries → ALL → JSON, preferably no
  methods/consumers initially.
- Open/inspect the resulting file size and JSON validity.
- Settings → Export full recovery bundle; verify the file exists.
- Do **not** tap Restore full recovery bundle on a live
  configured device without a deliberate need to recover data.

Then proceed to the release-engineering/RC acceptance round.
