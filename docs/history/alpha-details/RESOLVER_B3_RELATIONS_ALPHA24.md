# AA Experiments alpha24 — semantic relationships and eligibility gates

## Implemented

- Added a conservative cross-identifier pass over proven consumer and decision paths.
- Added `CO_GATED_WITH` for flags whose values are branched within the same bounded decision region.
- Deliberately does not promote co-gating to `REQUIRES`, `MASTER_OVERRIDE` or `VARIANT_OF`; those meanings require dominance and outcome proof.
- Added mechanically proven eligibility gates for `Build.VERSION.SDK_INT`, `PackageManager.hasSystemFeature`, recognizable Boolean capability sources and transported configuration snapshots.
- Added semantic activation scopes for the next render, Activity construction and configuration snapshot.
- Kept the pass proportional to the bounded consumer evidence set rather than indexing every method in Android Auto.

## Real 17.8.663814 oracles

- Earth: `earth_enabled` and `earth_status` are co-gated and their direct consumer includes an SDK gate.
- Launcher Shortcuts: the framework and Assistant shortcut flags are co-gated in `AddAssistantShortcutActivity` without hardcoding that class in production.
- Satellite network status: both SDK and Android system-feature gates are recovered.
- CAL voice plate variants are grouped only when their branch decisions are locally adjacent.

Exact obfuscated names remain test outcomes only. Production uses getter paths, decision offsets, method categories, referenced framework fields and invoked framework methods.

## Precision controls

- Relationships are limited to decision paths no deeper than two consumer edges.
- Branches must be within a bounded instruction distance.
- Large decision groups and technical dump/logging/test consumers are rejected.
- Shared high-level infrastructure alone never creates a relationship.

## Still pending

- Dominance/outcome proof for strong `REQUIRES`, master/variant and threshold relations.
- String/numeric comparison operands and their accepted domains.
- Source routing, precedence, reevaluation triggers and negotiated snapshots.
- Generic lineage for non-Boolean secondary caches and asynchronous/inter-process propagation.
