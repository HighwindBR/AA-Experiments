package io.github.aaexperiments.discovery

/** Keeps sensitive controls visible in the inventory without turning a generic
 * reverse-engineering match into an immediately executable hook. */
object SafetyPolicy {
    private val blockedTokens = listOf(
        "auth", "cert", "certificate", "credential", "crypt", "integrity", "keystore",
        "oauth", "signature", "ssl", "tls", "token", "trust", "validation",
        "app_validation", "package_validation", "unknown_sources",
        "driver_distraction", "driving_restriction", "movement", "moving",
        "parked", "passenger", "speed_lockout", "video_while_driving"
    )

    fun blockedReason(key: String): String? {
        val normalized = key.lowercase()
        val match = blockedTokens.firstOrNull { it in normalized } ?: return null
        return "Read-only safety policy: identifier matched sensitive token '$match'. It remains catalogued for research."
    }
}
