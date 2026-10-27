# Frozen Android Auto 17.8 editable delta — beta3 → beta4

## Evidence and outcome

The private WSL runner executed the installed-scan gate against the
same SHA-256-verified `aa-17.8.663814.apkm` fixture used in the public
beta3 release. Run
[40142250027](https://github.com/HighwindBR/AA-Experiments/actions/runs/40142250027)
failed an assertion expecting 919 editable identifiers. The observed
value was **942**, with no `OutOfMemoryError`.

The follow-up diagnostic compared the current editable keys against
the **original 919-key frozen compatibility fixture**:

- 919 existing editable keys retained;
- 23 newly editable keys;
- 0 previously editable keys removed.

The 23 are accounted for by the following **three independent beta4
changes**, compared directly against the beta3 code. This is a
classifier/safety-policy reconciliation, not a claim that every
overridden value produces a visible effect in Android Auto.

## A. Twelve parked audio/video timing knobs

The beta3 `SafetyPolicy` blocked any key containing `parked`.
Beta4 explicitly exempts **only 12 audited media/video timing keys**;
unreviewed parking-state/speed/restriction keys remain blocked.
They all have a single DIRECT getter in the diagnostic output.

| Exact identifier | Type |
| --- | --- |
| `SynchronizedMediaFeature__audio_buffer_limit_parked_exp_ms` | LONG |
| `SynchronizedMediaFeature__audio_buffer_limit_parked_exp_ms_tts` | LONG |
| `SynchronizedMediaFeature__average_lag_weight_parked_exp` | LONG |
| `SynchronizedMediaFeature__enable_sync_parked_exp` | BOOLEAN |
| `SynchronizedMediaFeature__max_audio_delay_allowed_parked_exp_ms` | LONG |
| `SynchronizedMediaFeature__max_lag_tolerance_parked_exp_ms` | LONG |
| `SynchronizedMediaFeature__max_video_delay_allowed_parked_exp_ms` | LONG |
| `SynchronizedMediaFeature__sync_interval_parked_exp_ms` | LONG |
| `SynchronizedMediaFeature__sync_offset_override_parked_exp_ms` | LONG |
| `VideoTimingControllerFeature__enable_video_timing_controller_parked_exp` | BOOLEAN |
| `VideoTimingControllerFeature__video_timing_controller_max_delay_parked_exp_ms` | LONG |
| `VideoTimingControllerFeature__video_timing_controller_min_delay_parked_exp_ms` | LONG |

**Safety follow-up:** these can now enter the normal editor, where
Advanced-only `PARKED APPLY` warnings were not enforced. The beta4
normal editor was updated to show the elevated-risk warning and
require typing `PARKED APPLY` for every non-default normal value.
Returning to `Default` remains available without that confirmation.
This is a UI-layer gate, not a claim of verified safe numeric ranges.

## B. Three WirelessLatency false SSL substring matches

Beta3 blocked the lowercase substring `ssl`, which appears across
letters in `WirelessLatencyMonitor`. Beta4 tokenizes `SSL`/`TLS`
by acronym boundaries while still blocking real SSL/TLS controls.

All three are LONG with `STRUCTURALLY_VALIDATED/STRUCTURAL` getters:

| Exact identifier |
| --- |
| `WirelessLatencyMonitor__probe_interval_ms` |
| `WirelessLatencyMonitor__report_interval_ms` |
| `WirelessLatencyMonitor__warning_threshold_ms` |

## C. Eight BOOLEAN config/manufacturer flags

Beta3's `ProtobufSemanticAnalyzer` attempted to interpret defaults
as Base64 when a key contained `config` or `manufacturer_`.
A scalar BOOLEAN default such as `true` can resemble Base64 but is
not a Protobuf. This could incorrectly demote an otherwise proven
BOOLEAN to a read-only `PROTOBUF_CONFIG`. Beta4 only attempts
Protobuf default decoding for STRING/BYTES.

All eight are BOOLEAN with `UNIQUE_GETTER/DIRECT` evidence:

| Exact identifier |
| --- |
| `CarActivityManager__use_pending_video_config_kill_switch` |
| `CarAppLibrary__recreate_map_view_context_on_config_change_kill_switch` |
| `ConnectivityLogging__disable_usb_trigger_configured_accessory_event` |
| `ConnectivityLogging__disable_usb_trigger_configured_event` |
| `FrameworkGalFeature__video_defer_early_ui_config_request_kill_switch` |
| `PhoneThemeFeature__enable_dynamic_app_icon_shape_by_manufacturer_kill_switch` |
| `SystemUi__dump_dhu_config_kill_switch` |
| `SystemUi__process_font_weight_adjustment_configuration_change_kill_switch` |

## Release regression gate

`FrozenBeta4EditableCohort` is a **test-only** exact allowlist of
these 23 reviewed identifiers, each with its observed type and
resolution. Both `InstalledHeapRegressionTest` and
`InstalledCompatibilityHeapRegressionTest` must compare the
complete set of editable names to `original 919 ∪ reviewed 23`.
A new, missing, mistyped or unexpectedly remapped key must still
fail the private gate. The old beta3 919-key fixture is unchanged;
neither the runtime nor production resolver reads this allowlist.

**Pending acceptance:** rerun all four private gates with the
self-hosted WSL runner, review any unexpected compatibility
differences and visually confirm the elevated-risk normal editor
with a signed development APK. No official beta4 release, merge or
tag is authorized by this ledger.
