package io.github.highwindbr.aaxp.discovery

/**
 * A deliberately small read-only diagnostic cohort, not the advanced override eligibility policy.
 * These probes call through to the original getter and NEVER set a replacement value.
 */
object GetterProbePolicy {
    val supportedKeys = setOf(
        "PhoneThemeFeature__enable_dynamic_app_icon_shape_by_manufacturer_kill_switch",
        "CarAppLibrary__recreate_map_view_context_on_config_change_kill_switch",
        "WirelessLatencyMonitor__warning_threshold_ms"
    )

    fun issue(identifier: DiscoveredIdentifier): String? {
        val expectedType = if (identifier.key == "WirelessLatencyMonitor__warning_threshold_ms")
            DiscoveredType.LONG else DiscoveredType.BOOLEAN
        if (identifier.key !in supportedKeys) return "Not in the limited read-only probe cohort."
        if (identifier.inferredType != expectedType) return "Probe target type does not match the verified cohort."
        if (identifier.kind != IdentifierKind.FENOTYPE_FLAG) return "Not a proven Fenotype identifier."
        if (identifier.resolution !in setOf(MappingResolution.DIRECT, MappingResolution.STRUCTURAL))
            return "A unique getter is not resolved."
        val method = identifier.getterCandidates.singleOrNull() ?: return "Expected exactly one getter."
        val descriptor = when (identifier.inferredType) {
            DiscoveredType.BOOLEAN -> "()Z"
            DiscoveredType.LONG -> "()J"
            else -> return "Unsupported probe return type."
        }
        if (method.descriptor != descriptor || method.parameterTypes.isNotEmpty() || method.isStaticInitializer)
            return "Getter ABI does not match the inferred primitive type."
        SafetyPolicy.blockedReason(identifier.key)?.let { return it }
        return null
    }
}
