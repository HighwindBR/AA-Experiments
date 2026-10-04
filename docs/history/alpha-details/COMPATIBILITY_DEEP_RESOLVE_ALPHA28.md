# AA Experiments alpha28 — compatibility rediscovery and directed deep resolve

## Purpose

Alpha28 finishes the resolver work selected for this release: reuse structural evidence safely when Android Auto changes build, and allow an explicitly bounded second analysis when the ordinary consumer traversal is inconclusive.

## Compatibility rediscovery

- Rediscovery is anchored by the stable identifier key. A removed key is never attached to a differently named discovery merely because its obfuscated structure looks similar.
- On a resolver-schema rebuild, the previous catalog for the same APK hash is the baseline. On a new Android Auto build, the retained predecessor catalog is used.
- Preserved unique mappings remain preserved.
- An ambiguous current mapping is promoted only when global structural matching produces one compatible, unique result and the previous mapping was editable.
- Collisions, insufficient margins and missing keys remain suspended. The resolver fails closed instead of resurrecting an old hook.
- Obfuscated names are still test or diagnostic evidence, never production lookup rules.

The catalog records whether each configurable mapping was preserved, recovered by literal/structure, suspended or newly discovered. Diagnostics summarizes those results without loading the full catalog into the UI.

## Directed deep resolve

The ordinary scan keeps the consumer traversal budget at 256 nodes. Increasing that budget for every identifier was tested and rejected because it exceeded the 256 MiB process target.

Diagnostics now offers **Deep resolve next inconclusive**. One press:

1. selects one editable mapping whose ordinary consumer traversal reached its budget;
2. reuses the installed APK and DEX files;
3. analyzes consumers only for that identifier with a 512-node budget;
4. skips resource-catalog materialization;
5. updates only that identifier's runtime, confidence and evidence fields.

The operation never changes identity or editability merely because more graph nodes were visited. A successful retry is marked `RESOLVED`; otherwise it remains `STILL_INCONCLUSIVE`. Further items can be processed one at a time, keeping memory and latency explicit and bounded.

Imported archives are not eligible after their scan session ends because their temporary extracted files are intentionally deleted. Directed retry is therefore enabled only for the currently installed Android Auto package.

## Schema and cache

- Resolver schema: 14.
- Inventory cache generation: `inventory-r3-v7`.
- Installing alpha28 rebuilds older catalogs while preserving overrides in their separate store.

## Regression coverage

- A same-key ambiguous mapping can be recovered through unique structural correspondence.
- A disappeared old key is not attached to a different new key.
- Structural collisions remain suspended.
- A synthetic consumer chain that exceeds 256 nodes is resolved with the directed budget.
- The installed 17.8.663814 catalog still fits a 256 MiB test worker.
- A real single-key deep resolve also completes in a 256 MiB test worker and returns only the requested identifier.

