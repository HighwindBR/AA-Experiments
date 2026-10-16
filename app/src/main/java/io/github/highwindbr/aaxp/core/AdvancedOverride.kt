package io.github.highwindbr.aaxp.core

import io.github.highwindbr.aaxp.discovery.*
import org.json.JSONObject

/**
 * One independent, typed experiment per resolved getter. Baseline null means the
 * user had NO normal override, and must not be replaced by the compiled default.
 */
data class AdvancedOverrideRecord(
    val key: String,
    val type: DiscoveredType,
    val value: String,
    val buildFingerprint: String,
    val targetClass: String,
    val targetMethod: String,
    val descriptor: String,
    val opcodeFingerprint: String,
    val instructionCount: Int,
    val previousOverride: String?,
    val previousName: String?,
    val active: Boolean,
    val createdAtMillis: Long
) {
    fun encode(): String = JSONObject().apply {
        put("schema", 1); put("key", key); put("type", type.name); put("value", value)
        put("build", buildFingerprint); put("targetClass", targetClass)
        put("targetMethod", targetMethod); put("descriptor", descriptor)
        put("opcodeFingerprint", opcodeFingerprint); put("instructionCount", instructionCount)
        put("previousOverride", previousOverride ?: JSONObject.NULL)
        put("previousName", previousName ?: JSONObject.NULL)
        put("active", active); put("createdAtMillis", createdAtMillis)
    }.toString()

    companion object {
        fun decode(raw: String?): AdvancedOverrideRecord? = runCatching {
            if (raw == null) return null
            val json = JSONObject(raw)
            require(json.getInt("schema") == 1)
            val key = json.getString("key")
            val type = DiscoveredType.valueOf(json.getString("type"))
            val value = DynamicOverrideCodec.validate(type, json.getString("value")).encodedValue
            val previous = json.optString("previousOverride").takeUnless { json.isNull("previousOverride") }
            val name = json.optString("previousName").takeUnless { json.isNull("previousName") }
            require(previous == null || DynamicOverrideCodec.deserialize(previous)?.type == type)
            require(key.isNotBlank())
            AdvancedOverrideRecord(
                key, type, value, json.getString("build"), json.getString("targetClass"),
                json.getString("targetMethod"), json.getString("descriptor"),
                json.getString("opcodeFingerprint"), json.getInt("instructionCount"),
                previous, name, json.getBoolean("active"), json.getLong("createdAtMillis")
            )
        }.getOrNull()
    }
}

/**
 * Eligibility is structural and intentionally narrower than the full catalog.
 * This is NOT an escape hatch for safety-policy denials, enums as standalone
 * literals, malformed protobufs, ambiguous getters, or type reassignment.
 */
object AdvancedOverridePolicy {
    const val PREFIX = "advanced.v1."
    val supported = setOf(DiscoveredType.BOOLEAN, DiscoveredType.LONG,
        DiscoveredType.DOUBLE, DiscoveredType.STRING)

    fun preferenceKey(key: String): String =
        PREFIX + DynamicOverrideCodec.preferenceKey(key).removePrefix(DynamicOverrideCodec.PREFIX)

    fun descriptor(type: DiscoveredType): String? = when (type) {
        DiscoveredType.BOOLEAN -> "()Z"
        DiscoveredType.LONG -> "()J"
        DiscoveredType.DOUBLE -> "()D"
        DiscoveredType.STRING -> "()Ljava/lang/String;"
        else -> null
    }

    fun issue(id: DiscoveredIdentifier): String? {
        if (id.kind != IdentifierKind.FENOTYPE_FLAG) return "Not a configurable Fenotype flag."
        SafetyPolicy.blockedReason(id.key)?.let { return it }
        if (id.inferredType !in supported) return "Unsupported or unknown typed return; protobuf and other objects stay read-only."
        if (id.resolution !in setOf(MappingResolution.DIRECT, MappingResolution.STRUCTURAL))
            return "Unresolved, catalog-only, or ambiguous getter."
        val getter = id.getterCandidates.singleOrNull() ?: return "Expected exactly one getter."
        if (getter.descriptor != descriptor(id.inferredType) ||
            getter.parameterTypes.isNotEmpty() || getter.isStaticInitializer)
            return "Getter signature does not match the detected type."
        if (getter.opcodeSha256.isBlank() || getter.instructionCount <= 0)
            return "Getter structural fingerprint unavailable."
        // Approved flags belong to the normal editor, not two overlapping hook paths.
        if (id.editable) return "Already supported by the normal Experiments editor."
        return null
    }

    fun matches(id: DiscoveredIdentifier, record: AdvancedOverrideRecord, build: String): Boolean {
        if (record.key != id.key || record.buildFingerprint != build ||
            record.type != id.inferredType || issue(id) != null) return false
        val getter = id.getterCandidates.single()
        return record.targetClass == getter.className &&
            record.targetMethod == getter.methodName &&
            record.descriptor == getter.descriptor &&
            record.opcodeFingerprint == getter.opcodeSha256 &&
            record.instructionCount == getter.instructionCount
    }

    fun newRecord(id: DiscoveredIdentifier, build: String, raw: String,
        previousOverride: String?, previousName: String?, now: Long): AdvancedOverrideRecord {
        require(issue(id) == null) { issue(id) ?: "Not eligible" }
        val value = DynamicOverrideCodec.validate(id.inferredType, raw).encodedValue
        require(previousOverride == null ||
            DynamicOverrideCodec.deserialize(previousOverride)?.type == id.inferredType) {
            "Existing normal override is malformed or of another type."
        }
        val getter = id.getterCandidates.single()
        return AdvancedOverrideRecord(id.key, id.inferredType, value, build,
            getter.className, getter.methodName, getter.descriptor, getter.opcodeSha256,
            getter.instructionCount, previousOverride, previousName, true, now)
    }
}
