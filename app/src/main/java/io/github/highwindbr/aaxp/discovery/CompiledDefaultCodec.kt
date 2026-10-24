package io.github.highwindbr.aaxp.discovery

/**
 * Fenotype registration arguments can encode FLOAT/DOUBLE defaults as raw IEEE-754
 * integer bits. Direct getter-return constants were already converted during DEX
 * analysis. Decode only with registration provenance; do not guess by magnitude.
 *
 * Idempotent for normalized decimal values, allowing old SQLite/JSON catalogs
 * to be corrected on read without touching the original DEX occurrence evidence.
 */
object CompiledDefaultCodec {
    private val rawBitSources = setOf("INVOKE_ARGUMENT", "REGISTRY_FLOW")

    fun normalize(raw: String?, type: DiscoveredType, source: String?): String? {
        if (raw == null || source !in rawBitSources) return raw
        return when (type) {
            DiscoveredType.DOUBLE -> raw.toLongOrNull()?.let { bits ->
                Double.fromBits(bits).takeIf(Double::isFinite)?.toString()
            } ?: raw
            DiscoveredType.FLOAT -> raw.toIntOrNull()?.let { bits ->
                Float.fromBits(bits).takeIf(Float::isFinite)?.toString()
            } ?: raw
            else -> raw
        }
    }
}
