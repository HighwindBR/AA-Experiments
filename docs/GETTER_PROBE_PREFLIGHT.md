# Read-only getter-probe preflight (beta4 development)

> **Historical test instructions, not the current installation guide.**
> The `preflight1` version and code listed below describe an early
> signed development artifact. Current beta4 builds use the version
> in `app/build.gradle`; the signed workflow still runs on the beta4
> feature branch, but its generated APK is not `preflight1`.
> Current Diagnostics groups optional probes under Developer tools.
> For the present workflow see [Development](DEVELOPMENT.md) and
> [Release process](RELEASE.md).

This preflight is **not** Advanced Overrides and cannot change an Android Auto flag.
It checks whether the selected getter can be hooked and whether the Android Auto
process actually calls it. Hook installation alone does not prove runtime use or a
visible effect. No getter return values are logged.

## Available test targets

- `PhoneThemeFeature__enable_dynamic_app_icon_shape_by_manufacturer_kill_switch` — BOOLEAN `()Z`
- `CarAppLibrary__recreate_map_view_context_on_config_change_kill_switch` — BOOLEAN `()Z`
- `WirelessLatencyMonitor__warning_threshold_ms` — LONG `()J`

The three targets are an exact allowlist in both the manager and runtime. Only a
uniquely resolved getter with the correct descriptor and no sensitive-policy
block may be armed. Only **one probe at a time** is saved for the exact
installed Android Auto build fingerprint; installing a different AA build
invalidates the selection. The existing signed build's normal override mappings
are not changed by arming a probe.

## How to test

1. Create a signed test update without publishing any release. Open
   **Actions → Build debug APK → Run workflow** on branch
   `feature/beta4-getter-probe-preflight` and run it. The branch automatically
   selects the signed preflight job instead of the normal debug build. Download the `AA-Experiments-beta4-probe-signed-...` artifact. The APK is
   inside the ZIP and is retained for one day. This uses the existing release
   signing secrets. The package version is `1.0.0-beta4-preflight1`
   (`versionCode 144`) so it can update beta3 without uninstalling.
   **Do not install the ordinary debug-signed APK over beta3.**
2. Keep Android Auto disconnected from the car while setting up the probe.
3. Open **Settings → Diagnostics → Full catalog**. Search for one of the keys
   above. Open **View evidence → Read-only getter probe → Arm read-only probe**,
   review the confirmation, and return to Status.
4. Restart the Android Auto process while safely parked, after ending any
   projection session. Merely restarting AA Experiments does not reinstall hooks.
5. Observe the event stream with ADB/Termux. Example:

   ```sh
   adb logcat -v time -s AAExperiments:I | grep -E 'PROBE_|PROFILE_|AAX_EVENT'
   ```

   Without ADB, in Termux with appropriate log-reading permissions:
   
   ```sh
   su -c 'logcat -d -v time -s AAExperiments:I' | grep 'PROBE_'
   ```

6. Record the build, tested key, and events. **PROBE_HOOK_INSTALLED** confirms
   class/method/descriptor resolution and hook registration. **PROBE_INVOKED**
   confirms at least one method entry during that process lifetime.
   Neither confirms that an override would change Android Auto behavior.
   **PROBE_REJECTED** / **PROBE_FAILED** mean the hook was not installed.
7. Use **Disarm probe** and restart Android Auto again. Disarming cannot
   unhook methods already installed in the running process.

The profile retains schema version 3. Its optional `probes` array has no
`preferenceKey` or override value, and the runtime always invokes
`chain.proceed()` on a read-only probe. This preflight does not implement raw
values, writable protobuf, advanced overrides, or unsafe reinjection.
