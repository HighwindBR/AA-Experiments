package io.github.aaexperiments.discovery

import org.json.JSONArray
import org.json.JSONObject

/** Compact normalized catalog encoding: method fingerprints are stored once and referenced by id. */
object InventoryJson {
    fun encode(value: DexInventory): String {
        val methods = linkedMapOf<String, MethodFingerprint>()
        value.identifiers.forEach { identifier ->
            (identifier.occurrences.map { it.method } + identifier.getterCandidates + identifier.consumers.map { it.method }).forEach { methods.putIfAbsent(methodKey(it), it) }
        }
        val ids = methods.keys.withIndex().associate { it.value to it.index }
        return JSONObject().apply {
            put("schemaVersion", 4); put("encoding", "normalized-method-table-v2"); put("baseSha256", value.baseSha256)
            put("buildFingerprintSha256", value.buildFingerprintSha256); put("packageVersionCode", value.packageVersionCode)
            put("packageArtifactSha256", JSONObject(value.packageArtifactSha256))
            put("generatedAtEpochMs", value.generatedAtEpochMs); put("resolverSchemaVersion", value.resolverSchemaVersion)
            put("dexSha256", JSONObject(value.dexSha256)); put("errors", JSONArray(value.errors))
            put("methods", JSONArray().apply { methods.values.forEach { put(method(it)) } })
            put("identifiers", JSONArray().apply { value.identifiers.forEach { put(identifier(it, ids)) } })
        }.toString()
    }

    fun decode(raw: String): DexInventory {
        val root = JSONObject(raw)
        require(root.getInt("schemaVersion") in 2..4)
        require(root.optString("encoding") in setOf("normalized-method-table-v1", "normalized-method-table-v2"))
        val methods = root.getJSONArray("methods").objects().map(::method)
        val dex = root.getJSONObject("dexSha256"); val dexHashes = dex.keys().asSequence().associateWith { dex.getString(it) }
        return DexInventory(baseSha256 = root.getString("baseSha256"), dexSha256 = dexHashes,
            generatedAtEpochMs = root.getLong("generatedAtEpochMs"), identifiers = root.getJSONArray("identifiers").objects().map { identifier(it, methods) },
            errors = root.getJSONArray("errors").strings(), resolverSchemaVersion = root.optInt("resolverSchemaVersion", 0),
            buildFingerprintSha256 = root.optString("buildFingerprintSha256", root.getString("baseSha256")),
            packageVersionCode = root.optLong("packageVersionCode", 0),
            packageArtifactSha256 = root.optJSONObject("packageArtifactSha256")?.let { o -> o.keys().asSequence().associateWith { o.getString(it) } }.orEmpty())
    }

    private fun identifier(value: DiscoveredIdentifier, ids: Map<String, Int>) = JSONObject().apply {
        put("key", value.key); put("namespace", value.namespace.name); put("type", value.inferredType.name)
        put("compiledDefault", value.compiledDefault ?: JSONObject.NULL); put("confidence", value.confidence.name)
        put("reason", value.reason ?: JSONObject.NULL); put("editable", value.editable)
        put("kind", value.kind.name); put("resolution", value.resolution.name)
        put("storageType", value.storageType.name); put("semanticType", value.semanticType.name)
        put("runtimeConsumerStatus", value.runtimeConsumerStatus.name); put("semanticsReviewed", value.semanticsReviewed)
        put("semanticsProvenance", value.semanticsProvenance.name)
        put("evidenceConfidence", confidence(value.evidenceConfidence))
        put("editabilityReason", value.editabilityReason?.name ?: JSONObject.NULL)
        put("metadata", JSONObject(value.metadata))
        put("occurrences", JSONArray().apply { value.occurrences.forEach { occurrence -> put(JSONObject().apply {
            put("method", ids.getValue(methodKey(occurrence.method))); put("ordinal", occurrence.literalOrdinal)
            put("default", occurrence.candidateDefault ?: JSONObject.NULL); put("fields", JSONArray(occurrence.associatedFields))
            put("instructionOffset", occurrence.instructionOffset); put("role", occurrence.role.name); put("kind", occurrence.kind.name)
            put("storageType", occurrence.storageType.name); put("semanticType", occurrence.semanticType.name)
            put("classificationConfidence", occurrence.classificationConfidence.name)
            put("registryId", occurrence.registryId ?: JSONObject.NULL)
            put("defaultSource", occurrence.defaultSource ?: JSONObject.NULL)
        }) } })
        put("getters", JSONArray(value.getterCandidates.map { ids.getValue(methodKey(it)) }))
        put("consumers", JSONArray().apply { value.consumers.forEach { evidence -> put(JSONObject().apply {
            put("method", ids.getValue(methodKey(evidence.method))); put("depth", evidence.depth); put("linkKind", evidence.linkKind.name)
            put("path", JSONArray(evidence.path)); put("semanticSummary", evidence.semanticSummary ?: JSONObject.NULL)
        }) } })
    }

    private fun identifier(value: JSONObject, methods: List<MethodFingerprint>): DiscoveredIdentifier {
        val legacy = DiscoveredIdentifier(
        key = value.getString("key"), namespace = IdentifierNamespace.valueOf(value.getString("namespace")),
        inferredType = DiscoveredType.valueOf(value.getString("type")), compiledDefault = value.nullableString("compiledDefault"),
        occurrences = value.getJSONArray("occurrences").objects().map { occurrence -> IdentifierOccurrence(
            methods[occurrence.getInt("method")], occurrence.getInt("ordinal"), occurrence.nullableString("default"), occurrence.getJSONArray("fields").strings(),
            occurrence.optInt("instructionOffset", -1), occurrence.optString("role").enumOr(LiteralRole.UNKNOWN),
            occurrence.optString("kind").identifierKindOr(IdentifierKind.UNKNOWN),
            occurrence.optString("storageType").enumOr(StorageValueType.UNKNOWN),
            occurrence.optString("semanticType").enumOr(SemanticValueType.UNKNOWN),
            occurrence.optString("classificationConfidence").enumOr(EvidenceStrength.NONE),
            occurrence.nullableString("registryId"), occurrence.nullableString("defaultSource")) },
        getterCandidates = value.getJSONArray("getters").ints().map(methods::get),
        confidence = ResolutionConfidence.valueOf(value.getString("confidence")), reason = value.nullableString("reason"), editable = value.getBoolean("editable"),
        metadata = value.optJSONObject("metadata")?.let { metadata -> metadata.keys().asSequence().associateWith { metadata.getString(it) } }.orEmpty(),
        kind = value.optString("kind").takeIf { it.isNotBlank() }?.identifierKindOr(IdentifierKind.UNKNOWN)
            ?: SemanticClassifier.kindOf(value.getString("key"), IdentifierNamespace.valueOf(value.getString("namespace"))),
        resolution = value.optString("resolution").takeIf { it.isNotBlank() }?.let(MappingResolution::valueOf)
            ?: SemanticClassifier.resolutionOf(ResolutionConfidence.valueOf(value.getString("confidence"))),
        runtimeConsumerStatus = value.optString("runtimeConsumerStatus").takeIf { it.isNotBlank() }?.let(RuntimeConsumerStatus::valueOf) ?: RuntimeConsumerStatus.UNKNOWN,
        semanticsReviewed = value.optBoolean("semanticsReviewed", false),
        editabilityReason = value.optString("editabilityReason").takeIf { it.isNotBlank() && it != "null" }?.let(EditabilityReason::valueOf),
        consumers = value.optJSONArray("consumers")?.objects()?.map { evidence -> ConsumerEvidence(
            methods[evidence.getInt("method")], evidence.getInt("depth"), ConsumerLinkKind.valueOf(evidence.getString("linkKind")),
            evidence.getJSONArray("path").strings(), evidence.nullableString("semanticSummary")) }.orEmpty(),
        storageType = value.optString("storageType").enumOr(SemanticClassifier.storageTypeOf(DiscoveredType.valueOf(value.getString("type")))),
        semanticType = value.optString("semanticType").enumOr(SemanticClassifier.semanticTypeOf(value.getString("key"), DiscoveredType.valueOf(value.getString("type")))),
        evidenceConfidence = value.optJSONObject("evidenceConfidence")?.let(::confidence) ?: SemanticClassifier.confidenceOf(
            ResolutionConfidence.valueOf(value.getString("confidence")), value.nullableString("compiledDefault"), emptyList()),
        semanticsProvenance = value.optString("semanticsProvenance").enumOr(
            if (value.optBoolean("semanticsReviewed", false)) SemanticsProvenance.MANUAL_EXACT_BUILD else SemanticsProvenance.NONE))
        return SemanticClassifier.normalize(legacy)
    }

    private fun method(value: MethodFingerprint) = JSONObject().apply {
        put("dex", value.dex); put("class", value.className); put("name", value.methodName); put("descriptor", value.descriptor)
        put("returnType", value.returnType.name); put("parameters", JSONArray(value.parameterTypes)); put("instructions", value.instructionCount)
        put("opcodeSha256", value.opcodeSha256); put("clinit", value.isStaticInitializer)
        put("fields", JSONArray(value.referencedFields)); put("calls", JSONArray(value.invokedMethods))
    }
    private fun method(value: JSONObject) = MethodFingerprint(value.getString("dex"), value.getString("class"), value.getString("name"),
        value.getString("descriptor"), DiscoveredType.valueOf(value.getString("returnType")), value.getJSONArray("parameters").strings(),
        value.getInt("instructions"), value.getString("opcodeSha256"), value.getBoolean("clinit"), value.getJSONArray("fields").strings(), value.getJSONArray("calls").strings())

    private fun methodKey(value: MethodFingerprint) = "${value.dex}|${value.className}|${value.methodName}|${value.descriptor}"
    private fun confidence(value: EvidenceConfidence) = JSONObject().apply {
        put("identity", value.identity.name); put("storageType", value.storageType.name); put("semanticType", value.semanticType.name)
        put("defaultValue", value.defaultValue.name); put("getter", value.getter.name); put("runtimeConsumer", value.runtimeConsumer.name)
        put("semantics", value.semantics.name); put("evidence", JSONArray(value.evidence)); put("ambiguities", JSONArray(value.ambiguities))
    }
    private fun confidence(value: JSONObject) = EvidenceConfidence(
        value.optString("identity").enumOr(EvidenceStrength.NONE), value.optString("storageType").enumOr(EvidenceStrength.NONE),
        value.optString("semanticType").enumOr(EvidenceStrength.NONE), value.optString("defaultValue").enumOr(EvidenceStrength.NONE),
        value.optString("getter").enumOr(EvidenceStrength.NONE), value.optString("runtimeConsumer").enumOr(EvidenceStrength.NONE),
        value.optString("semantics").enumOr(EvidenceStrength.NONE), value.optJSONArray("evidence")?.strings().orEmpty(),
        value.optJSONArray("ambiguities")?.strings().orEmpty())
    private inline fun <reified T : Enum<T>> String.enumOr(fallback: T): T = runCatching { enumValueOf<T>(this) }.getOrDefault(fallback)
    private fun String.identifierKindOr(fallback: IdentifierKind): IdentifierKind = when (this) {
        "ENUM_LITERAL" -> IdentifierKind.JAVA_ENUM_LITERAL
        else -> enumOr(fallback)
    }
    private fun JSONObject.nullableString(name: String) = if (isNull(name)) null else getString(name)
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
    private fun JSONArray.ints() = (0 until length()).map { getInt(it) }
}
