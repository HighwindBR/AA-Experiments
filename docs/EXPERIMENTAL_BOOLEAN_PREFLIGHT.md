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
tagged release. It uses `versionCode 145`, `versionName
1.0.0-beta4-preflight2` and the existing application ID, permitting
an update over beta3 without removing app data. The ordinary debug-signed APK
is not a compatible update.

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
6. Confirm `EXPERIMENT_HOOK_INSTALLED` and `OVERRIDE_RETURNED` with the
   selected value (or an error event) in the LSPosed/Termux log:
   
   ```sh
   su -c 'logcat -d -v time -s AAExperiments:V' |
     grep -E 'EXPERIMENT_|OVERRIDE_RETURNED|PROFILE_'
   ```

The return event proves the value was returned by the installed hook, **not**
that Android Auto visually changed. Test the UI separately, while parked.

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
