package io.github.aaexperiments.core

import io.github.aaexperiments.discovery.DiscoveredType
import java.security.MessageDigest

data class TypedOverride(val type: DiscoveredType, val encodedValue: String)

object DynamicOverrideCodec {
    const val PREFIX = "override.v2."
    fun preferenceKey(identifier: String): String = PREFIX + sha256(identifier).take(40)

    fun validate(type: DiscoveredType, input: String): TypedOverride = when (type) {
        DiscoveredType.BOOLEAN -> require(input == "true" || input == "false") { "Expected true or false" }.let { TypedOverride(type, input) }
        DiscoveredType.INT -> TypedOverride(type, input.toInt().toString())
        DiscoveredType.LONG -> TypedOverride(type, input.toLong().toString())
        DiscoveredType.FLOAT -> TypedOverride(type, input.toFloat().also { require(it.isFinite()) }.toString())
        DiscoveredType.DOUBLE -> TypedOverride(type, input.toDouble().also { require(it.isFinite()) }.toString())
        DiscoveredType.STRING -> TypedOverride(type, input)
        else -> error("Type $type is not editable")
    }

    fun serialize(value: TypedOverride): String = "${value.type.name}:${value.encodedValue}"
    fun deserialize(raw: String?): TypedOverride? {
        if (raw == null) return null
        val split = raw.indexOf(':'); if (split <= 0) return null
        val type = runCatching { DiscoveredType.valueOf(raw.substring(0, split)) }.getOrNull() ?: return null
        return runCatching { validate(type, raw.substring(split + 1)) }.getOrNull()
    }

    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
