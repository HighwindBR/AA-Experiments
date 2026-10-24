# AA Experiments 1.0.0-beta4

> **Draft for maintainer review — not yet a published release.**

This beta improves the reliability and ergonomics of configuring
Android Auto experiments while preserving build-scoped safety checks.
It does not unlock every Android Auto or vehicle capability.

## Highlights

- **Advanced Overrides:** an explicitly enabled editor for a
  restricted, structurally verified subset of BOOLEAN, LONG, DOUBLE
  and STRING getters not eligible for the normal editor. The interface
  clearly labels risk, requires two confirmations, and preserves
  independent baseline snapshots for pause/resume/restore.
- **Expanded scalar validation:** stricter normal runtime decoding,
  correct DOUBLE compiled defaults, checked integer ABI ranges and
  redacted STRING return values in LSPosed event logs.
- **Safer runtime profiles:** verified two-slot remote-file publication
  with legacy-profile migration, serialized refreshes and improved
  failure recovery. Profiles still require an exact installed
  Android Auto package fingerprint.
- **Read-only diagnostic tools:** reviewed getter probes and Video
  Timing Controller trace, with a developer-tools section in Diagnostics.
- **More predictable UI:** theme-aware colors, adaptive Experiments
  layout, clear configured-versus-runtime terminology, accessible
  switch rows, inline numeric validation and transient feedback.
- **Large catalog export:** JSON and CSV stream 250 rows per batch
  instead of materializing more than 50,000 rows in app memory.
- **Complete recovery bundle:** optional exact-build backup of normal,
  Advanced and Experimental records and baseline absence. Existing
  normal-only backup files remain supported.

## Compatibility and safety

- Android Auto build identity covers package version and base/split
  APK hashes. Missing, ambiguous or incompatible mappings remain
  suspended rather than silently rebound.
- Advanced eligibility is not an unrestricted toggle for all catalog
  entries. Safety-sensitive flags, unresolved consumers and unsupported
  types remain read-only.
- An override return observed in LSPosed does not prove that a
  downstream app/server/head unit changed its behavior.
- Full recovery files contain raw STRING data, are **not encrypted or
  authenticated**, and should never be imported from unknown sources.
  They are exact-build recovery files, not cross-version migrations.
- Replacing a full recovery bundle is a destructive operation; its
  confirmation is explicit. A user should keep a separate backup
  before attempting restoration.
- Avoid applying/restarting projection while driving.

## Validation summary

Previous development preflight evidence includes Android Auto 18.0
real-device sessions with successful hooks and typed return events,
a real 50,675-item full-catalog JSON/CSV comparison, and a
20,147-byte complete recovery file export. The private fixture gates,
complete restoration in isolated storage, final signed RC package,
and signed-tag workflow require review **before publication**.

## Known limitations

- Full restore is restricted to the same exact Android Auto build;
  only an isolated/disposable profile should be used for destructive
  recovery QA.
- Large analyses should remain screen-on until completion.
- LSPosed API 101 has no Manager-side reverse runtime telemetry; use
  LSPosed logs for `PROFILE_ACCEPTED`, `HOOK_INSTALLED` and
  `OVERRIDE_RETURNED`.
- Raw catalog strings may contain unusual control characters;
  some CSV spreadsheet viewers do not handle these faithfully.

AA Experiments is independently developed and is not affiliated with
Google, Android Auto or LSPosed. It is GPL-3.0-or-later software.
See the root LICENSE, THIRD_PARTY_NOTICES.md and docs/KNOWN_LIMITATIONS.md.
