# Experimental BOOLEAN override — beta4 controlled test

This preflight is **not** the complete Advanced Overrides Mode. It unlocks only
`PhoneThemeFeature__enable_dynamic_app_icon_shape_by_manufacturer_kill_switch`.
No other experimental flags, protobuf editing, enum editing, or arbitrary Java
methods may be modified. Do not use while driving.

## Why this one?

A previous read-only probe on Android Auto 18.0 recorded both
`PROBE_HOOK_INSTALLED` for `adic.d()Z` and `PROBE_INVOKED` in the projection
process. That demonstrates getter interception and execution, **not that
flipping its result produces a particular visual effect**. The kill-switch
polarity has not been established.

## Update safely

On the `feature/beta4-getter-probe-preflight` branch, trigger the manual
**Build debug APK** workflow. That branch builds an **APK signed using the
existing release keystore**, available as a temporary artifact rather than a
tagged release. It uses `versionCode 147`, `versionName
1.0.0-beta4-preflight4` and the existing application ID, permitting
an update over beta3 without removing app data. The ordinary debug-signed APK
is not a compatible update.

## Preflight4: fixed DEX/Java class-name mismatch

The preflight3 LSPosed archive on Android Auto 18.0 showed a real call to
`adic.d()Z`, but the experimental override fell back with `CLASS_MISMATCH`.
The existing saved snapshot has a DEX class descriptor (`Ladic;`) whereas
the installed hook receives a Java binary name (`adic`). These identify
the same class; preflight4 now accepts the exact DEX or Java spelling
while retaining all other identity checks. **The saved experimental
snapshot and original baseline remain untouched**; no reset, overwrite,
or new confirmation is needed solely to migrate the class representation.

After updating to preflight4, disconnect projection, restart the Android
Auto process, and check LSPosed module logs for
`EXPERIMENT_SNAPSHOT_LOADED` with `VALID`, followed by
`EXPERIMENT_GETTER_INVOKED` and `EXPERIMENT_OVERRIDE_RETURNED`.
If the last event is absent, export the logs for another diagnosis.

## Activate

1. In **Settings → Diagnostics → Full catalog**, search for the exact key and
   open **View evidence**.
2. If the read-only probe is armed, disarm it and restart Android Auto first;
   the read-only probe and writable hook may not share a getter.
3. In the **Experimental BOOLEAN override** section, review the known default,
   the detected getter, and the existing normal override state.
4. Tap **Set TRUE** or **Set FALSE**, then review the explicit confirmation.
   Saving the first value takes an **atomic snapshot** of the original normal
   override (including when it was *absent*). A subsequent experimental edit
   keeps the original baseline unchanged.
5. Disconnect projection and fully restart the Android Auto process.
6. In **LSPosed → Logs → Module logs**, search for the exact flag or
   `EXPERIMENT_`. The LSPosed API 101/102 module log is **not guaranteed**
   to be mirrored into Android's ordinary `logcat`; a blank Termux grep
   does not demonstrate failure. Save/export the LSPosed logs after testing.
7. Look for these `AAX_EVENT` messages from the **same process/PID**:
   - `PROFILE_ACCEPTED` with `"experimental":"1"` — profile loaded;
   - `EXPERIMENT_HOOK_INSTALLED` — getter hook registered;
   - `EXPERIMENT_SNAPSHOT_LOADED` with `"status":"VALID"` — experiment
     snapshot was accessible and compatible at installation or invocation;
   - `EXPERIMENT_GETTER_INVOKED` — the hooked getter was called;
   - `EXPERIMENT_OVERRIDE_RETURNED` with `"source":"EXPERIMENTAL"`
     and `"value":"false"` (or `"true"`) — the experimental hook
     returned the selected value;
   - `EXPERIMENT_FALLBACK` with a `reason` — the original getter was
     executed instead, e.g. `ABSENT`, `DISABLED`, `BUILD_MISMATCH`,
     or `INVALID_VALUE`.

Each event/state is emitted at most once per process; changing the BOOLEAN
value produces a distinct return event. The logs contain only event names,
reason codes and the experimental BOOLEAN, never the raw preference snapshot.
`EXPERIMENT_SNAPSHOT_LOADED` is status telemetry, not an assertion that a
hooked getter executed.

Existing `HOOK_INSTALLED`, `ORIGINAL_RETURNED` and
`OVERRIDE_RETURNED` are emitted by the **normal** override path and must
not be confused with the new `EXPERIMENT_*` events. A past process may
still log its old normal hook until it is fully restarted.

A return event proves the hook returned the value, **not** that Android Auto
visually changed. Test appearance separately, while parked.

## Recover

- **Pause:** retains the baseline and value but disables publication of the
  experimental hook. Restart Android Auto to guarantee no old installed hook
  remains active.
- **Restore baseline:** atomically restores the original serialized normal
  override if one existed, or removes the override if the original state was
  absent. The experimental snapshot is removed. Restart Android Auto to
  restore the original hook layout.
- **Restore all overrides:** clears normal overrides and the experimental
  snapshot, so do not use it when you intend to keep the earlier baseline.
- After an Android Auto upgrade, the experiment is preserved but suspended
  because its fingerprint no longer matches. It cannot be applied to the new
  build automatically or restored into that build. Return to the original
  build if you need to restore the earlier baseline.

The experimental snapshot lives in the LSPosed remote preferences under a
separate key, and **is not part of the usual JSON export** in this preflight.
Do not uninstall AA Experiments or clear its data as a way of trying to
preserve the snapshot.

The runtime has an independent allowlist for the exact flag and BOOLEAN
descriptor. Unknown, malformed, disabled, or cross-build experimental
records fall back to the original getter via `chain.proceed()`.
