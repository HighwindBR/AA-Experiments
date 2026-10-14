package io.github.highwindbr.aaxp.discovery

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
        // SSL/TLS are short acronyms: substring searches accidentally match unrelated
        // compound identifiers such as WireleSSLatencyMonitor.
        val words = key.split(Regex("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|[^A-Za-z0-9]+"))
            .filter { it.isNotBlank() }.map { it.lowercase() }
        val match = blockedTokens.firstOrNull {
            if (it == "ssl" || it == "tls") it in words else it in normalized
        } ?: return null
        return "Read-only safety policy: identifier matched sensitive token '$match'. It remains catalogued for research."
    }
}
