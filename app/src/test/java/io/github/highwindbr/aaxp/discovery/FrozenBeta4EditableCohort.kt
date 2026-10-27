package io.github.highwindbr.aaxp.discovery

import org.json.JSONArray
import org.junit.Assert.*
import java.io.File

/**
 * Exact beta3 (919) -> beta4 (942) regression oracle for the frozen AA 17.8 APKM.
 * Never replace the old fixture with a new production-derived snapshot.
 * See docs/BETA4_17_8_EDITABLE_DELTA.md for independent review evidence.
 */
internal object FrozenBeta4EditableCohort {
    private data class Extra(val type: DiscoveredType, val change: String)

    private val reviewedAdditions = mapOf(
        "CarActivityManager__use_pending_video_config_kill_switch" to Extra(DiscoveredType.BOOLEAN, "proto"),
        "CarAppLibrary__recreate_map_view_context_on_config_change_kill_switch" to Extra(DiscoveredType.BOOLEAN, "proto"),
        "ConnectivityLogging__disable_usb_trigger_configured_accessory_event" to Extra(DiscoveredType.BOOLEAN, "proto"),
        "ConnectivityLogging__disable_usb_trigger_configured_event" to Extra(DiscoveredType.BOOLEAN, "proto"),
        "FrameworkGalFeature__video_defer_early_ui_config_request_kill_switch" to Extra(DiscoveredType.BOOLEAN, "proto"),
        "PhoneThemeFeature__enable_dynamic_app_icon_shape_by_manufacturer_kill_switch" to Extra(DiscoveredType.BOOLEAN, "proto"),
        "SystemUi__dump_dhu_config_kill_switch" to Extra(DiscoveredType.BOOLEAN, "proto"),
        "SystemUi__process_font_weight_adjustment_configuration_change_kill_switch" to Extra(DiscoveredType.BOOLEAN, "proto"),
        "SynchronizedMediaFeature__audio_buffer_limit_parked_exp_ms" to Extra(DiscoveredType.LONG, "parked"),
        "SynchronizedMediaFeature__audio_buffer_limit_parked_exp_ms_tts" to Extra(DiscoveredType.LONG, "parked"),
        "SynchronizedMediaFeature__average_lag_weight_parked_exp" to Extra(DiscoveredType.LONG, "parked"),
        "SynchronizedMediaFeature__enable_sync_parked_exp" to Extra(DiscoveredType.BOOLEAN, "parked"),
        "SynchronizedMediaFeature__max_audio_delay_allowed_parked_exp_ms" to Extra(DiscoveredType.LONG, "parked"),
        "SynchronizedMediaFeature__max_lag_tolerance_parked_exp_ms" to Extra(DiscoveredType.LONG, "parked"),
        "SynchronizedMediaFeature__max_video_delay_allowed_parked_exp_ms" to Extra(DiscoveredType.LONG, "parked"),
        "SynchronizedMediaFeature__sync_interval_parked_exp_ms" to Extra(DiscoveredType.LONG, "parked"),
        "SynchronizedMediaFeature__sync_offset_override_parked_exp_ms" to Extra(DiscoveredType.LONG, "parked"),
        "VideoTimingControllerFeature__enable_video_timing_controller_parked_exp" to Extra(DiscoveredType.BOOLEAN, "parked"),
        "VideoTimingControllerFeature__video_timing_controller_max_delay_parked_exp_ms" to Extra(DiscoveredType.LONG, "parked"),
        "VideoTimingControllerFeature__video_timing_controller_min_delay_parked_exp_ms" to Extra(DiscoveredType.LONG, "parked"),
        "WirelessLatencyMonitor__probe_interval_ms" to Extra(DiscoveredType.LONG, "ssl"),
        "WirelessLatencyMonitor__report_interval_ms" to Extra(DiscoveredType.LONG, "ssl"),
        "WirelessLatencyMonitor__warning_threshold_ms" to Extra(DiscoveredType.LONG, "ssl")
    )

    fun assertExact(inventory: DexInventory, root: File) {
        val baselineJson = JSONArray(File(root, "fixtures/compatibility-17.8.663814.json").readText())
        val original = (0 until baselineJson.length()).map {
            baselineJson.getJSONObject(it).getString("key")
        }.toSet()
        assertEquals("Frozen beta3 reference unexpectedly changed", 919, original.size)
        assertEquals(23, reviewedAdditions.size)

        val editable = inventory.identifiers.filter { it.editable }
        val current = editable.map { it.key }.toSet()
        val expected = original + reviewedAdditions.keys
        assertEquals("Beta4 editable cohort size (919 + 23)", 942, editable.size)
        assertEquals(
            "Unexpected newly editable: " + (current - expected).sorted() +
                "; missing approved: " + (expected - current).sorted(),
            expected, current
        )

        for (identifier in editable.filter { it.key in reviewedAdditions }) {
            val extra = reviewedAdditions.getValue(identifier.key)
            assertEquals("Return type changed: ${identifier.key}", extra.type, identifier.inferredType)
            assertEquals("Getter resolution changed: ${identifier.key}",
                if (extra.change == "ssl") MappingResolution.STRUCTURAL else MappingResolution.DIRECT,
                identifier.resolution)
            assertNull("Reintroduced sensitive safety block: ${identifier.key}",
                SafetyPolicy.blockedReason(identifier.key))
            when (extra.change) {
                "parked" -> assertNotNull("Missing elevated warning: ${identifier.key}",
                    SafetyPolicy.elevatedRiskReason(identifier.key))
                "ssl" -> assertTrue(identifier.key.startsWith("WirelessLatencyMonitor__"))
                "proto" -> {
                    assertEquals(DiscoveredType.BOOLEAN, identifier.inferredType)
                    assertNotEquals("Boolean regressed to protobuf: ${identifier.key}",
                        IdentifierKind.PROTOBUF_CONFIG, identifier.kind)
                }
            }
        }
    }
}
