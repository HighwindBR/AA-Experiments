# AA Experiments alpha33 — semantic corridor deep resolve

Alpha32 demonstrated that directed resolution could find real endpoints, but 723 of 751
attempts exhausted the shared 512-node worklist. Alpha33 changes traversal strategy rather
than increasing that limit.

## Resolver changes

- Semantic endpoints (`Activity`, renderer and configuration snapshot) are terminal.
- A reusable semantic corridor is derived structurally from endpoint-to-callee relationships.
- Callers inside that corridor are visited before generic infrastructure.
- Semantic-corridor and fallback work have separate limits and outcome reasons.
- Dump, logging, diagnostics, telemetry and tests remain terminal evidence.
- Endpoint evidence is retained ahead of shallow generic callers when the 24-item display cap
  is applied.
- Existing alpha32 `BUDGET_EXHAUSTED` results are queued once for the alpha33 strategy; the
  three already resolved mappings and non-budget outcomes are preserved.

No obfuscated Android Auto name is used as a production rule. The corridor is rebuilt from
the installed DEX graph, so it can adapt to later builds.

## Memory policy

The corridor is computed with bounded linear passes instead of materializing a second complete
caller-to-callee index next to the reverse graph. This intentionally trades CPU time for lower
peak memory on the Android 256 MiB heap.

## Outcomes

Diagnostics distinguishes `SEMANTIC_ENDPOINT_FOUND`, `SEMANTIC_CORRIDOR_EXHAUSTED`,
`FALLBACK_BUDGET_EXHAUSTED`, shared fan-out, technical-only, no-consumer, generic-only and
multiple-endpoint outcomes.
