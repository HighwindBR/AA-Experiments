# AA Experiments alpha32 — targeted semantic traversal

Alpha32 adds a second, directed strategy for mappings left `STILL_INCONCLUSIVE` by alpha31. Existing results remain in the database and are explicitly requeued only when `targetedResolveAttempted` is absent.

## Traversal policy

- Activity, renderer and configuration-snapshot consumers are scheduled first.
- Wrapper-only paths remain traversable but have a per-node fan-out limit.
- Generic runtime/unknown callers have a separate bounded fan-out.
- Dump, logging, diagnostics, telemetry and test consumers are retained as limited evidence but are terminal and do not expand the graph.
- The existing 512-node global budget, streaming persistence, cancellation and memory reserve remain active.

## Honest outcomes

The resolver records one reason instead of treating every completed traversal as proven:

- `SEMANTIC_ENDPOINT_FOUND`
- `BUDGET_EXHAUSTED`
- `SHARED_INFRASTRUCTURE_FANOUT`
- `ONLY_TECHNICAL_CONSUMERS`
- `NO_CONSUMER_FOUND`
- `GENERIC_RUNTIME_PATH_ONLY`
- `MULTIPLE_SEMANTIC_ENDPOINTS`

Only one unambiguous semantic endpoint without budget exhaustion is promoted to `RESOLVED`. Every other outcome remains `STILL_INCONCLUSIVE`, now with a concrete explanation displayed in Diagnostics totals.

The resolver schema remains 15. Alpha31 catalogs are not rebuilt; their still-inconclusive rows become the alpha32 targeted retry queue.

