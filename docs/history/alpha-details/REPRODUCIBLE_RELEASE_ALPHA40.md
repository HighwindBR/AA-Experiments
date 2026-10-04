# AA Experiments alpha40 — reproducible build and resilient analysis

Alpha40 removes machine-specific build assumptions and makes the release gates explicit. The
project compiles against Android 36 without an absolute `javac.exe` path. Debug builds use AGP's
disposable key by default; a restricted local builder may inject an optional disposable debug key.
Release signing is configured only through `AAXP_RELEASE_*` environment variables and the tagged
GitHub job restores its keystore from repository secrets.

CI now runs the regular unit suite, three independent 256 MiB process gates (installed scan,
compatibility scan and directed deep resolve), and a frozen 919-key full-corpus gate. A tag can
publish a release only after all of those gates pass and `apksigner` verifies the result.

Long DEX analyses run with a low-priority data-sync foreground service. The Activity may be
recreated or Android Auto may briefly disconnect while the screen turns off, but the ViewModel
analysis coroutine remains in the pinned manager process and the progress state is restored when
the UI returns. Device validation is still required because process-management policy varies by
OEM.

Compatibility scans release the undecorated catalog generation before consumer classification.
The reverse graph materializes evidence paths only for nodes that actually enter the worklist and
retains at most twelve independently classified paths per experiment, prioritizing semantic
endpoints. The clean scan, compatibility scan and directed scan all pass with a 256 MiB worker.

Raw resolver metadata and Diagnostics omit numeric counters whose value is zero. Boolean states,
enum values and meaningful healthy summaries such as `Discovery errors: 0` are preserved, so
absence means only that an optional numeric diagnostic has no occurrences.

Other corrections include density-aware popup placement, editable empty STRING overrides,
`CUSTOM` for non-boolean values in fixed-width pills, removal of unused inventory cache code and
stale localized strings, and current limitations/release documentation.

Required GitHub release secrets:

- `RELEASE_KEYSTORE_BASE64`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

