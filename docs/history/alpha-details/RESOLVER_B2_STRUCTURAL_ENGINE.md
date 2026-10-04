# Resolver B2 — structural engine, alpha12

## Implemented

- Instruction-level DEX control-flow graph using code-unit offsets, fallthrough, conditional branches, jumps and switch payloads.
- Worklist register provenance through control-flow joins for configuration registration.
- Ambiguous registration paths are suspended instead of being converted into a hook.
- Explicit field-to-return def-use proof. Merely mentioning a registry field is no longer sufficient to classify a method as its getter.
- Deferred field-reader analysis: only fields that were actually registered are analyzed, avoiding a CFG pass over every scalar method.
- Direct invoke-result-to-Boolean-branch detection, recording mechanical polarity without inventing product semantics.
- Reverse consumer traversal no longer stops at depth four. It uses a visited-node cost budget and records whether that budget was exhausted.
- Iterative strongly connected component detection identifies recursive consumer regions without risking JVM stack overflow.
- Diagnostic, telemetry and test-only callers no longer prove a production runtime consumer by themselves.
- One-hop call-subgraph similarity contributes to structural matching.
- A conservative batch resolver enforces one-to-one method assignment and suspends globally contested candidates.

## 17.8.663814 result

- B1 editable mappings: 954.
- B2 editable mappings: 935.
- Confirmed unchanged: 935.
- Promoted: 0.
- Suspended: 19.

The 19 suspended entries are numeric flags whose old registration association does not remain unique through the CFG. They are listed in `outputs/discovery/resolver-b1-to-b2.json`. This is an intentional precision-first result, not an attempt to preserve the old count.

The complete stable catalog contains 54,384 identifiers, 2,431 direct mappings, 124 structurally linked mappings, 199 ambiguous entries and no scanner errors. Of the 935 editable mappings, 917 have a non-diagnostic runtime consumer and 18 remain dormant.

## Performance

The three-version fixture export completed in 225.5 seconds including Gradle startup and test execution. Field-to-return analysis is filtered by the set of registered fields; it is not run blindly for every scalar method.

## Validation

- All 31 unit and fixture regression tests passed.
- The regression suite proves field-to-return filtering, mechanical branch polarity, traversal through eight wrapper levels, diagnostic-only dormancy and global method-reuse rejection.
- `assembleDebug` and APK Signature Scheme v2 verification passed for `1.0.0-alpha12`.

## Deliberately unresolved

- Mechanical branch polarity is not yet equivalent to effective feature semantics. A label such as `true -> corrected path` still requires audited downstream regions.
- The batch structural resolver is available and regression-tested, but runtime scanning still primarily rediscovers getters from stable literals in the currently installed APK.
- Full multi-method subgraph fingerprints and component-level optimal matching remain a further B2 refinement.
- Protobuf schemas, caches, activation scope, source routing and side effects belong to B3.
