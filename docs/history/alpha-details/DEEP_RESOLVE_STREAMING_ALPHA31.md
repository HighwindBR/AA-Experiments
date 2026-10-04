# AA Experiments alpha31 — continuous streaming deep resolve

Alpha31 removes the fixed three-key session limit. A directed session now reads and indexes the installed DEX files once, then consumes the complete pending queue while memory remains safe.

Each mapping is semantically finalized, committed to SQLite and discarded before the next one starts. The returned diagnostic inventory intentionally retains no per-key results in streaming mode. This keeps memory approximately flat even when dozens of mappings share one prepared graph.

The session stops when the queue finishes, the user requests cancellation, or the safety reserve remains unavailable after a garbage-collection opportunity. Completed rows remain committed. Starting another session rebuilds the graph but selects only mappings still pending.

Compatibility inputs are also loaded in compact getter-only form for the complete queue; historical consumer graphs are never attached.

The progress dialog reports the real position in the original pending queue and supports **Cancel after current**. The completion message distinguishes resolved, still-inconclusive and remaining mappings.

