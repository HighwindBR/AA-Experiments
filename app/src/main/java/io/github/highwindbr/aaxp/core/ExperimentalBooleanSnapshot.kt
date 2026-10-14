package io.github.highwindbr.aaxp.core

import io.github.highwindbr.aaxp.discovery.*
import org.json.JSONObject

/**
 * Single-target beta4 preflight. Stored atomically in LSPosed remote preferences, not
 * in the regular override namespace. Null baseline means ABSENT, never "compiled default".
 */
data class ExperimentalBooleanSnapshot(
    val buildFingerprint: String,
    val targetClass: String,
    val targetMethod: String,
    val descriptor: String,
    val opcodeFingerprint: String,
    val instructionCount: Int,
    val value: Boolean,
    val previousOverride: String?,
    val previousName: String?,
    val active: Boolean,
    val createdAtMillis: Long
) {
    fun encode(): String = JSONObject().apply {
        put("schema", 1)
        put("key", ExperimentalBooleanPolicy.KEY)
        put("build", buildFingerprint)
        put("targetClass", targetClass)
        put("targetMethod", targetMethod)
        put("descriptor", descriptor)
        put("opcodeFingerprint", opcodeFingerprint)
        put("instructionCount", instructionCount)
        put("value", value)
        put("previousOverride", previousOverride ?: JSONObject.NULL)
        put("previousName", previousName ?: JSONObject.NULL)
        put("active", active)
        put("createdAtMillis", createdAtMillis)
    }.toString()

    companion object {
        fun decode(raw: String?): ExperimentalBooleanSnapshot? = runCatching {
            val j = JSONObject(raw ?: return null)
            require(j.getInt("schema") == 1 && j.getString("key") == ExperimentalBooleanPolicy.KEY)
            require(j.getString("descriptor") == "()Z")
            val previous = if (j.isNull("previousOverride")) null else j.getString("previousOverride")
            val name = if (j.isNull("previousName")) null else j.getString("previousName")
            require(previous == null || DynamicOverrideCodec.deserialize(previous)?.type == DiscoveredType.BOOLEAN)
            ExperimentalBooleanSnapshot(
                j.getString("build"), j.getString("targetClass"), j.getString("targetMethod"),
                j.getString("descriptor"), j.getString("opcodeFingerprint"),
                j.getInt("instructionCount"), j.getBoolean("value"), previous, name,
                j.getBoolean("active"), j.getLong("createdAtMillis")
            )
        }.getOrNull()
    }
}

object ExperimentalBooleanPolicy {
    const val KEY = "PhoneThemeFeature__enable_dynamic_app_icon_shape_by_manufacturer_kill_switch"
    const val RECORD_KEY = "experimental.preflight1.snapshot"

    fun issue(identifier: DiscoveredIdentifier): String? {
        if (identifier.key != KEY) return "Only the validated PhoneTheme getter is allowed in this preflight."
        GetterProbePolicy.issue(identifier)?.let { return it }
        if (identifier.inferredType != DiscoveredType.BOOLEAN ||
            identifier.compiledDefault !in listOf("true", "false"))
            return "A known BOOLEAN compiled default is required."
        val method = identifier.getterCandidates.single()
        if (method.opcodeSha256.isBlank() || method.instructionCount <= 0)
            return "The getter lacks a verifiable structural fingerprint."
        return null
    }

    fun matches(identifier: DiscoveredIdentifier, snapshot: ExperimentalBooleanSnapshot, build: String): Boolean {
        if (snapshot.buildFingerprint != build || issue(identifier) != null) return false
        val method = identifier.getterCandidates.single()
        return snapshot.targetClass == method.className &&
            snapshot.targetMethod == method.methodName &&
            snapshot.descriptor == method.descriptor &&
            snapshot.opcodeFingerprint == method.opcodeSha256 &&
            snapshot.instructionCount == method.instructionCount
    }

    fun snapshot(
        identifier: DiscoveredIdentifier, build: String, value: Boolean,
        baselineOverride: String?, baselineName: String?, timeMillis: Long
    ): ExperimentalBooleanSnapshot {
        require(issue(identifier) == null) { issue(identifier) ?: "Not eligible" }
        val method = identifier.getterCandidates.single()
        return ExperimentalBooleanSnapshot(
            build, method.className, method.methodName, method.descriptor,
            method.opcodeSha256, method.instructionCount, value,
            baselineOverride, baselineName, true, timeMillis
        )
    }
}
