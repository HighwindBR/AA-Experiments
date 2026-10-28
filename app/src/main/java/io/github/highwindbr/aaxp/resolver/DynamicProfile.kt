package io.github.highwindbr.aaxp.resolver

import io.github.highwindbr.aaxp.core.DynamicOverrideCodec
import io.github.highwindbr.aaxp.discovery.DexInventory
import io.github.highwindbr.aaxp.discovery.ResolutionConfidence
import org.json.JSONArray
import org.json.JSONObject

object DynamicProfile {
    const val FILE_NAME = "active_profile_v2.json"

    data class Mapping(
        val key: String,
        val preferenceKey: String,
        val className: String,
        val methodName: String,
        val descriptor: String,
        val type: String
    )

    data class Probe(
        val key: String,
        val className: String,
        val methodName: String,
        val descriptor: String
    )

    data class ExperimentalMapping(
        val key: String,
        val className: String,
        val methodName: String,
        val descriptor: String
    )

    data class AdvancedMapping(
        val key: String,
        val className: String,
        val methodName: String,
        val descriptor: String,
        val type: String,
        val preferenceKey: String
    )

    fun create(buildFingerprintSha256: String, baseSha256: String, packageVersionCode: Long, packageArtifactSha256: Map<String, String>, dexSha256: Map<String, String>, values: List<Mapping>, probes: List<Probe> = emptyList(), experimental: List<ExperimentalMapping> = emptyList(), advanced: List<AdvancedMapping> = emptyList(), videoTimingTrace: Boolean = false, mediaStartupTrace: Boolean = false): String {
        val mappings = JSONArray()
        values.forEach { value -> mappings.put(JSONObject().apply {
            put("key", value.key); put("preferenceKey", value.preferenceKey); put("className", value.className)
            put("methodName", value.methodName); put("descriptor", value.descriptor); put("type", value.type)
        }) }
        val probeRecords = JSONArray()
        probes.forEach { probe -> probeRecords.put(JSONObject().apply {
            put("key", probe.key); put("className", probe.className)
            put("methodName", probe.methodName); put("descriptor", probe.descriptor)
        }) }
        val experimentalRecords = JSONArray()
        experimental.forEach { item -> experimentalRecords.put(JSONObject().apply {
            put("key", item.key); put("className", item.className)
            put("methodName", item.methodName); put("descriptor", item.descriptor)
        }) }
        val advancedRecords = JSONArray()
        advanced.forEach { item -> advancedRecords.put(JSONObject().apply {
            put("key", item.key); put("className", item.className)
            put("methodName", item.methodName); put("descriptor", item.descriptor)
            put("type", item.type); put("preferenceKey", item.preferenceKey)
        }) }
        return JSONObject().apply {
            put("schemaVersion", 3); put("buildFingerprintSha256", buildFingerprintSha256); put("baseSha256", baseSha256)
            put("packageVersionCode", packageVersionCode); put("packageArtifactSha256", JSONObject(packageArtifactSha256))
            put("dexSha256", JSONObject(dexSha256)); put("mappings", mappings)
            put("probes", probeRecords); put("experimental", experimentalRecords)
            put("advanced", advancedRecords)
            put("videoTimingTrace", videoTimingTrace)
            put("mediaStartupTrace", mediaStartupTrace)
        }.toString()
    }

    fun create(inventory: DexInventory, activePreferenceKeys: Set<String>): String {
        val mappings = JSONArray()
        inventory.identifiers.filter {
            it.confidence in setOf(ResolutionConfidence.UNIQUE_GETTER, ResolutionConfidence.STRUCTURALLY_VALIDATED) &&
                it.editable && it.getterCandidates.size == 1 && DynamicOverrideCodec.preferenceKey(it.key) in activePreferenceKeys
        }.forEach { identifier ->
            val method = identifier.getterCandidates.single()
            mappings.put(JSONObject().apply {
                put("key", identifier.key)
                put("preferenceKey", DynamicOverrideCodec.preferenceKey(identifier.key))
                put("className", method.className.removePrefix("L").removeSuffix(";").replace('/', '.'))
                put("methodName", method.methodName)
                put("descriptor", method.descriptor)
                put("type", identifier.inferredType.name)
            })
        }
        return JSONObject().apply {
            put("schemaVersion", 3); put("buildFingerprintSha256", inventory.buildFingerprintSha256); put("baseSha256", inventory.baseSha256)
            put("packageVersionCode", inventory.packageVersionCode); put("packageArtifactSha256", JSONObject(inventory.packageArtifactSha256))
            put("dexSha256", JSONObject(inventory.dexSha256)); put("mappings", mappings)
        }.toString()
    }
}
