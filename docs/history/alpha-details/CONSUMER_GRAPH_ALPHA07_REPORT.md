# Consumer graph alpha07

## Scope

Alpha07 adds bounded static consumer tracing on top of resolver schema 2. It does not promote new overrides and does not alter the LSPosed hook surface. The graph is diagnostic evidence used to distinguish a resolved getter from a value with an identifiable runtime consumer.

## Implementation

- Reverse index from invoked method descriptor to caller methods.
- Interface-dispatch recovery using each DEX class' implemented interfaces.
- Transitive wrapper traversal up to four call levels.
- Per-identifier evidence containing consumer method, DEX, link kind, depth and complete descriptor path.
- Runtime status persisted as `PRESENT`, `DORMANT` or `UNKNOWN`.
- Reviewed semantics stored separately from automatic graph evidence.
- SQLite schema 3 stores consumer evidence in a cold child table; the editable list does not eagerly render every path.
- Resolver schema 3 forces a one-time rescan while preserving overrides in remote preferences.
- Traversal is limited to hookable entries and explicitly reviewed diagnostic entries, with a 256-node safety bound and 24 persisted paths per identifier.

## UxPrototype golden path

On Android Auto 17.8.663814, the analyzer recovered the following path automatically:

`Ladbh;->b()Z` -> interface `Ladbg;->b()Z` -> wrapper `Ladbf;->d()Z` -> consumer `Lmrd;->b(Lqvz;Locn;)Lyjc;`

The static APK also contains `UxPrototype__url` and the compiled URL `https://youtu.be/dQw4w9WgXcQ?t=1`. The user-observed launcher entry and video launch validate the reviewed meaning recorded for this build:

- `true`: show the UX prototype launcher entry.
- `false`: hide the entry.

## Fixture results

For Android Auto 17.8.663814:

- 54,384 catalog entries.
- 954 editable entries.
- 936 editable entries with at least one consumer path.
- 18 editable entries with no consumer found within the bounded graph.
- 17,203 consumer paths retained before the final UxPrototype URL diagnostic pass.
- No discovery errors.

`DORMANT` is conservative: it means no consumer was found by this static bounded strategy, not proof that the value can never be consumed through reflection, native code, generated dispatch or a deeper path.

## Validation

- Synthetic direct/interface/transitive graph tests pass.
- Dormant classification test passes without changing editability.
- Normalized inventory JSON round-trip passes with consumer evidence.
- Three-version fixture export passes without out-of-memory failure after limiting the analysis working set.
- Full unit-test suite and `assembleDebug` pass.

## Next work

Expand reviewed semantics only for small, evidence-backed feature families (LauncherShortcuts, Gemini/Kitt, Cielo, ACE/Magic Cue, CoreMaps and parked-video gates). Add a details screen for all retained paths before attempting automatic branch-polarity inference.
