# AA Experiments alpha30 — bounded deep-resolve batches

## Behavior

Alpha30 replaces one-key deep resolve sessions with bounded batches that reuse one DEX read and one structural graph.

- Heap up to 320 MiB: up to 3 pending mappings.
- Heap from 320 to 512 MiB: up to 4 pending mappings.
- Larger heaps: up to 5 pending mappings.
- Every mapping keeps the existing 512-node individual traversal budget.
- At least one mapping is attempted. Before every following mapping the resolver checks cancellation and preserves a 2 MiB or 1/128-heap safety reserve, whichever is larger.

The complete graph is never persisted. It exists only for the current batch and is released when the analyzer returns.

## Incremental safety

Each completed mapping is written to SQLite immediately. Cancellation takes effect between mappings, so it cannot interrupt one mapping's database update. If the memory reserve is reached or a later mapping fails, prior results remain committed and the catalog itself remains valid.

Diagnostics now shows the number of pending mappings, real `current of total` progress and a **Cancel after current** action. Already attempted mappings are excluded from subsequent batches.

## Validation

- Synthetic cancellation verifies that the completed result is returned and the next identifier is not started.
- A real three-key batch against Android Auto 17.8.663814 completes with a 256 MiB test-worker heap.
- Each result carries the directed budget and final resolved/inconclusive state.

