# AA Experiments alpha29 — bounded classification memory

## Reported failure

On-device rebuilds with the alpha28 resolver failed or closed the application during **Classifying experiments**. The original 256 MiB regression covered a clean scan but did not include an existing compatibility baseline.

## Root causes

Two independent allocations overlapped with the new DEX graph:

1. compatibility rediscovery loaded historical consumers even though structural matching only reads getter fingerprints;
2. the reverse-call worklist limited scheduled nodes to 256, but still retained evidence and traversed edges for every caller discovered after the queue was full. Sorting that unbounded evidence collection caused the observed `OutOfMemoryError`.

## Correction

- Compatibility baselines now contain only compact mapping identity and getter fingerprints. Historical occurrences, consumers, metadata and confidence narratives are omitted.
- Unique literal getters bypass global structural scoring. Only ambiguous same-key mappings enter the bipartite matcher.
- The consumer budget is now also a memory budget. A caller that cannot enter the bounded worklist cannot materialize evidence or graph edges.
- Resolver schema 15 and inventory cache v8 force a clean rebuild instead of accepting alpha28 classification output.

## Validation

The new regression performs two complete 17.8.663814 scans sequentially in the same 256 MiB test worker. The second scan receives the previous compact getter baseline, reproducing schema rebuild and cross-build rediscovery conditions. It completes without OOM and retains the expected 919 editable mappings.

