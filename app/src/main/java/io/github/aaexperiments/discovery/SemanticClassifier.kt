package io.github.aaexperiments.discovery

object SemanticClassifier {
    const val SCHEMA_VERSION = 15

    private val configurableKinds = setOf(
        IdentifierKind.FENOTYPE_FLAG,
        IdentifierKind.PREFERENCE_KEY,
        IdentifierKind.PROTOBUF_CONFIG
    )

    fun kindOf(key: String, legacy: IdentifierNamespace): IdentifierKind = when {
        legacy == IdentifierNamespace.RESOURCE_NAME -> IdentifierKind.RESOURCE
        legacy == IdentifierNamespace.MANIFEST_COMPONENT -> IdentifierKind.MANIFEST_COMPONENT
        key.contains(".action.", ignoreCase = true) || key.endsWith(".action", ignoreCase = true) -> IdentifierKind.INTENT_ACTION
        key.startsWith("SETTINGS_") -> IdentifierKind.TELEMETRY_EVENT
        key.endsWith("_ENABLED_KEY") || key.endsWith("_STATE_KEY") -> IdentifierKind.DERIVED_RUNTIME_STATE
        legacy == IdentifierNamespace.DECISION_GATE || (key.endsWith("_ENABLED") && "__" !in key) -> IdentifierKind.DERIVED_RUNTIME_STATE
        key.startsWith("key_") || key.startsWith("pref_") || legacy == IdentifierNamespace.PREFERENCE_KEY -> IdentifierKind.PREFERENCE_KEY
        "__" in key -> IdentifierKind.FENOTYPE_FLAG
        legacy == IdentifierNamespace.PROTOBUF_CONFIG -> IdentifierKind.PROTOBUF_CONFIG
        key.matches(Regex("[A-Z][A-Z0-9_]*")) && '_' in key -> IdentifierKind.JAVA_ENUM_LITERAL
        key.matches(Regex("[A-Z][A-Z0-9]*")) -> IdentifierKind.ROUTE_STATE
        legacy == IdentifierNamespace.STRING_LITERAL -> IdentifierKind.STRING_LITERAL
        else -> IdentifierKind.UNKNOWN
    }

    fun resolutionOf(confidence: ResolutionConfidence): MappingResolution = when (confidence) {
        ResolutionConfidence.UNIQUE_GETTER -> MappingResolution.DIRECT
        ResolutionConfidence.STRUCTURALLY_VALIDATED -> MappingResolution.STRUCTURAL
        ResolutionConfidence.AMBIGUOUS -> MappingResolution.AMBIGUOUS
        ResolutionConfidence.CATALOGUED -> MappingResolution.CATALOG_ONLY
        ResolutionConfidence.UNSUPPORTED -> MappingResolution.UNSUPPORTED
    }

    fun isConfigurable(kind: IdentifierKind) = kind in configurableKinds

    fun storageTypeOf(type: DiscoveredType): StorageValueType = when (type) {
        DiscoveredType.BOOLEAN -> StorageValueType.BOOLEAN
        DiscoveredType.INT -> StorageValueType.INT
        DiscoveredType.LONG -> StorageValueType.LONG
        DiscoveredType.FLOAT -> StorageValueType.FLOAT
        DiscoveredType.DOUBLE -> StorageValueType.DOUBLE
        DiscoveredType.STRING, DiscoveredType.STRING_SET, DiscoveredType.ENUM -> StorageValueType.STRING
        DiscoveredType.BYTES -> StorageValueType.BYTES
        DiscoveredType.OBJECT -> StorageValueType.OBJECT
        DiscoveredType.VOID -> StorageValueType.VOID
        DiscoveredType.UNKNOWN -> StorageValueType.UNKNOWN
    }

    fun semanticTypeOf(key: String, type: DiscoveredType): SemanticValueType = when {
        key.endsWith("__mode") && type == DiscoveredType.INT -> SemanticValueType.ENUM_BACKED_INT
        key.endsWith("__mode") && type == DiscoveredType.LONG -> SemanticValueType.ENUM_BACKED_LONG
        type == DiscoveredType.BOOLEAN -> SemanticValueType.BOOLEAN
        type == DiscoveredType.INT -> SemanticValueType.INT
        type == DiscoveredType.LONG -> SemanticValueType.LONG
        type == DiscoveredType.FLOAT -> SemanticValueType.FLOAT
        type == DiscoveredType.DOUBLE -> SemanticValueType.DOUBLE
        type == DiscoveredType.STRING -> SemanticValueType.STRING
        type == DiscoveredType.STRING_SET -> SemanticValueType.STRING_SET
        type == DiscoveredType.ENUM -> SemanticValueType.ENUM
        type == DiscoveredType.BYTES -> SemanticValueType.BYTES
        type == DiscoveredType.OBJECT -> SemanticValueType.OBJECT
        type == DiscoveredType.VOID -> SemanticValueType.VOID
        else -> SemanticValueType.UNKNOWN
    }

    fun occurrenceRole(key: String, namespace: IdentifierNamespace, registered: Boolean): LiteralRole = when {
        registered -> LiteralRole.IDENTIFIER_KEY
        namespace == IdentifierNamespace.RESOURCE_NAME -> LiteralRole.RESOURCE_NAME
        key.contains(".action.", true) || key.endsWith(".action", true) -> LiteralRole.INTENT_ACTION
        key.startsWith("SETTINGS_") -> LiteralRole.TELEMETRY_EVENT
        key.matches(Regex("[A-Z][A-Z0-9_]*")) && '_' in key -> LiteralRole.JAVA_ENUM_CONSTANT
        key.matches(Regex("[A-Z][A-Z0-9]*")) -> LiteralRole.ROUTE_STATE
        else -> LiteralRole.UNKNOWN
    }

    fun kindOfOccurrence(key: String, namespace: IdentifierNamespace, role: LiteralRole): IdentifierKind = when (role) {
        LiteralRole.IDENTIFIER_KEY -> when (namespace) {
            IdentifierNamespace.FENOTYPE_FLAG -> IdentifierKind.FENOTYPE_FLAG
            IdentifierNamespace.PREFERENCE_KEY -> IdentifierKind.PREFERENCE_KEY
            IdentifierNamespace.PROTOBUF_CONFIG -> IdentifierKind.PROTOBUF_CONFIG
            else -> kindOf(key, namespace)
        }
        LiteralRole.JAVA_ENUM_CONSTANT -> IdentifierKind.JAVA_ENUM_LITERAL
        LiteralRole.PROTO_ENUM_CONSTANT -> IdentifierKind.PROTO_ENUM
        LiteralRole.INTENT_ACTION -> IdentifierKind.INTENT_ACTION
        LiteralRole.ROUTE_STATE -> IdentifierKind.ROUTE_STATE
        LiteralRole.TELEMETRY_EVENT -> IdentifierKind.TELEMETRY_EVENT
        LiteralRole.RESOURCE_NAME -> IdentifierKind.RESOURCE
        LiteralRole.CAPABILITY_NAME -> IdentifierKind.CAPABILITY_NAME
        else -> kindOf(key, namespace)
    }

    fun confidenceOf(
        confidence: ResolutionConfidence,
        compiledDefault: String?,
        occurrences: List<IdentifierOccurrence>
    ): EvidenceConfidence {
        val getter = when (confidence) {
            ResolutionConfidence.UNIQUE_GETTER -> EvidenceStrength.HIGH
            ResolutionConfidence.STRUCTURALLY_VALIDATED -> EvidenceStrength.PROVEN
            ResolutionConfidence.AMBIGUOUS -> EvidenceStrength.LOW
            else -> EvidenceStrength.NONE
        }
        return EvidenceConfidence(
            identity = if (occurrences.any { it.role == LiteralRole.IDENTIFIER_KEY }) EvidenceStrength.HIGH else EvidenceStrength.MEDIUM,
            storageType = if (getter >= EvidenceStrength.HIGH) EvidenceStrength.PROVEN else getter,
            semanticType = EvidenceStrength.LOW,
            defaultValue = if (compiledDefault != null) EvidenceStrength.MEDIUM else EvidenceStrength.NONE,
            getter = getter,
            evidence = buildList {
                if (occurrences.any { it.role == LiteralRole.IDENTIFIER_KEY }) add("registration-key occurrence")
                if (confidence == ResolutionConfidence.STRUCTURALLY_VALIDATED) add("registration-to-field structural flow")
                if (confidence == ResolutionConfidence.UNIQUE_GETTER) add("unique literal getter")
            },
            ambiguities = if (confidence == ResolutionConfidence.AMBIGUOUS) listOf("multiple getter candidates") else emptyList()
        )
    }

    private fun mergeConfidence(left: EvidenceConfidence, right: EvidenceConfidence) = EvidenceConfidence(
        identity = maxOf(left.identity, right.identity), storageType = maxOf(left.storageType, right.storageType),
        semanticType = maxOf(left.semanticType, right.semanticType), defaultValue = maxOf(left.defaultValue, right.defaultValue),
        getter = maxOf(left.getter, right.getter), runtimeConsumer = maxOf(left.runtimeConsumer, right.runtimeConsumer),
        semantics = maxOf(left.semantics, right.semantics), evidence = (left.evidence + right.evidence).distinct(),
        ambiguities = (left.ambiguities + right.ambiguities).distinct())

    fun editabilityReason(
        kind: IdentifierKind,
        type: DiscoveredType,
        resolution: MappingResolution,
        editable: Boolean,
        metadata: Map<String, String>
    ): EditabilityReason? {
        if (editable) return null
        if (metadata["risk"] == "BLOCKED_BY_SAFETY_POLICY") return EditabilityReason.SAFETY_POLICY
        if (!isConfigurable(kind)) return EditabilityReason.ENTITY_NOT_CONFIGURABLE
        if (type !in DexCatalogAnalyzer.EDITABLE_TYPES) return if (type == DiscoveredType.ENUM) EditabilityReason.ENUM_DOMAIN_UNRESOLVED else EditabilityReason.TYPE_UNSUPPORTED
        if (resolution !in setOf(MappingResolution.DIRECT, MappingResolution.STRUCTURAL, MappingResolution.REVIEWED)) return EditabilityReason.MAPPING_NOT_PROVEN
        return EditabilityReason.NO_SAFE_OVERRIDE_POINT
    }

    fun normalize(identifier: DiscoveredIdentifier): DiscoveredIdentifier {
        val occurrenceKinds = identifier.occurrences.map { it.kind }.filter { it != IdentifierKind.UNKNOWN }.distinct()
        val kind = occurrenceKinds.singleOrNull() ?: kindOf(identifier.key, identifier.namespace)
        val suspiciousReason = suspiciousReason(identifier, kind)
        val resolution = if (suspiciousReason != null) MappingResolution.CATALOG_ONLY else identifier.resolution
        val type = identifier.inferredType
        val semanticType = semanticTypeOf(identifier.key, type)
        val unresolvedEnumDomain = semanticType in setOf(SemanticValueType.ENUM_BACKED_INT, SemanticValueType.ENUM_BACKED_LONG)
        val editable = suspiciousReason == null && identifier.editable && isConfigurable(kind) && type != DiscoveredType.ENUM && !unresolvedEnumDomain
        val reason = when {
            suspiciousReason != null -> suspiciousReason
            identifier.editable && unresolvedEnumDomain -> "Numeric storage is resolved, but the enum domain is not proven."
            identifier.editable && !editable -> "${kind.name} is catalogued evidence, not a configurable value."
            else -> identifier.reason
        }
        val enumMetadata = if (unresolvedEnumDomain) mapOf(
            "enumMapping" to "RESOLVED",
            "enumDomain" to "UNRESOLVED",
            "enumStorage" to type.name
        ) else emptyMap()
        return ProtobufSemanticAnalyzer.enrich(identifier.copy(
            editable = editable,
            inferredType = type,
            kind = kind,
            resolution = resolution,
            storageType = storageTypeOf(type),
            semanticType = semanticType,
            evidenceConfidence = mergeConfidence(identifier.evidenceConfidence, confidenceOf(identifier.confidence, identifier.compiledDefault, identifier.occurrences)),
            editabilityReason = when { suspiciousReason != null -> EditabilityReason.SUSPICIOUS_DISCOVERY; unresolvedEnumDomain -> EditabilityReason.ENUM_DOMAIN_UNRESOLVED; else -> editabilityReason(kind, type, resolution, editable, identifier.metadata) },
            reason = reason,
            metadata = identifier.metadata + enumMetadata + if (suspiciousReason != null) mapOf("discoveryQuality" to "SUSPICIOUS", "suspiciousReason" to suspiciousReason) else emptyMap()
        ))
    }

    private fun suspiciousReason(identifier: DiscoveredIdentifier, kind: IdentifierKind): String? {
        val key = identifier.key
        if (key != key.trim() || key.any { it == '=' || it == '\n' || it == '\r' }) {
            return "Suspicious discovery: formatting or diagnostic text is not a valid configuration key."
        }
        if (identifier.getterCandidates.isNotEmpty() && identifier.getterCandidates.all { it.methodName in setOf("toString", "dump", "dumpDebug") }) {
            return "Suspicious discovery: only representation or dump methods were found."
        }
        if (kind == IdentifierKind.STRING_LITERAL && (identifier.editable || identifier.getterCandidates.isNotEmpty())) {
            return "Suspicious discovery: a library/string literal is not a registered configuration key."
        }
        if (kind == IdentifierKind.PROTOBUF_CONFIG && identifier.occurrences.none { it.role == LiteralRole.IDENTIFIER_KEY }) {
            return "Suspicious discovery: Protobuf classification has no proven registry-key occurrence."
        }
        return null
    }
}
