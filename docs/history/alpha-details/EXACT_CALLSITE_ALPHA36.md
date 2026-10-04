# AA Experiments alpha36 — exact callsite outcomes

Alpha35 proved that the remaining directed-resolution failures were mostly value-flow failures, but its diagnostic buckets were inferred from method-wide traits. A field referenced anywhere in a caller could therefore label every unresolved invocation in that method as field/object flow.

Alpha36 classifies the concrete invocation result instead. Each invoke occurrence has one compact outcome byte aligned with the existing invoked-method list. Duplicate calls to the same getter remain independent.

## Proven outcomes

- returned by the caller;
- used by a branch or switch;
- stored in an instance or static field;
- forwarded as a value argument to another call, including constructors and builders;
- used as a receiver;
- stored in an array or collection path;
- propagated through a scalar transformation.

## Inconclusive outcomes

- result not captured by `move-result`;
- captured result overwritten before a proven sink;
- captured result left unused;
- unsupported opcode touching the tracked register;
- origin made ambiguous by a CFG join;
- unknown/unclassified callsite.

The resolver records the exact counts in per-identifier metadata and shows the dominant inconclusive cause in Diagnostics. Unsupported opcode names are retained as bounded evidence. The representation is sparse and is built only for directed deep resolution, keeping the ordinary scan and the 256 MiB heap boundary unchanged.

Alpha36 deliberately does not implement general field/object expansion. The new evidence is intended to show which remaining flows actually need that heavier analyzer before it is designed.
