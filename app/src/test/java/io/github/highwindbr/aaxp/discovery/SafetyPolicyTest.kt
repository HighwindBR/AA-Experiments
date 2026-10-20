package io.github.highwindbr.aaxp.discovery

import org.junit.Assert.*
import org.junit.Test

class SafetyPolicyTest {
    @Test fun sensitiveControlsAreBlockedButOrdinaryUiFlagsAreNot() {
        assertNotNull(SafetyPolicy.blockedReason("AppValidation__signature_check_enabled"))
        assertNotNull(SafetyPolicy.blockedReason("video_while_driving"))
        assertNotNull(SafetyPolicy.blockedReason("SenderlibCertFeature__backup_key_raw"))
        assertNull(SafetyPolicy.blockedReason("CieloFeature__earth_enabled"))
        assertNull(SafetyPolicy.blockedReason("settings_show_widgets_default"))
        assertNull(SafetyPolicy.blockedReason("WirelessLatencyMonitor__warning_threshold_ms"))
    }

    @Test fun allTwelveReviewedParkedAudioVideoKnobsAreElevatedNotBlocked() {
        val allowed = listOf(
            "SynchronizedMediaFeature__audio_buffer_limit_parked_exp_ms",
            "SynchronizedMediaFeature__audio_buffer_limit_parked_exp_ms_tts",
            "SynchronizedMediaFeature__average_lag_weight_parked_exp",
            "SynchronizedMediaFeature__enable_sync_parked_exp",
            "SynchronizedMediaFeature__max_audio_delay_allowed_parked_exp_ms",
            "SynchronizedMediaFeature__max_lag_tolerance_parked_exp_ms",
            "SynchronizedMediaFeature__max_video_delay_allowed_parked_exp_ms",
            "SynchronizedMediaFeature__sync_interval_parked_exp_ms",
            "SynchronizedMediaFeature__sync_offset_override_parked_exp_ms",
            "VideoTimingControllerFeature__enable_video_timing_controller_parked_exp",
            "VideoTimingControllerFeature__video_timing_controller_max_delay_parked_exp_ms",
            "VideoTimingControllerFeature__video_timing_controller_min_delay_parked_exp_ms"
        )
        assertEquals(12, allowed.size)
        allowed.forEach { key ->
            assertNull("Expected elevated not blocked: $key", SafetyPolicy.blockedReason(key))
            assertNotNull("Missing elevated review: $key", SafetyPolicy.elevatedRiskReason(key))
        }
    }

    @Test fun parkedSpeedThresholdsAndUnknownVariantsRemainBlocked() {
        val prohibited = listOf(
            "CarSensorParameters__max_parked_speed_gps_sensor",
            "CarSensorParameters__max_parked_speed_wheel_sensor",
            "VehicleState__force_parked",
            "VideoTimingControllerFeature__unreviewed_parked_exp_ms",
            "SynchronizedMediaFeature__new_parked_exp_parameter",
            "SynchronizedMediaFeature__video_while_driving_parked_exp",
            "CarSensorParameters__driver_distraction_override_parked",
            "Feature__speed_lockout_parked",
            "SomeFeature__movement_override"
        )
        prohibited.forEach { key ->
            assertNotNull("Unexpectedly writable: $key", SafetyPolicy.blockedReason(key))
            assertNull("Not an elevated timing control: $key", SafetyPolicy.elevatedRiskReason(key))
        }
    }

    @Test fun exactMatchCannotBeSpoofedWithCaseOrSuffixes() {
        val key = "VideoTimingControllerFeature__video_timing_controller_min_delay_parked_exp_ms"
        assertNotNull(SafetyPolicy.blockedReason(key + "__bypass"))
        assertNotNull(SafetyPolicy.blockedReason(key.uppercase()))
        assertNull(SafetyPolicy.elevatedRiskReason(key.uppercase()))
    }
}
