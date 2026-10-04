# Alpha14: multi-layer subgraph and global matching

## Implemented

- Name-independent method fingerprints cover the root and two neighboring call-graph layers.
- Both callees and callers contribute evidence; wrapper insertion/removal and a migrated edge do not require perfect graph isomorphism.
- Stable, non-obfuscated class owners can act as contextual anchors. Obfuscated class names are never production mappings.
- Candidate families are solved as bounded maximum-weight bipartite assignments instead of resolving each method independently.
- Every accepted edge is challenged by recomputing the optimum without it. If the score margin is insufficient, the edge remains ambiguous.
- Densely ambiguous components above the exact-solver bound are suspended rather than guessed.
- A class-family matcher handles symmetric method groups using local structure plus multi-hop external callers.

## Real regression oracles

The exact names below exist only under `src/test` and are not consulted by production code:

- 17.8 daily `Llzl.n()` resolves to stable `Lmav.n()`, not the locally similar `Llyt.q()`.
- The symmetric MessagingMetaCache family resolves globally as `Lmax -> Lmcg` and `Lmaz -> Lmce` using the surrounding stable view callers.
- A synthetic perfectly symmetric family remains `Ambiguous`.

The daily/stable provenance of the first oracle is explicit in the fixture test. It was initially (and incorrectly) attached to the 16.9 fixture; the test caught that provenance error before release.

## Runtime and memory boundary

The normal installed-APK scan remains on the alpha13 compact path. It does not retain the full method graph or run the cross-build matcher, so the 256 MiB regression is not reintroduced. The new matcher is production code intended for an explicit deep-rediscovery/migration operation, while the full-APK comparisons remain fixture tests on the development host.

This completes the core B2 algorithm. Persisted cross-build migration workflow and UI exposure belong to the later compatibility phase; protobuf and activation semantics remain B3.
