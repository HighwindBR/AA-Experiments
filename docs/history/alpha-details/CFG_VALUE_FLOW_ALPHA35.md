# AA Experiments alpha35 — CFG-aware value flow and limitation taxonomy

Alpha34 eliminated brute-force exhaustion, but 704 mappings ended as `VALUE_FLOW_NOT_PROVEN`.
Alpha35 improves the proof engine and explains the remaining boundary.

## CFG-aware summaries

Invoke-result provenance is now evaluated with the real DEX control-flow graph. Register origins
are merged across conditional branches, jumps and loops. The analyzer also recognizes switch
conditions, array stores, unary conversions, comparisons and scalar arithmetic in addition to
returns, branches, field stores and forwarded arguments.

## Limitation taxonomy

When no supported value path is proven, metadata records edge counts and one dominant diagnostic
category:

- `LIKELY_DISCARDED_OR_UNSUPPORTED`;
- `FIELD_OR_OBJECT_FLOW`;
- `CALLBACK_OR_LAMBDA`;
- `COMPLEX_CFG_OR_UNSUPPORTED`.

These labels describe why the current static analyzer stopped; they do not claim that a feature
is dormant. Diagnostics shows aggregate counts so the next resolver expansion can be selected
from device evidence rather than guessed.

## Retry and UX

Alpha34 `VALUE_FLOW_NOT_PROVEN` entries are retried once with the CFG-aware strategy. Earlier
resolved results remain preserved. The deep-resolve button is disabled when the actionable queue
is empty.

The real directed fixture continues to pass with a 256 MiB test-worker heap.
