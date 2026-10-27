# Advanced Overrides Mode (beta4)

Advanced Overrides are opt-in and remain **more restricted than the full
catalog**. The actual beta4 preflight18–19 navigation replaced the older
preflight11 seven-tap unlock described later in this document.

## Current navigation (preflight18–19)

1. Open **Settings → Diagnostics → Advanced Overrides**.
2. Expand the card and choose **Enable Advanced Overrides**. Read and
   confirm the risk warning; the count of eligible getters is
   build-dependent.
3. Open **Experiments**: eligible Advanced entries appear alongside
   normal experiments and carry an **Advanced** indicator. Selecting
   an entry uses the current Experiment details flow, not the old
   Full Catalog-only editor route.
4. Read the getter/method evidence, change only the demonstrated
   scalar value type, then complete the editor's two confirmations.
5. Pausing, resuming and restoring Advanced records are separate
   operations. **Turning off the Advanced access switch only hides the
   extra editing access; it does not disable previously installed
   overrides.** The emergency pause control is on Status.
6. End projection and restart Android Auto, while parked, for changed
   hook installations. Verify installation/return events in LSPosed
   module logs; a returned getter value does not establish a
   downstream UI, server or head-unit feature.

The read-only getter/VTC tools are grouped under the collapsible
**Developer tools** area of Diagnostics, independently of the
Advanced access card. The Full Catalog still exposes evidence,
but is no longer the primary Advanced unlock pathway.

### Elevated-risk normal editor

The 12 exact reviewed `parked_exp` timing flags can also qualify
for the **normal** Experiments editor if they pass the usual getter
criteria. Starting with preflight20, that route displays an
`ELEVATED RISK` indicator and requires a typed `PARKED APPLY`
confirmation before saving a non-default value. The Advanced
editor retains its own risk confirmation. Restoring `Default`
does not require that additional acknowledgement.

## Historical preflight11 unlock and use (superseded)

The following procedure documents the earlier beta4 implementation
for reproducibility; **do not follow the seven-tap unlock instructions
on current preflights**.

1. Open **Settings → Diagnostics → Full Catalog**.
2. Tap the **Full Catalog** heading seven times. Read the explicit danger
   warning before unlocking. The extra controls are hidden until unlocked.
3. Search for a Fenotype flag and open **View evidence**. Under **Advanced
   Overrides**, inspect the eligibility diagnosis, then **Open advanced
   editor**. For a blocked symbol, the UI instead explains why it is read-only.
4. Edit a **BOOLEAN**, **LONG**, **DOUBLE**, or **STRING** value; no type
   changes are permitted. LONG is signed 64-bit, DOUBLE must be finite.
   Unknown numeric enum domains may accept raw LONG *only* if a unique typed
   getter has been proven, and the value remains explicitly unverified.
5. Confirm twice: review the key/default/type/confidence and finally type
   `APPLY` for ordinary Advanced entries, or `PARKED APPLY` for the
   **elevated-risk parked-mode A/V timing cohort**. The manager atomically
   saves the original override or original absence before writing any
   experimental value.
6. Fully restart Android Auto after publishing. Observe **LSPosed module
   logs** for `ADVANCED_HOOK_INSTALLED`, `ADVANCED_GETTER_INVOKED`,
   `ADVANCED_OVERRIDE_RETURNED` or `ADVANCED_FALLBACK`. Hook success
   does not guarantee a visible feature change.

The opt-in editor does **not** support unknown getter types, ambiguous
mappings, standalone enum literals, route states, protobuf objects, methods
with parameters, or safety-policy blocks. Known normally editable flags use
the ordinary Experiments screen.

## Persistence, recovery and build changes

Records reside separately under `advanced.v1.*` in LSPosed remote
preferences, scoped to an exact build fingerprint, method/ABI, and opcode
fingerprint. Records include the original serialized normal override and
name (or explicit absence), value, and timestamp.

- **Pause / Resume:** toggles one current-build record. Resume revalidates
  the method before publication.
- **Pause all Advanced:** pauses all current-build records atomically.
- **Restore baseline / Restore all baselines:** restores previous normal
  override bytes or removes the override when it had been absent.
- A different Android Auto build **suspends all old records**. They stay
  visible in the saved count, but are never automatically published or
  converted into a default. Reapplying across builds requires a dedicated
  reviewed migration path; this preflight intentionally fails closed.
- Existing beta4-preflight PhoneTheme BOOLEAN snapshots remain in their
  legacy storage location and retain their original restoration action.
  This experimental legacy path is deliberately not migrated automatically.

Regular backup/import does not silently include or reactivate unsafe records.
The manager rejects normal backup import while advanced baselines are retained,
to avoid overwriting their recorded previous state. A module reinstall,
data wipe, or framework change may make experimental backups unrecoverable.

## Safety policy: parked-mode media/video timing

`parked` no longer causes a blanket denial of all flags: the **12 exact,
manually scoped** `SynchronizedMediaFeature__*parked_exp*` (9) and
`VideoTimingControllerFeature__*parked_exp*` (3) identifiers found in the
Android Auto 18.0 inventory are allowed to proceed to the **unchanged**
getter/type/structural fingerprint checks. They carry an **elevated-risk**
warning and must be confirmed by typing `PARKED APPLY`.

This is not an all-family wildcard. Unknown/new `parked` identifiers
remain **blocked by default**, including
`CarSensorParameters__max_parked_speed_gps_sensor` and
`CarSensorParameters__max_parked_speed_wheel_sensor`, whose role involves
parking-state velocity thresholds. All other sensitive tokens still take
precedence. Allowing a parked-mode *timing* parameter does not claim the
setting is safe, that its bounds are valid, or that it is reached on Open
Headunit Self-Mode. Tests should start with a tiny change, with a known
baseline, when no real vehicle is moving.

## Technical boundaries

The runtime only hooks class/method/descriptors emitted by the scanner for
the exact installed AA build, requires a matching typed record, and calls the
original getter if validation fails. Logged STRING values are redacted.
The legacy normal profiles and the original beta3 runtime path remain
unchanged. This preflight is not a tagged release; install only an APK signed
with the existing release key after public CI succeeds and the manually
triggered signed-build workflow completes.

## VTC protocol diagnostics and focused Advanced consumer tracing (preflight7)

Android Auto 18.0.164204 DEX research showed that `Liyo.<init>(CarInfo)`
reads `CarInfo.e` as the HU protocol **major** version. The ordinary timing
branch is selected below version 6; at protocol major 6 or later, the
`parked_exp` timing settings may be selected when `Liyo.d()` runs.
Installing a getter hook does **not** prove this selector ran.

### Opt-in read-only runtime trace

In **Settings → Diagnostics → Video Timing protocol trace**, enable it
only for the inspected base APK (SHA-256
`cebd7c3c50885fb7dbb8fce202b5f33f0412c8f3801fac91a509da9482b9c60d`).
A full Android Auto process restart installs two *read-only* observers:
`Liyo.<init>(com.google.android.gms.car.CarInfo)` and
`Liyo.d()Lxxe;`. The hooks always call `chain.proceed()`, never modify
values, and check the remote trace-enabled preference at each invocation.

Relevant LSPosed module events are `VTC_TRACE_INSTALLED`,
`VTC_HU_PROTOCOL_OBSERVED` (`major`, `minor`, `expectedBranch`),
`VTC_SELECTOR_INVOKED` and `VTC_BRANCH_SELECTED`
(`branch`, `mode`, `normalFlag`). Logging omits manufacturer, model,
VIN, vehicle IDs, Bluetooth addresses and all personal data.
These events are deduplicated per process/state. The selector is
only a **possible route**; logging it does not prove that the timing
getter was invoked. Turn the diagnostic off after obtaining evidence
and restart Android Auto to unload hook installations.

Other AA builds are rejected, even when their class names look similar.
This is a diagnostic feature: it does not expose the protocol fields to
the editing UI.

### Trace consumers (one getter)

Within the unlocked **Open Full Catalog → View evidence → Advanced
Overrides**, use **Trace consumers · read-only** on a structurally
eligible noneditable Fenotype getter. This launches **one bounded
directed analysis**, using the installed APK's DEX and the existing
reverse-call-graph engine. Only that key is selected; the full
50k-identifier catalog continues to skip consumer analysis for
unreviewed read-only entries to preserve memory. Interface/wrapper
paths are included in the graph evidence when statically traceable.

The result updates **only** consumer evidence, runtime-consumer
diagnostics and related metadata in the catalog DB, not permission,
editability, compiled defaults, stored override baselines or getter
fingerprints. It is not runtime tracing and cannot guarantee that a
method runs in a vehicle. Large shared infrastructure fan-out may
still produce a budget-limited result.

### Test criteria

- Off by default; profile contains `videoTimingTrace=false`.
- Source build and method signatures verified before runtime hook
  installation.
- With a new AA session, observe actual HU protocol version and whether
  `Liyo.d()` was reached; no vehicle-specific fields logged.
- Run targeted consumer trace for
  `VideoTimingControllerFeature__video_timing_controller_min_delay_parked_exp_ms`
  and verify the saved evidence without affecting the existing LONG
  override or its baseline.
- Preflight release remains withheld until tests on the user's device.

### Directed consumer evidence: regression invariant

The one-key reverse-graph scanner requires a **proven value-flow edge**
before promoting a caller to a semantic consumer. A bare `invoke-*` in
an obfuscated DEX is not sufficient. The directed traversal tests use
`InvokeCallOutcome.BRANCHED` when the synthetic renderer demonstrably
branches on the getter's return. A separate negative test preserves
`VALUE_FLOW_NOT_PROVEN` and `DORMANT` for the same synthetic call with
unknown value flow. This avoids false claims that a method is actually
used just because a matching invocation is present.


## Full Catalog UI consistency — preflight8

Catalog search results can contain abbreviated getter evidence. A search
result no longer claims an identifier is "Read-only in advanced mode"
based on a partial record. Open **View evidence** to use the complete
getter/signature record for eligibility.

The editor now distinguishes the **stored normal-editor classification**
from the **current Advanced eligibility**: a legacy catalog reason
mentioning `parked` is not a denial when the current Advanced policy
approves the exact reviewed key.

The missing **Trace consumers · read-only** button is now wired to the
existing `ManagerViewModel.traceAdvancedConsumers` action under
**Open Full Catalog → View evidence → Advanced Overrides**. The analysis
examines only the selected getter, does not change the override baseline,
and reopens the details with updated consumer evidence. The option needs
an eligible full record and a connected installed-build context.

This change is UI-only relative to preflight7; it does not change
runtime hooks, existing preference records or the safety policy.

## Diagnostics UI wiring — preflight9

The preflight8 module contained the build-pinned `VideoTimingProtocolTrace`
runtime hooks and `ManagerViewModel.setVideoTimingProtocolTrace`, but
the switch had accidentally been omitted from `DiagnosticsScreen`.
The dedicated **Video Timing protocol trace · read-only** card now appears
in **Settings → Diagnostics**, directly after the DEX files section.
It displays **OFF**, **ARMED**, or **Unavailable** according to the
installed build and stored preference.

**Enable VTC trace** is shown only as actionable when LSPosed remote
preferences are connected and the installed AA base SHA-256 matches the
exact 18.0 base analyzed previously. When enabled, the user must restart
the Android Auto target process to install the read-only hooks. The card
also provides a **Disable VTC trace** action and lists the expected
structured LSPosed events.

This is a UI integration correction. It does not change the trace's
runtime conditions, timing override values, stored baselines or
the advanced safety policy. The prior 41 ms LONG record can remain
paused during protocol observation. Version:
`1.0.0-beta4-preflight9` (`versionCode 152`).

## Controlled LONG hook handoff — beta4 preflight10

This is a **temporary, single-flag validation path**, not a general
permission to edit already-approved normal getters through Advanced.
The only supported getter is
`Assistant__icon_experiment_variant` → `Laczh;->l()J`,
compiled default `0`, on the exact inspected Android Auto
18.0.164204 base (SHA-256 pinned in `LongHandoffPolicy.EXPECTED_BASE`).
The manager requires an already-stored, named normal `LONG:3`
override. Value changes of any kind are **disallowed**: experimental
return `3` must match the original normal value `3`.

### Safety model

- The original normal preference `override.v2.* = LONG:3` and name
  preference remain **unchanged** at every stage. This is deliberate:
  a running normal hook continues to return 3 until the target is
  restarted with a newly published profile.
- Arming an Advanced handoff saves a separate, build-scoped,
  getter-fingerprinted snapshot with
  `handoffMode=NORMAL_LONG_VALIDATION` and `previousOverride=LONG:3`.
  The Advanced profile receives the getter **instead of**, never in
  addition to, the normal mapping. A defensive runtime collision check
  refuses a profile carrying the same method in both lists.
- **Pause** leaves `LONG:3` unchanged and publishes the normal mapping
  for future AA processes. A previously installed Advanced hook
  returns the preserved normal `3` while the process remains alive,
  and logs `LONG_HANDOFF_BASELINE_RETURNED`, instead of falling back
  to compiled default 0.
- **Resume** rechecks the exact build, getter fingerprint and original
  normal preference, then publishes the Advanced mapping again.
- **Restore baseline** removes only the handoff snapshot, retaining the
  original normal preference byte-for-byte. Restore-all and Pause-all
  respect the same invariant.
- An AA upgrade never resumes an old handoff automatically. Build,
  getter identity and opcode hash checks remain mandatory.
- Normal Experiments editing is disabled while an Advanced handoff
  record exists, to prevent changing the original baseline.
- The specific `LONG:3` and name are validated again by the runtime
  on each Advanced call; a tampered/missing record falls back to
  returning the preserved normal 3 rather than default 0.
- The UI requires two confirmations, ending with typing `HANDOFF 3`.
  The feature is visible only within the previously unlocked
  **Full Catalog → View evidence** dialog for that exact identifier.

### Device acceptance

1. With the existing normal Assistant override `3` and the same
   Android Auto 18.0 build, arm the LONG 3→3 handoff, restart the target
   process, and verify there is **one** relevant runtime hook.
2. Confirm `ADVANCED_HOOK_INSTALLED`,
   `ADVANCED_GETTER_INVOKED` and
   `ADVANCED_OVERRIDE_RETURNED` with `type=LONG`, `value=3`.
   A normal `OVERRIDE_RETURNED` event alone does not prove Advanced.
3. Pause without editing the normal value. On a fresh AA process,
   confirm the normal hook returns 3, not 0. A stale Advanced hook
   may emit `LONG_HANDOFF_BASELINE_RETURNED` before restart.
4. Resume and verify advanced return again after target restart;
   restore the baseline and confirm the normal editor still shows 3.
5. The temporary permission should be removed or disabled before the
   final tagged beta4 release.

No APK or release is published by the code change alone.

## Preflight10.1 — recovered missing LONG handoff UI

The preflight10 feature was present in `ManagerViewModel.startLongHandoff()`,
`LongHandoffPolicy`, and the runtime, but the published `MainActivity.kt`
did not connect those controls to the Full Catalog. The result was a
successful CI build with no usable handoff panel. Fixed by explicitly
wiring the state, the one-key handoff panel and its two confirmation
dialogs into `IdentifierDetailsDialog`.

Navigate **Full Catalog → Assistant__icon_experiment_variant →
View evidence** while Advanced mode is ON. Beneath the normal
Advanced editor (which correctly remains read-only for editable flags),
a **LONG hook handoff** card should show normal `LONG:3` and
**Review LONG 3→3 handoff**.

The underlying hook policy and normal/Advanced records are unchanged.
Version `1.0.0-beta4-preflight10.1` (`versionCode 154`).

## Preflight11 — Diagnostics opt-in and unified Experiments

Advanced discovery and editing are now configured from the **collapsible
Settings → Diagnostics → Advanced Overrides** section. This is a
persistent UI-only opt-in in the manager's local preferences. Enabling
it requires reading a prominent risk warning once. The old seven-tap
unlock is removed. Turning the switch off **hides** Advanced entries
but never disables or deletes an existing active runtime record.

The **Experiments** list adds a build-scoped, lazily loaded subset of
non-editable Fenotype getters passing the actual
`AdvancedOverridePolicy.issue` check on the complete record. SQL
prefilters getter-bearing BOOLEAN/LONG/DOUBLE/STRING entries to avoid
loading all 50k catalog symbols into memory; the manager subsequently
validates safety, getter identity and type. Each result is marked
**ADVANCED** and is searchable/filterable alongside normal editable
experiments. Directly tapping an Advanced entry opens the existing
typed advanced editor, not the ordinary editor.

Normal entries retain their original controls and permissions.
Approved Advanced records are still isolated by build and getter
fingerprint, preserve prior values/absence and support Pause/Resume/
Restore; the Status emergency Pause-all remains available.
The Diagnostics section includes active, saved and published counts,
Pause-all and a confirmed restore-all action.

After the one-time opt-in, ordinary Advanced writes require a single
per-value risk confirmation; the exact reviewed elevated-risk parked
timing cohort still requires a second confirmation with
`PARKED APPLY`. No change to hook policy or unsupported types.
Version `1.0.0-beta4-preflight11`, `versionCode 155`.
DOUBLE/STRING physical interception remains a separate test criterion.

## Preflight11.1 — consistent Experiments details navigation

A user-reported UI regression was confirmed: opening an ADVANCED-tagged
card from **Experiments** still invoked `AdvancedOverrideEditor` as a
large modal instead of opening the full **Experiment details** page.

All Experiments cards now route through the same
`ExperimentDetailScreen`. The screen retains the common header,
value/evidence panels, raw identifiers, resolver confidence, and
Back navigation. For an Advanced candidate, it renders the **same
typed Advanced controls inline** inside the Override section, using
the existing component's `inline` presentation option.

The Full Catalog retains its research-oriented modal, but the two
surfaces use exactly the same Advanced form and confirmation logic.
Normal Experiments still use their unchanged editor and default
controls. Advanced values remain build/getter-scoped, requiring
their original safety policy checks, and still provide Pause,
Resume, and Restore baseline. Diagnostics opt-in remains a
presentation switch only: it does not disable saved hooks.

The Advanced page shows **ADVANCED** and Active/Paused labels,
experimental value and recorded baseline separately from the
compiled default. It does not claim an observed runtime return
based only on a stored override.

Version: `1.0.0-beta4-preflight11.1` (`versionCode 156`).

## Preflight12-A — DOUBLE runtime-activity qualification

The Android Auto 18.0 full catalog contains **28 Fenotype DOUBLE flags**.
**26** are already editable using the ordinary Experiments editor. The
**2 noneditable** DOUBLE getters are
`CarSensorParameters__max_parked_speed_gps_sensor` and
`CarSensorParameters__max_parked_speed_wheel_sensor`; both remain
read-only under the existing safety policy, since they are associated
with parking-state velocity thresholds. This round does **not** add
an override bypass or alter those thresholds.

The next required evidence is **actual invocation of a DOUBLE getter**
in a real Android Auto process. The new collapsible
**Settings → Diagnostics → DOUBLE getter activity · 12-A** card arms
a single *read-only* probe on the verified Android Auto
18.0.164204 base APK (SHA-256
`cebd7c3c50885fb7dbb8fce202b5f33f0412c8f3801fac91a509da9482b9c60d`).
Probe targets are deliberately fixed to exact getter ABIs:

| Target flag | Method | Compiled default (IEEE-754 decoded) |
| --- | --- | --- |
| `SystemUi__widescreen_aspect_ratio_breakpoint` | `Ladjy;->a()D` | 1.67 |
| `ProjectionWindowManager__expected_frame_time_deviation` | `Ladiu;->b()D` | 0.2 |
| `VideoEncoderParams__bitrate_adjustment_exponent` | `Ladll;->c()D` | 0.5 |

For each target, the manager independently validates the getter from
the installed catalog and disallows probing over an existing normal
override. The runtime also checks a hardcoded allowlist, exact
class/method/`()D` signature and base SHA-256 before installing
the observation-only hook, which always executes `chain.proceed()`.
No DOUBLE values are logged or replaced.

**12-A test sequence:** arm one target, restart the Android Auto
process, connect to Open Headunit, inspect
`PROBE_HOOK_INSTALLED` and `PROBE_INVOKED`, disarm, and select
another target only if no invocation occurs. No vehicle trip is
required to perform this first pass. A positive invocation **does
not yet prove a DOUBLE override**; the subsequent normal-editor
same-value hook-ownership test independently verified the typed
`OVERRIDE_RETURNED` event in preflight12-B (not an Advanced override).

Version `1.0.0-beta4-preflight12a`, `versionCode 157`.
No main merge or GitHub Release.


## Preflight12-B — DOUBLE compiled-default correction and qualification results

Three separate read-only tests on 2026-10-23 confirmed actual `PROBE_INVOKED`:
- `SystemUi__widescreen_aspect_ratio_breakpoint`, `adjy.a()D`, process `:projection`, 15:19:15.
- `ProjectionWindowManager__expected_frame_time_deviation`, `adiu.b()D`, process `:car`, 16:46:28.248.
- `VideoEncoderParams__bitrate_adjustment_exponent`, `adll.c()D`, process `:car`, 16:47:35.218.

Each event proves getter invocation only, not return-value modification or downstream visual effects.
The two uploaded LSPosed archives overlap and contain the latter two confirmations.

### Compiled defaults: raw IEEE-754 bits are not decimal values

Prior discovery stored a registration's `const-wide` bits as a decimal integer
even when the getter's return type was DOUBLE. For example,
`4610199830550355640` is the 64-bit encoding of `1.67`, and
`4596373779694328218` encodes `0.2`.
New scans now decode FLOAT/DOUBLE registration defaults only when
the recorded provenance is `INVOKE_ARGUMENT` or `REGISTRY_FLOW`.
`DIRECT_RETURN` values are already decoded; other origins remain unchanged.
Cached SQLite catalogs and imported inventory JSON are normalized on read,
so no APK re-scan or catalog deletion is required. Raw DEX occurrence evidence
and LONG flags remain untouched.

This correction affects display and the compiled-default catalog, not stored
overrides, getters, or existing runtime hook policy. The `Double value` input
continues to accept typed decimal input such as `1.67`, never the raw bit integer.

Next: disarm read-only probes; use the normal editor to apply a *same-value*
`1.67` override on the SystemUi DOUBLE getter. After restarting Android Auto,
`HOOK_INSTALLED` plus `OVERRIDE_RETURNED` will verify typed return
interception; it will not alone establish any UI effect. Restore Default after testing.

Version: `1.0.0-beta4-preflight12b` (`versionCode 158`).
No main merge or GitHub Release.

### Preflight12-B on-device validation — 2026-10-23

The user confirmed that an **existing saved catalog**, without a new DEX scan,
now displays `SystemUi__widescreen_aspect_ratio_breakpoint` default as
decimal `1.67` rather than raw `4610199830550355640`.
The regression fix was packaged as preflight12b (versionCode 158).

Full LSPosed export `LSPosed_20261023_175245.zip` confirms the
normal typed-override path (not an Advanced override):

| Timestamp (device local) | Process | Event | Evidence |
| --- | --- | --- | --- |
| 17:36:42.699 | `:projection` | `HOOK_INSTALLED` | `SystemUi__widescreen_aspect_ratio_breakpoint`, `adjy.a()D` |
| 17:36:42.716 | `:projection` | `PROFILE_ACCEPTED` | 100 normal mappings, 0 probes, 1 experimental, 0 Advanced |
| 17:36:42.812 | `:projection` | `OVERRIDE_RETURNED` | `SystemUi__widescreen_aspect_ratio_breakpoint`, value `1.67` |

The target hook was also installed in `:shared`, `:car`,
`:provider`, and `:watchdog`, but the confirmed return was
in `:projection`. The log segment includes zero
`PROFILE_REJECTED`, `PROFILE_FAILED`, `HOOK_FAILED`,
`HOOK_SUSPENDED`, or `OVERRIDE_REJECTED` events; this
is not a general crash-safety guarantee.

This establishes **normal `()D` getter interception and return of a
finite typed DOUBLE**. It does **not** establish an observable UI
effect, because `1.67` is the same as the decoded compiled default.
The log alone does not establish that the user later restored Default.

## Beta4 validation policy — representative runtime tests, not 900+ A/B tests

Decision (2026-10-23): **omit the proposed preflight12-C visual A/B
exercise** and do not require an observable functional effect for each
catalogued flag. Preflight12-B completes the DOUBLE qualification.
The goal is a reliable, type-safe, build-aware override engine; discovering
and proving the downstream UI or feature semantics of every registered
key is not a practical release criterion.

Four levels of evidence must remain distinct:

1. **Per-identifier static checks (automated):** typed getter,
   exact signature, nonambiguous mapping, fingerprint, build compatibility,
   known safety restrictions, and default-value decoding.
2. **Per-type regression tests (automated):** BOOLEAN, LONG, DOUBLE,
   STRING, INT, FLOAT and any other supported scalar type; validate
   serialization/deserialization, validation errors, publication,
   pause/restore and fail-open behavior where applicable.
3. **Representative on-device checks:** sample actual getter invocation
   and typed override returns for each runtime hook *mechanism* and
   primitive type, not every identifier. DOUBLE normal-editor proof
   exists via `OVERRIDE_RETURNED` for `SystemUi__widescreen_aspect_ratio_breakpoint`.
   Other types/mechanisms require their own evidence where not already
   established; do not infer universal success from a single flag.
4. **Optional functional A/B only when targeted:** high-impact features,
   newly introduced hook mechanisms, regression investigations, or
   failure reports. Never promote this to an all-keys release gate.

User-facing claims must distinguish **statically eligible / configurable**
from **runtime return observed** and **functional effect independently
verified**. A successful hook or emitted `OVERRIDE_RETURNED` alone
does not guarantee that the value wins all downstream gates/caches or
causes a visible change.

Continue beta4 by auditing *mechanism coverage* and remaining
unverified supported types, not by testing 900+ effects one at a time.
No new APK, version bump, main merge, or GitHub Release is needed
for this documentation decision.

## Beta4 mechanism audit — compatibility and fail-open hardening (2026-10-23)

The audit covers normal BOOLEAN/INT/LONG/FLOAT/DOUBLE/STRING scalar overrides,
the separate BOOLEAN/LONG/DOUBLE/STRING Advanced policy, profile publication,
baseline preservation and build-fingerprint compatibility. The verified
DOUBLE `OVERRIDE_RETURNED` remains representative evidence, not a guarantee
that any particular downstream feature changes.

Safeguards added to the shared normal override path:
- Strict boolean parsing; invalid text cannot silently return `false`.
- Non-finite FLOAT/DOUBLE rejection in the runtime, independently of
  manager-side validation. Out-of-range `INT` for byte/short/char getters
  is rejected consistently before publication; char runtime conversion
  does not wrap negative or oversized codepoints.
- Normal STRING getter values are redacted from LSPosed events, matching
  existing Advanced privacy handling.
- Backup import accepts only currently editable, type/ABI-compatible
  mappings. Noneditable entries are rejected instead of reported as applied.
- A single normal hook owns each exact target method. Aliases sharing one
  getter remain stored under `SUSPENDED_TARGET_COLLISION` instead of
  installing two competing normal hooks.
- An individual normal edit rechecks the current build's identifier and
  rejects edits while the same getter's read-only probe is armed.

No changes to baseline preservation, Advanced enablement, package fingerprint,
normal override storage schema, or existing profile version.
Full Android Gradle unit tests and signed build remain to be run before APK
installation. No functional test for every flag is required.

### Advanced editor confirmation consistency

Audit correction: the review dialog now **always** transitions to the
second, typed-acknowledgement dialog. All Advanced overrides require
`APPLY`; flags with elevated parked-mode risk require `PARKED APPLY`.
Previously ordinary Advanced overrides were written directly from the first
review dialog. No override eligibility, runtime method, or stored values change.

## Preflight13 packaging (pending Android validation)

Version metadata is now `1.0.0-beta4-preflight13` / `versionCode 159`
for the mechanism-audit hardening fixes and universal Advanced typed
confirmation. The signed APK build, full Gradle regression suite,
installation and release checks have **not** run yet.
