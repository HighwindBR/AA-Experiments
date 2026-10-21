# Advanced Overrides Mode (beta4 preflight9)

This beta4 work is an opt-in capability **inside Open Full Catalog**, not
an upgrade of the default Experiments editor. It does not treat all catalog
identifiers as writable flags.

## Unlock and use

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
