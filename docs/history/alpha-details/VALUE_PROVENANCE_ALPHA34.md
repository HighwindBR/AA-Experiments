# AA Experiments alpha34 — interprocedural value provenance

Alpha33 reduced generic traversal but 709 mappings still exhausted the semantic corridor. A
call-graph edge alone does not prove that a flag value reaches the caller's decision. Alpha34
therefore records whether each invoked result is actually consumed.

## Proven sinks

While DEX instructions are available, the analyzer follows an invocation result through moves,
simple transformations and method calls. An edge is eligible for directed traversal only when
the value is:

- returned by the wrapper;
- tested by a conditional branch;
- written to an instance or static field;
- forwarded as an argument or receiver;
- used by a supported scalar transformation.

An invocation whose result is discarded is not a runtime consumer proof. If all candidate edges
are discarded or unsupported, Diagnostics reports `VALUE_FLOW_NOT_PROVEN` instead of spending
the entire graph budget.

## Heap policy

The proof is stored as a sparse list of invoked signatures, reusing strings already present in
the method fingerprint. It is collected only for directed deep resolve. Directed scans now also
materialize identifier occurrences only for requested keys, rather than retaining the complete
50-thousand-entry catalog beside the graph. The three-key real fixture passes with a 256 MiB
test-worker heap.

## Retry policy

Alpha34 queues the unresolved alpha32 `BUDGET_EXHAUSTED` and alpha33
`SEMANTIC_CORRIDOR_EXHAUSTED` results once. Existing resolved mappings and other honest terminal
outcomes remain unchanged.
