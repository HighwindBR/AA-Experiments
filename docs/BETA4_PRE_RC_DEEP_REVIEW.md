# AA Experiments — Deep pre-RC code and UI review

Date: 2026-10-23
Reviewed source: `feature/beta4-getter-probe-preflight`, commit `bbad5d6fe0e34d95ac571fe411b55797cbb1d0d9`
Stack: native Android / Kotlin + Jetpack Compose, LSPosed API 101-compatible manager/runtime, Java hook layer and on-device SQLite catalog.
Review lenses: safety, behavior/state transitions, resilience, privacy, performance, readability, navigation, accessibility, recovery, delivery.

**Scope of evidence:** static inspection of the branch source and existing tests,
the user-provided UI screenshots and earlier successful LSPosed beta4 smoke logs.
No new emulator screenshot suite, instrumented Compose run, failure injection, full Gradle/lint invocation, or production crash report was executed by this review.
An issue marked *risk* is supported by a control-flow path but has not necessarily been reproduced on device.
The findings below are recommendations **before** preparing a release candidate; no code behavior was changed in this review.

## Overall assessment

**Conditional NO-GO for RC until the P1 resilience issues are addressed.**
The architecture is viable: typed normal overrides, narrower build-pinned Advanced overrides,
build identity checks and suspended mappings behave coherently, and the normal
DOUBLE return was confirmed in a real AA process. The beta4 on-device log evidence
also confirms current profile publication, remote preference operation,
runtime hook returns and STRING log redaction. No per-flag behavioral A/B validation
is required.

The highest priority is **robust recovery after interruption and concurrent jobs**,
followed by a small number of **UI correctness and accessibility regressions**.
There is no justification for a Kotlin/Compose rewrite or a new database architecture.

## P1 — fix before RC

### P1-01: Profile publication can destroy the last valid profile on an interrupted write

**Source:** `app/src/main/java/io/github/highwindbr/aaxp/service/ServiceBridge.kt`,
`publishProfile` (remote file open → `truncate(0)` → write → flush).
Reader: `runtime/AndroidAutoModule.java:450-457`, `readProfile`.

**Evidence:** the currently active profile file is overwritten in place; no
staging file, atomic replace, last-known-good backup, checksum or rollback is
implemented. If publication is interrupted after truncation, the next process
may parse an empty/truncated file and fail to install mappings.
This is a **risk**, not a reproduced failure.

**Acceptance:** publication is atomic or has a proven rollback/last-valid strategy;
failure injection at truncation, halfway write and close leaves the last valid
profile loadable; runtime logs a distinct and actionable error.

### P1-02: Async catalog operations and profile refreshes lack a single publication owner

**Source:** `ManagerViewModel.kt:76-111` (service collector + installation refresh),
`:205-259` (archive import), `:430-467` (normal edit),
`:949-1091` (refresh/profile publication).

**Evidence:** multiple `viewModelScope.launch` entry points trigger
`refreshOverridesAndProfile()`, which captures manager state then performs
DB and remote preference work off the main thread. No actor, Mutex or
generation token serializes writes or rejects a result calculated for an
outdated installed build. A newer refresh can finish before an older one.
Concurrent scans also share mutable UI state.

**Acceptance:** serialize operations that publish profiles, associate
each result with a build fingerprint and monotonically increasing generation,
discard stale completions, and test rapid Apply/Default/reconnect/re-scan.

### P1-03: Long-running analysis foreground service has no multi-operation ownership

**Source:** `ManagerViewModel.kt:86-115`, `:207-259`,
`keepAnalysisAlive` near end; `AnalysisKeepAliveService.kt`.

**Evidence:** independent callers start/stop a single service using a Boolean,
not a reference count or unified work controller. One operation can stop the
service while another is running. Also, screen-off hangs are explicitly
documented as unresolved (`docs/KNOWN_LIMITATIONS.md:3`).
The service lifetime issue is a **concurrency risk**.

**Acceptance:** one serialized or reference-counted owner; cancel and process
death handling are deterministic; no premature stop with overlapping operations.
If screen-off reliability remains unsupported, preserve the current explicit
limitation and validate screen-awake behavior.

### P1-04: No pre-RC integrated test of the complete persistence/publication cycle

**Source:** `app/src/test/`, `.github/workflows/debug-apk.yml`,
`.github/workflows/private-release.yml`.

**Evidence:** many pure JVM tests exist, but no instrumented Compose
end-to-end suite or interruption/rollback scenario is present in the
reviewed tree. Signed preflight gates unit tests and lint, while the private
tagged-release workflow remains tied to beta3 (`private-release.yml:48-51,83-125`).
The reviewer did not independently retrieve the CI status of every run.

**Acceptance:** before beta4 tagging, exercise published-profile recovery,
normal import validation, pause/resume/restore, wrong-build rejection, and
manager/LSPosed reconnect on a representative environment. Update release
workflow names/conditions for beta4 without publishing an RC during review.

## P2 — address in RC polish if possible

### P2-01: "Effective value" conflates a configured override with a runtime observation

**Source:** `MainActivity.kt:989-1012`, `ExperimentDetailScreen`.

**Evidence:** the `Effective value` row simply chooses the manager's
stored override or compiled default; it does not read back a running AA getter.
Separately, the same panel says `Runtime observed: See LSPosed events`.
This wording can imply a runtime guarantee that API 101 cannot supply.

**Fix:** label the row **Configured value** and show `Runtime verification:
Not available in-app; inspect LSPosed events`. Distinguish
`Saved`, `Profile published`, `Hook installed` and `Value returned`.

### P2-02: Experiments header collides at narrow widths / larger fonts

**Source:** `MainActivity.kt:471-485`, screenshot from the user's beta4
Experiments tab ("900 shown · 100 normal modified · 30 Advanced · 3 suspended").

**Evidence:** one Row uses `Arrangement.SpaceBetween` with a title Column and
an unconstrained long Text, no flexible width allocation or responsive flow;
the actual screen capture shows visual collision.

**Fix:** make the title a full-width first line; move the count into a separate,
wrapping/compact line or adaptive FlowRow. Validate at widths 320–440 dp,
font scale 1.0/1.3/1.5/2.0 and long localized terms.

### P2-03: Static Lab Green is too dark for text in dark mode

**Source:** `MainActivity.kt:112-125`, `:875-885`,
`:1028-1040`, and `:1108` (`ValueRow`).

**Evidence:** the accent text is forced to `AaGreen = #006D3F`,
even when the dark theme defines primary `#72D99A`.
For example, the foreground `#006D3F` on the dark background
`#101510` contrasts at approximately **2.87:1** (WCAG AA
normal text expects >=4.5:1). Contrast is lower on lighter dark cards.

**Fix:** use `MaterialTheme.colorScheme.primary` for foreground text and
retain the dark green only as a surface/button background with proven
contrasting text. Add automated/manual contrast checks in both Lab Green
and Material You schemes.

### P2-04: Stale success/error messages persist and leak into unrelated screens

**Source:** `MainActivity.kt:484-497`, `ManagerViewModel.kt:55-59`
and many `message` assignments.

**Evidence:** `state.message` is a long-lived global string, not a
consumable UI event; `SaveState.SAVED` also remains after completion.
The screenshot showed "Advanced baseline restored ..." on the Experiments
listing even after a different navigation/action.

**Fix:** a `SnackbarHost` for transient, keyed result events;
persistent failure cards only for actionable errors; clear when changing
screens/build or beginning another operation. Keep technical failure details
accessible without false positive green styling.

### P2-05: Numeric editors offer no pre-submit validation or type-specific keyboard

**Source:** `MainActivity.kt:1028-1049`, `AdvancedOverrideEditor.kt:96-135`.

**Evidence:** the normal field is a single-line free-text control for
INT/LONG/FLOAT/DOUBLE/STRING, and Apply can be enabled for any nonblank
numeric string. Advanced's Review action is allowed before numeric
validation. Failures are deferred to ViewModel.

**Fix:** show inline finite/range errors using the same typed codec,
disable Apply/Review until valid; decimal/signed keyboard for numeric types
(with robust paste handling), multiline controls for STRING, and explicitly
differentiate empty string from Default.

### P2-06: Advanced BOOLEAN default selection can be implicit

**Source:** `AdvancedOverrideEditor.kt:31-44`.

**Evidence:** if `raw` is unknown or empty, `selected` falls back to
`"false"`, which may appear chosen before explicit user selection.

**Fix:** require an explicit TRUE/FALSE selection on a new Advanced record
with unproven default; keep the existing double confirmation.

### P2-07: Full-catalog export assembles an unbounded payload in memory

**Source:** `ManagerViewModel.kt:159-208` uses
`CatalogQuery(limit = Int.MAX_VALUE)`, JSON arrays / `buildString`,
and an `ExportPayload.content` retained in UI state.
`CatalogDatabase.kt:345-363` scans full getter/consumer tables even
when only a subset was requested.

**Evidence:** no streaming writer/backpressure/cancel is used for the
50k+ ALL discoveries export. Large exports may exceed the app's small
heap. This is a **capacity risk** rather than an observed crash.

**Fix:** stream JSON/CSV through a ContentResolver OutputStream in chunks,
query needed keys only (or page), offer progress/cancel, and limit
single-screen preview size. Preserve existing export schemas.

### P2-08: Standard backup is not a complete snapshot of runtime settings

**Source:** `ManagerViewModel.kt:903-959` and `:888-901`,
`MainActivity.kt:1500-1530`.

**Evidence:** normal active and suspended overrides are exported;
Advanced/Experimental records and their previous-normal baselines
are excluded. Global restore discards them. Recent UI warns about the
partial scope, but users still lack a one-click *complete* recoverable
backup.

**Fix:** either add a separately versioned complete backup with explicit
risk acknowledgment and exact-build validation, or maintain clear
partial-only semantics and a restore-baseline-first checklist.
Do **not** silently import old Advanced data into a new AA build.

### P2-09: Runtime status can overstate hook coverage

**Source:** `AndroidAutoModule.java:70-109,120-157`,
`Models.kt` `runtimeReadiness` and `StatusScreen`.

**Evidence:** per-method failures are caught locally, but
`PROFILE_ACCEPTED` is logged even if individual hooks were suspended.
`Dynamic profile published` proves file I/O, not that the new session
installed every hook or that behavior changed.

**Fix:** emit a per-process installation summary with counts
(attempted / installed / suspended / failed) and label the UI
`Profile published — runtime requires logs`, not `all applied`.
Reverse telemetry is absent, so do not fabricate live feedback.

### P2-10: "Diagnostic preflight" material dominates the user-facing engineering screen

**Source:** `MainActivity.kt:1620-1760` and
`IdentifierDetailsDialog` around `:1200-1360`.

**Evidence:** first-class cards for Video Timing, Advanced and 12-A DOUBLE
probes are interleaved with ordinary installation/runtime diagnostics,
and dated preflight labels remain after their respective validations.
The full-catalog dialog also exposes an older Advanced modal while the
Experiments tab uses a unified inline editor.

**Fix:** split into `Diagnostics` (read-only status),
`Developer tools` (optional probes/traces/handoff), and a single
Advanced detail workflow. Hide or archive completed preflight tools
unless enabled explicitly; never remove historical proof from docs.

### P2-11: Large code concentration limits UI iteration and testability

**Source:** `MainActivity.kt` (~140 KB), `ManagerViewModel.kt` (~70 KB).

**Evidence:** navigation, presentation, advanced editor integration,
scan process and profile persistence are highly concentrated.
A single global UI state carries transient messages, catalog, scanning,
restore and hook-publication results.

**Fix:** incremental extraction, not a rewrite:
`ui/status/`, `ui/experiments/`, `ui/settings/`,
`ui/diagnostics/`, `ui/components/`; use-case/service boundaries for
scan and publication with injectable data sources; snapshot tests for
screen layouts and reducer tests for transient state. This can follow
the RC if the P1 fixes are isolated and covered.

## P3 — desirable improvements

- Accessible, selectable full values when `ValueRow` currently truncates
  after two lines (`MainActivity.kt:1108`); add copy/expand.
- Add a brief first-run checklist for LSPosed scope, target build and
  optional read-only exploration; no sign-up required.
- Use one accessibility node for settings toggle rows; test TalkBack,
  large-font and landscape/foldable layouts.
- Use active theme colors instead of hard-coded warning/status hues.
- Update technical copy from "API 101" where it suggests an API 102
  connection is unsupported (API 101 is the minimum supported ABI).
- Consider `android:allowBackup="true"` (`AndroidManifest.xml:11`):
  review device-cloud backup contents, retention and sensitive manager
  metadata; no developer-operated upload is present in the inspected code.
- State explicitly which structured events are observational, and mark
  `Runtime observed` as unavailable in-app instead of a missing number.

## UI flow map and acceptance criteria

### Normal user loop

`Status` → `Experiments` (search/filter) → `Detail` →
`Default/TRUE/FALSE` or typed `Apply` →
`Saved` → `Profile published` (or error) →
restart target process only when hook set changes →
LSPosed verification if needed → `Restore Default`.

Acceptance: no action success before persistence succeeds; no value labeled
runtime-confirmed without runtime evidence; dark-mode readable; back navigation
restores previous list/filter/scroll; keyboard dismissed on successful Apply.

### Advanced loop

`Diagnostics` opt-in → `Experiments` ADVANCED badge →
same Detail page → risk review → typed `APPLY` or `PARKED APPLY` →
save baseline+record → publish profile → Pause/Resume/Restore baseline.

Acceptance: consent stays explicit, no accidental implicit Boolean default,
existing Advanced hooks are not silenced merely by hiding opt-in, recorded
original absence remains absent on Restore, cross-build records never auto-resume.

### Recovery loop

`Settings` → export normal backup (scope clearly labeled);
`Status` / `Experiments` → suspended audit;
per-flag Restore or special baseline Restore;
global restore only after explicit full-destructive warning.
No destructive tests on the user's live 100-override profile.

## RC exit gates

1. Fix or explicitly mitigate **P1-01**, **P1-02** and **P1-03**.
2. Add regressions for profile write interruption, stale-refresh ordering,
   and save/import/restore with reconnect.
3. Fix high-impact UI correctness **P2-01–P2-04** and numeric input
   feedback **P2-05**, then test accessibility/contrast.
4. Verify a complete signed candidate with Gradle unit tests, release lint,
   the existing private-fixture memory gates and a small representative
   runtime smoke test. **No per-flag functional A/B requirement.**
5. Update the beta3-only release workflow/release docs for beta4 RC,
   validate signing continuity, APK version metadata and release notes;
   make no tag or GitHub release until explicitly authorized.
6. Record remaining accepted limitations transparently in `docs/KNOWN_LIMITATIONS.md`.

## What is already working

- Build-scoped fingerprints include base and split APK hashes.
- Normal scalar overrides are validated by typed codepaths and now
  enforce narrowed INT ABI ranges and strict BOOLEAN/FLOAT/DOUBLE decoding.
- Advanced is allowlisted by policy/ABI and records baselines separately;
  unsafe symbols remain read-only.
- Suspended mappings are preserved across incompatible Android Auto builds.
- On-device beta4 logs confirmed normal hooks, DOUBLE value return,
  preflight getter probes and STRING event redaction.
- Compact pages for the complete catalog already use SQLite pagination;
  the unbounded path is specifically the export/attachment operation.
- The current UI remains entirely native Kotlin/Jetpack Compose.

## Round A implementation addendum — preflight14

Implemented in the beta4 development branch after the original review:

- **P1-01: code-level mitigation implemented, device validation pending.**
  `VerifiedProfilePublisher` publishes to the inactive remote slot and
  commits the slot pointer only after a matching read-back; failure leaves
  the old slot/legacy profile selected. The runtime reads the selected slot,
  checks schema and fingerprint as before, and can fall back to another
  valid file. Injection unit tests cover interruption, corruption, failed
  pointer commit and legacy migration. Actual libxposed remote-file
  reopen/fsync behavior still requires an on-device test.
- **P1-02: code-level mitigation implemented, integration test pending.**
  All `refreshOverridesAndProfile` operations and installed/archive
  build-state handoffs are serialized via a cancellation-safe coroutine
  mutex. Service state now uses `collect` rather than cancellable
  `collectLatest`. When preferences are temporarily unavailable, the
  manager preserves the existing file instead of publishing an empty one.
- **P1-03: code-level mitigation implemented, integration test pending.**
  An atomic single-owner gate covers the four scan/DEX operations and
  balances foreground service lifetime in `finally`. Progress updates
  are applied atomically, and exceptional exits reset UI loading state.
  Screen-off survival remains a documented limitation.
- **P1-04: tests added but not yet executed by Android Gradle.**
  Added pure tests for active-slot migration/rotation, fault injection,
  serialization ordering/unlocking, and analysis-owner exclusion.
  CI/lint/device integration are still release gates.

The addendum does **not** supersede the original evidence level: these
are implemented changes, not a claim that a preflight14 APK has been
built or that a physical LSPosed runtime has already validated them.

## Round B implementation addendum — preflight15

Source changes address the following previously reviewed UI findings:

- **P2-01:** `Effective value` is now `Configured value` and
  `Runtime observed` becomes `Runtime verification: Check LSPosed logs`.
  No fake read-back state has been introduced.
- **P2-02:** Experiments heading and summary now stack vertically
  and wrap naturally, rather than sharing an unconstrained Row.
- **P2-03:** Status/override foreground colors now use
  `MaterialTheme.colorScheme.primary` or matching content colors,
  including Material You and the dark Lab Green palette.
- **P2-04:** the main Experiments page no longer renders persistent
  `state.message` and `SaveState.SAVED` banners. The main scaffold
  uses transient Snackbar feedback when a new message arrives.
- **P2-05:** normal and Advanced editors use shared inline validation
  from the production `DynamicOverrideCodec`, disable invalid saves,
  show finite/range errors and select number/decimal keyboards.
  STRING retains valid empty values and multiline editing.
- **P2-06:** Advanced BOOLEAN with an unknown compiled default now
  requires an explicit True or False selection, rather than silently
  choosing False.
- **P2-10:** Advanced access remains directly visible in Diagnostics;
  historical DOUBLE probes and Video Timing Controller trace live
  behind a developer-tools affordance and surface when armed.

Filter toggles now expose a single accessible switch row with a
48dp-minimum touch target. Numeric/type-feedback unit regressions
were added. These changes do **not** assert that Compose layouts were
tested at 320dp, large font scaling, landscape or TalkBack: the
preflight15 Android build/QA step must verify those conditions.

## Round C implementation addendum — preflight17

- **P2-07:** catalog export switched from `Int.MAX_VALUE` in-memory
  materialization to direct SAF file output using bounded 250-row
  pages. Getters/consumers are now attached through page-key-constrained
  SQL instead of a build-wide scan for every page.
- **P2-08:** normal-only backup remains schema 3 and is imported exactly
  as before. A separate, checksum-protected, exact-build full recovery
  schema 1 exports normal/suspended, Advanced and Experimental records
  and their baselines, including a previous value's absence.
- Full recovery import requires the same installed AA fingerprint and
  versionCode, validates all stored scalar data and current-build
  special getter mappings, then commits managed-key replacement in one
  remote preferences transaction. It does not mutate the active
  profile file directly; the manager republishes after success.
- The UI separates ordinary import/export from full recovery and
  requires typed `RESTORE ALL` after a record/active-hook preview.
- Additional tests cover page boundaries, JSON framing, CSV escaping,
  exact-build rejection, checksum tampering, collision rejection,
  Advanced/Experimental baseline absence and skipped unrelated settings.

These are code-level implementations; performance measurements on the
actual ~50k AA 18.0 corpus and a harmless isolated-device recovery
round trip remain pending. No destructive test on live overrides
is required before opening Round D.
