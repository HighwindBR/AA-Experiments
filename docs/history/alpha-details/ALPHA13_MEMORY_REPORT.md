# Alpha13 — 256 MiB scan memory fix

## Root cause

Alpha12 retained instruction objects for scalar methods until all registered fields were known. During consumer analysis it also allowed duplicate interface/inheritance paths to accumulate in the queue and loaded roughly 32,000 compiled resources before the DEX graph was released. The combined peak exceeded the manager process limit of 256 MiB.

## Changes

- Field-to-return analysis now runs while each method is available and retains only compact proof, never the instruction list.
- Duplicate graph targets are scheduled once, using their shortest BFS path.
- The consumer cost budget is enforced while scheduling, preventing a high-fanout node from filling an unbounded queue before the traversal limit is checked.
- The traversal remains depth-independent; the budget is by visited/scheduled nodes and still passes the eight-wrapper regression.
- Intermediate identifier, field-proof and method-graph structures are cleared as soon as their phase finishes.
- `resources.arsc` and packaged resource names are loaded only after DEX consumer analysis and after the method graph has been released.
- A dedicated installed-build regression can run under a 256 MiB test-worker heap.

## Validation

- A complete 17.8.663814 APKM scan, including splits, 54,000+ catalog entries, 935 editable mappings and consumer analysis, passes with `-Daa.test.heap=256m`.
- Multi-version comparison tests continue to use a larger heap because they intentionally retain two or three complete inventories simultaneously; the Android manager scans only one installed build at a time.
