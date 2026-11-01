# Stable 1.0.0 promotion

Candidate: `maintenance/group-a-stable`, PR #14. Identity: `1.0.0` / `180`.
Official predecessor: beta5 / `178`; beta6 preflight used `179`.

## Device acceptance

On 2026-11-01 the maintainer confirmed successful beta6 checks of screen-off
scanning, data/override preservation and automatic media opening in the vehicle.
The supplied screenshots accepted the updated interface except for appearance
menus opening at the row center. The candidate restores a touch anchor and
retains the row-center fallback for keyboard/accessibility activation.
The final touch correction has not yet been visually rechecked on the device.
No independent claim is made for every route/player or a signed beta5-to-stable upgrade.

## Existing automated evidence

[Public CI #290](https://github.com/HighwindBR/AA-Experiments/actions/runs/41269981422)
passed for head `011e60d989c2783d27fea0d9c3c0f704ae4a68b9`: unit tests,
release lint, app/test APK compilation and Android 15/API 35 SQLite instrumentation
(`OK (3 tests)`). Local results: 284 JVM tests passed, 10 fixture-dependent or opt-in
tests skipped, eight public delivery tests are required after this promotion change.
This earlier result does not certify the final commit.

## Final candidate gates

- [ ] Public unit tests, lint, APK builds and the three SQLite tests on the final commit.
- [ ] Exact private fixture hashes; installed/compatibility/deep-resolve 256 MiB tests
      and frozen 17.8 full-corpus test actually executed.
- [ ] Signed candidate: package/version/code, v2 signature and beta5 certificate match.
- [ ] Final tag's own gated signing/publication run and published APK/checksum verification.

The stable workflow pins the official beta5 APK to SHA-256
`bd03c9dd388b148bdda2a933949e25056eaf504579197157af8b9bda3ec135e8`.
Publication status is determined by the official release and run evidence, not this checklist.
