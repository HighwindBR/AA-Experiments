# AA Experiments alpha37 — occurrence-aligned callsites

Alpha36 stored one flow outcome per concrete DEX invoke but aligned it with `invokedMethods`, a deduplicated set used by the reverse call graph. Repeated calls shifted that positional mapping and produced 691 artificial `UNKNOWN` classifications on the tested build.

Alpha37 separates the two representations:

- `invokedMethods` remains deduplicated and compact for reverse-graph indexing;
- the flow analyzer preserves every ordinary method invocation in instruction order;
- duplicate outcomes are combined only after occurrence-level analysis, then aligned with the compact `invokedMethods` list;
- `invoke-custom` instructions without a normal `MethodReference` do not consume an ordinal;
- traversal considers every occurrence of the requested target and accepts a caller when at least one occurrence has a proven sink.

The occurrence list is temporary inside each method analysis. It is discarded before the method fingerprint enters the graph, is not serialized into the catalog and does not enlarge the ordinary cold scan.

Alpha36 entries with `valueFlowLimitation=UNKNOWN` are explicitly requeued, including entries that found an endpoint through another path, so stale diagnostic totals are replaced. The alpha37 strategy marker prevents repeated retries after the corrected pass.

General field/object expansion remains deferred until the corrected device distribution is available.
