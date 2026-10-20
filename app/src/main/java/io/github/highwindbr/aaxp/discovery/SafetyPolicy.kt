package io.github.highwindbr.aaxp.discovery

/**
 * Catalog visibility is independent of write eligibility. Known safety controls stay
 * read-only; being a parked-mode parameter does not itself make a setting a
 * parking-state detector or a driving-restriction bypass.
 */
object SafetyPolicy {
    /**
     * Audited Android Auto 18.0 cohort. These knobs tune audio/video synchronization
     * *within* the parked experiment; they are not the input used to decide whether
     * the car is parked. Matching the family prefix alone is deliberately insufficient.
     * New flags remain blocked until individually reviewed.
     */
    private val parkedMediaTimingKeys = setOf(
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

    private val blockedTokens = listOf(
        "auth", "cert", "certificate", "credential", "crypt", "integrity", "keystore",
        "oauth", "signature", "ssl", "tls", "token", "trust", "validation",
        "app_validation", "package_validation", "unknown_sources",
        "driver_distraction", "driving_restriction", "movement", "moving",
        "passenger", "speed_lockout", "video_while_driving"
    )

    /** Elevated warning and acknowledgement, not a global eligibility bypass. */
    fun elevatedRiskReason(key: String): String? =
        if (key in parkedMediaTimingKeys)
            "Reviewed parked-mode media/video timing parameter: runtime safety and valid ranges remain unproven. " +
                "May destabilize projection or A/V synchronization; never test while driving."
        else null

    fun blockedReason(key: String): String? {
        val normalized = key.lowercase()
        // SSL/TLS are short acronyms: substring searches accidentally match unrelated
        // compound identifiers such as WireleSSLatencyMonitor.
        val words = key.split(Regex("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|[^A-Za-z0-9]+"))
            .filter { it.isNotBlank() }.map { it.lowercase() }

        val match = blockedTokens.firstOrNull {
            if (it == "ssl" || it == "tls") it in words else it in normalized
        }
        if (match != null) return "Read-only safety policy: identifier matched sensitive token '$match'. It remains catalogued for research."

        // Default-deny parked-state and parked-mode symbols. Only the 12 exact
        // reviewed audio/video timing keys above receive an elevated-risk exception.
        if ("parked" in normalized && key !in parkedMediaTimingKeys) {
            return "Read-only safety policy: unreviewed parked-state/parked-mode identifier. " +
                "Speed thresholds, parking detection and driving restrictions cannot be overridden."
        }
        return null
    }
}
