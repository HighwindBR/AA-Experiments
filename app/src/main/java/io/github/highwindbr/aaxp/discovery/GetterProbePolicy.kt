package io.github.highwindbr.aaxp.discovery

/**
 * A deliberately small read-only diagnostic cohort, not the advanced override eligibility policy.
 * These probes call through to the original getter and NEVER set a replacement value.
 */
object GetterProbePolicy {
    /** Preflight12-A: reviewed, read-only DOUBLE activity probes for AA 18.0. */
    const val DOUBLE_BASE_SHA256 =
        "cebd7c3c50885fb7dbb8fce202b5f33f0412c8f3801fac91a509da9482b9c60d"

    /** DEX binary class, no-arg method. Never treat a matching prefix as eligible. */
    val doubleCandidates = linkedMapOf(
        "SystemUi__widescreen_aspect_ratio_breakpoint" to ("Ladjy;" to "a"),
        "ProjectionWindowManager__expected_frame_time_deviation" to ("Ladiu;" to "b"),
        "VideoEncoderParams__bitrate_adjustment_exponent" to ("Ladll;" to "c")
    )
    fun isInspectedDoubleBuild(baseSha256: String?): Boolean =
        DOUBLE_BASE_SHA256.equals(baseSha256, ignoreCase = true)

    val supportedKeys = setOf(
        "PhoneThemeFeature__enable_dynamic_app_icon_shape_by_manufacturer_kill_switch",
        "CarAppLibrary__recreate_map_view_context_on_config_change_kill_switch",
        "WirelessLatencyMonitor__warning_threshold_ms"
    ) + doubleCandidates.keys

    fun issue(identifier: DiscoveredIdentifier): String? {
        val double = identifier.key in doubleCandidates
        val expectedType = when {
            double -> DiscoveredType.DOUBLE
            identifier.key == "WirelessLatencyMonitor__warning_threshold_ms" -> DiscoveredType.LONG
            else -> DiscoveredType.BOOLEAN
        }
        if (identifier.key !in supportedKeys) return "Not in the limited read-only probe cohort."
        if (identifier.inferredType != expectedType) return "Probe target type does not match the verified cohort."
        if (identifier.kind != IdentifierKind.FENOTYPE_FLAG) return "Not a proven Fenotype identifier."
        if (identifier.resolution !in setOf(MappingResolution.DIRECT, MappingResolution.STRUCTURAL))
            return "A unique getter is not resolved."
        val method = identifier.getterCandidates.singleOrNull() ?: return "Expected exactly one getter."
        val descriptor = when (identifier.inferredType) {
            DiscoveredType.BOOLEAN -> "()Z"
            DiscoveredType.LONG -> "()J"
            DiscoveredType.DOUBLE -> "()D"
            else -> return "Unsupported probe return type."
        }
        if (method.descriptor != descriptor || method.parameterTypes.isNotEmpty() || method.isStaticInitializer)
            return "Getter ABI does not match the inferred primitive type."
        if (double) {
            val pinned = checkNotNull(doubleCandidates[identifier.key])
            if (!identifier.editable || method.className != pinned.first ||
                method.methodName != pinned.second ||
                method.instructionCount <= 0 || method.opcodeSha256.isBlank())
                return "DOUBLE probe requires the exact reviewed editable getter."
        }
        SafetyPolicy.blockedReason(identifier.key)?.let { return it }
        return null
    }
}
