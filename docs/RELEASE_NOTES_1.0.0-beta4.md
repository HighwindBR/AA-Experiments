# AA Experiments 1.0.0-beta4

> **Release notes for final-candidate review — NOT YET PUBLISHED.**
> Application ID: `io.github.highwindbr.aaxp`.
> Installed version: `1.0.0-beta4` / versionCode `169`.
> Unpublished signed test file: `AAExperiments-1.0.0-beta4-candidate.apk`.
> Approved official release asset, when published:
> `AAExperiments-1.0.0-beta4.apk`.

This beta improves reliability and ergonomics of Android Auto experiment
inspection, configuration and recovery. It does not unlock every
Android Auto, server or vehicle capability.

## What's new

- **Advanced Overrides:** opt-in editing of a strictly eligible,
  structurally verified subset of BOOLEAN, LONG, DOUBLE and STRING
  getters. Explicit selection, two-stage review, independent saved
  baselines, Pause / Resume / Restore and risk warnings are provided.
  Advanced entries use the current Experiment details UI.
- **Stronger compatibility:** the frozen Android Auto 17.8 fixture
  preserves all 919 beta3-editable keys and adds 23 individually
  reviewed keys: 12 parked-mode media-timing, three WirelessLatency
  false SSL matches and eight BOOLEAN config/manufacturer flags
  previously misclassified as possible Protobuf. All still require
  valid getter evidence; risky parked-mode timing values require
  the exact `PARKED APPLY` confirmation, including in the normal
  Experiment editor.
- **Typed override safeguards:** stricter normal-path decoding,
  correct DOUBLE compiled defaults, integer range checks and
  redaction of sensitive STRING returns in LSPosed logs. A fixed
  Advanced BOOLEAN selection bug now enables Review when True/False
  is chosen, without bypassing elevated-risk confirmation.
- **Runtime stability:** two-slot remote profile publication,
  serialized refreshes, profile migration and fail-open recovery,
  pinned to the precise installed base/split Android Auto fingerprint.
- **Read-only diagnostics:** reviewed getter probes and Video Timing
  Controller tracing under collapsible developer tools.
- **Clearer UI:** theme-aware surfaces, adaptive Experiments layout,
  clearer configured/runtime terms, input validation and feedback.
  The compact analysis dialog uses bounded current-stage progress
  without reserved blank rows or detached end dots, and avoids
  transient `Not found` messages while scanning. Old catalog
  safety-policy descriptions are marked historical.
- **Backup & recovery:** one Settings page prioritizes full recovery
  export/import, with legacy normal-only operations and the destructive
  reset in separate collapsed sections. The complete recovery bundle
  includes normal, Advanced and Experimental states and baseline
  absence; exact-build/type/ownership validation and typed
  confirmation protect restoration. Existing normal-only backup
  formats remain supported.
- **Large JSON/CSV exports:** stream SQLite results in 250-row
  pages instead of buffering 50,000+ records in the Compose UI.
  Backup imports have bounded file sizes.

## Validation and evidence

- The signed `1.0.0-beta4-preflight22` candidate passed public
  [CI 40325455245](https://github.com/HighwindBR/AA-Experiments/actions/runs/40325455245)
  and private [gates 40431677743](https://github.com/HighwindBR/AA-Experiments/actions/runs/40431677743):
  all three 256 MiB fixture regressions, non-skipped frozen 17.8
  full corpus, APK v2 signature and certificate match against the
  published beta3 APK. Later delivery-tooling and UI-acceptance
  changes passed [CI 40524090180](https://github.com/HighwindBR/AA-Experiments/actions/runs/40524090180).
- Preflight22 Android 17 / Galaxy S24+ screenshots established scan,
  Backup & recovery, Advanced BOOLEAN selection/confirmation and
  pause/restore **UI behavior**, not runtime getter-return evidence.
- Earlier signed preflight21 LSPosed logs separately established
  normal, Advanced and Experimental getter interception and typed
  returns on Android Auto 18.0. The preflight22 visual fixes did not
  modify the underlying runtime hook algorithms.
- The maintainer reported successful **normal backup restoration**
  and **complete recovery restoration** on 2026-10-26, then accepted
  preserved override state after the preflight22 update. No extra
  Status screenshots or export are required. Isolated disposable
  storage and exact byte-for-byte restored state were **not
  independently verified**.
- Additional test evidence includes a 50,675-item full-catalog
  JSON/CSV comparison and a 20,147-byte recovery-bundle export.
- **Still required before publication:** execute public and private
  regression gates and verify v2 signing/metadata/certificate on the
  final `1.0.0-beta4` / 169 commit; obtain explicit approval to
  merge/tag/publish; ensure the tag's own gates pass.

## Compatibility and limitations

- Advanced is not an unrestricted toggle. Unproven, unsupported,
  sensitive or incompatible mappings remain read-only/suspended.
  Getter interception alone cannot guarantee any head-unit, server
  or downstream feature will behave differently.
- Complete backup files can contain raw STRING data; they are **not
  encrypted or authenticated**, must be stored privately and must not
  be imported from untrusted sources. Full restore requires the exact
  matching Android Auto build and should only be destructively tested
  in isolated/disposable storage.
- Long scans can stall if the display turns off; keep the phone awake.
  LSPosed API 101 offers no Manager-side reverse telemetry, so check
  `PROFILE_ACCEPTED`, `HOOK_INSTALLED` and `OVERRIDE_RETURNED` in
  LSPosed logs.
- Some CSV viewers mishandle raw control characters. Do not perform
  overrides or restart projection while driving.

AA Experiments is independent GPL-3.0-or-later software, not
affiliated with Google, Android Auto or LSPosed. See LICENSE,
THIRD_PARTY_NOTICES.md and docs/KNOWN_LIMITATIONS.md.
