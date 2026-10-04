# AA Experiments alpha38 — instance result origin

Alpha37 corrected occurrence alignment but still lost the identity of most instance getter calls. When a getter was invoked on an object produced by another call, the analyzer propagated only the receiver provider as the returned value's origin.

Alpha38 separates those concepts:

- the receiver is recorded as call context (`USED_AS_RECEIVER`);
- every non-void invocation always contributes its own callsite identity to its result;
- value arguments may continue through a call without replacing the current call identity;
- method-local origin sets are capped at 16; overflow is reported as CFG ambiguity rather than exhausting memory;
- value-flow limitation totals include only limitations that actually block a mapping, while secondary unproven edges remain available in raw metadata.

## Full-corpus release gate

A dedicated opt-in regression now performs directed analysis for all 919 editable mappings in Android Auto 17.8.663814. The baseline key list is streamed from disk so the test itself does not inflate the heap.

Validated with a 256 MiB test-worker heap:

- completed: 919 / 919;
- semantic endpoint found: 70;
- generic runtime path only: 749;
- multiple semantic endpoints: 84;
- shared infrastructure fan-out: 6;
- only technical consumers: 4;
- value flow not proven: 6;
- blocking CFG ambiguity: 5;
- blocking unsupported opcode: 1;
- blocking unclassified callsite: 0.

This gate must pass before an alpha38 artifact is produced. General field/object expansion remains outside this build.
