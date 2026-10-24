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

## Physical export validation — 2026-10-24 (preflight17)

The user's Settings/About screenshot confirms installed
`1.0.0-beta4-preflight17` (versionCode 163), and exposes all five
Data operations including the distinct complete recovery actions.

Four user-supplied exports were independently parsed in full. Files
were used solely for validation and were **not** committed:

| File | Size | Parsed identifiers | Result |
| --- | ---: | ---: | --- |
| `aa-catalog-all_discoveries.json` (with methods) | 14.98 MiB | 50,675 | Valid JSON, unique sorted keys |
| `aa-catalog-all_discoveries.json (1)` (without methods) | 13.28 MiB | 50,675 | Valid JSON, exact field match |
| `aa-catalog-all_discoveries.csv` | 6.10 MiB | 50,675 | Valid CSV, exact shared-field match |
| `aa-catalog-editable.json` (with methods) | 0.59 MiB | 870 | Valid JSON; exact matching subset |

Findings:
- All four files have **zero duplicated keys**, and the editable
  export matches all 870 corresponding full-catalog JSON objects
  **including getter and consumer arrays**.
- Full JSON, JSON without methods and CSV have exactly the same 50,675
  keys and 11 shared fields, including compiled defaults and overrides.
  No missing rows, reordered keys or shared-field mismatches.
- The complete export has 1,588 getter entries and 5,000 consumer
  entries, while editable contains 870 getters and 4,999 consumers.
  The extra consumer belongs to one noneditable Video Timing flag;
  this is consistent with the narrower EDITABLE scope.
- Exactly **100 normal overrides** occur in each export:
  95 BOOLEAN, 2 LONG, 2 STRING and 1 DOUBLE. No invalid scalar
  representation was identified.
- One noneditable raw technical identifier contains control characters
  including U+0000. JSON correctly escapes these characters, while
  CSV preserves them literally. The CSV parses successfully with a
  standards-compliant reader, but some spreadsheet programs may
  mishandle the embedded NUL. Treat this as a minor interoperability
  concern for raw ALL discoveries, **not** as lost export records.
- Existing GitHub Actions run
  [40060544570](https://github.com/HighwindBR/AA-Experiments/actions/runs/40060544570)
  completed with unit-tests, lint-release and debug-apk jobs passing.
  That validates the debug CI, not the signed build provenance.

**Round C export sign-off:** JSON and CSV full-catalog streaming,
editable filtering, column consistency and preserved normal overrides
PASS against real on-device data. This does not benchmark peak memory
usage, and no complete recovery bundle was supplied. Full recovery
write/restore behavior is **not yet device-validated**; a destructive
restore should only be exercised on an isolated test profile.
