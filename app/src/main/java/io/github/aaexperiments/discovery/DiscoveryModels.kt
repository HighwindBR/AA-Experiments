package io.github.aaexperiments.discovery

enum class AnalysisStage {
    PREPARING_ARCHIVE,
    READING_DEX,
    RESOLVING_IDENTIFIERS,
    REDISCOVERING_MAPPINGS,
    DEEP_RESOLVING,
    CLASSIFYING_EXPERIMENTS,
    READING_RESOURCES,
    SAVING_CATALOG,
    LOADING_EXPERIMENTS
}

data class AnalysisProgress(
    val stage: AnalysisStage,
    val current: Int? = null,
    val total: Int? = null,
    val detail: String? = null
) {
    val fraction: Float? get() = if (current != null && total != null && total > 0) {
        (current.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    } else null
}

enum class IdentifierNamespace {
    FENOTYPE_FLAG, PREFERENCE_KEY, RESOURCE_NAME, MANIFEST_COMPONENT,
    PROTOBUF_CONFIG, DECISION_GATE, NATIVE_REFERENCE, STRING_LITERAL
}

/** Runtime type used by the hook ABI. Kept for profile compatibility. */
enum class DiscoveredType { BOOLEAN, INT, LONG, FLOAT, DOUBLE, STRING, STRING_SET, ENUM, BYTES, OBJECT, VOID, UNKNOWN }

/** Physical representation read from DEX or another backing store. */
enum class StorageValueType { BOOLEAN, INT, LONG, FLOAT, DOUBLE, STRING, BYTES, OBJECT, VOID, UNKNOWN }

/** Meaning of a value. This is deliberately independent from its storage representation. */
enum class SemanticValueType {
    BOOLEAN, INT, LONG, FLOAT, DOUBLE, STRING, STRING_SET, STRING_LIST,
    ENUM, JAVA_ENUM, PROTO_ENUM, ENUM_BACKED_INT, ENUM_BACKED_LONG,
    BYTES, PROTOBUF, PROTO_LIST, OBJECT, VOID, UNKNOWN
}
enum class ResolutionConfidence { CATALOGUED, UNIQUE_GETTER, STRUCTURALLY_VALIDATED, AMBIGUOUS, UNSUPPORTED }

enum class IdentifierKind {
    FENOTYPE_FLAG, PREFERENCE_KEY, PROTOBUF_CONFIG, JAVA_ENUM_LITERAL, PROTO_ENUM,
    ENUM_BACKED_NUMERIC, INTENT_ACTION,
    ROUTE_STATE, TELEMETRY_EVENT, DERIVED_RUNTIME_STATE, RESOURCE,
    MANIFEST_COMPONENT, CAPABILITY_NAME, STRING_LITERAL, UNKNOWN,
    /** Old serialized name accepted during schema migration. */
    ENUM_LITERAL
}

enum class MappingResolution { DIRECT, STRUCTURAL, REVIEWED, ADAPTER, AMBIGUOUS, CATALOG_ONLY, UNSUPPORTED }
enum class RuntimeConsumerStatus { UNKNOWN, PRESENT, DORMANT }
enum class ConsumerLinkKind { DIRECT_CALL, INTERFACE_DISPATCH, TRANSITIVE_CALL }
enum class LiteralRole {
    IDENTIFIER_KEY, DEFAULT_VALUE, JAVA_ENUM_CONSTANT, PROTO_ENUM_CONSTANT,
    INTENT_ACTION, ROUTE_STATE, TELEMETRY_EVENT, RESOURCE_NAME, CAPABILITY_NAME,
    DIAGNOSTIC_TEXT, UNKNOWN
}

enum class EvidenceStrength { NONE, LOW, MEDIUM, HIGH, PROVEN }

/** Confidence is tracked per claim; a proven getter does not imply proven semantics. */
data class EvidenceConfidence(
    val identity: EvidenceStrength = EvidenceStrength.NONE,
    val storageType: EvidenceStrength = EvidenceStrength.NONE,
    val semanticType: EvidenceStrength = EvidenceStrength.NONE,
    val defaultValue: EvidenceStrength = EvidenceStrength.NONE,
    val getter: EvidenceStrength = EvidenceStrength.NONE,
    val runtimeConsumer: EvidenceStrength = EvidenceStrength.NONE,
    val semantics: EvidenceStrength = EvidenceStrength.NONE,
    val evidence: List<String> = emptyList(),
    val ambiguities: List<String> = emptyList()
)

enum class SemanticsProvenance { NONE, AUTOMATIC, MANUAL_EXACT_BUILD }
enum class EditabilityReason {
    SAFETY_POLICY, ENTITY_NOT_CONFIGURABLE, TYPE_UNSUPPORTED, ENUM_DOMAIN_UNRESOLVED,
    MAPPING_NOT_PROVEN, NO_SAFE_OVERRIDE_POINT, NO_RUNTIME_CONSUMER, SUSPICIOUS_DISCOVERY
}

data class MethodFingerprint(
    val dex: String,
    val className: String,
    val methodName: String,
    val descriptor: String,
    val returnType: DiscoveredType,
    val parameterTypes: List<String>,
    val instructionCount: Int,
    val opcodeSha256: String,
    val isStaticInitializer: Boolean,
    val referencedFields: List<String>,
    val invokedMethods: List<String>,
    val invokeOutcomeCodes: ByteArray? = null,
    val unsupportedFlowOpcodes: List<String> = emptyList()
)

data class IdentifierOccurrence(
    val method: MethodFingerprint,
    val literalOrdinal: Int,
    val candidateDefault: String? = null,
    val associatedFields: List<String> = emptyList(),
    val instructionOffset: Int = -1,
    val role: LiteralRole = LiteralRole.UNKNOWN,
    val kind: IdentifierKind = IdentifierKind.UNKNOWN,
    val storageType: StorageValueType = StorageValueType.UNKNOWN,
    val semanticType: SemanticValueType = SemanticValueType.UNKNOWN,
    val classificationConfidence: EvidenceStrength = EvidenceStrength.NONE,
    val registryId: String? = null,
    val defaultSource: String? = null
)

data class ConsumerEvidence(
    val method: MethodFingerprint,
    val depth: Int,
    val linkKind: ConsumerLinkKind,
    val path: List<String>,
    val semanticSummary: String? = null
)

data class DiscoveredIdentifier(
    val key: String,
    val namespace: IdentifierNamespace,
    val inferredType: DiscoveredType,
    val compiledDefault: String?,
    val occurrences: List<IdentifierOccurrence>,
    val getterCandidates: List<MethodFingerprint>,
    val confidence: ResolutionConfidence,
    val reason: String?,
    val editable: Boolean,
    val metadata: Map<String, String> = emptyMap(),
    val kind: IdentifierKind = SemanticClassifier.kindOf(key, namespace),
    val resolution: MappingResolution = SemanticClassifier.resolutionOf(confidence),
    val runtimeConsumerStatus: RuntimeConsumerStatus = RuntimeConsumerStatus.UNKNOWN,
    val semanticsReviewed: Boolean = false,
    val editabilityReason: EditabilityReason? = SemanticClassifier.editabilityReason(kind, inferredType, resolution, editable, metadata),
    val consumers: List<ConsumerEvidence> = emptyList(),
    val storageType: StorageValueType = SemanticClassifier.storageTypeOf(inferredType),
    val semanticType: SemanticValueType = SemanticClassifier.semanticTypeOf(key, inferredType),
    val evidenceConfidence: EvidenceConfidence = SemanticClassifier.confidenceOf(confidence, compiledDefault, occurrences),
    val semanticsProvenance: SemanticsProvenance = if (semanticsReviewed) SemanticsProvenance.MANUAL_EXACT_BUILD else SemanticsProvenance.NONE
)

data class DexInventory(
    val schemaVersion: Int = 3,
    val baseSha256: String,
    val dexSha256: Map<String, String>,
    val generatedAtEpochMs: Long,
    val identifiers: List<DiscoveredIdentifier>,
    val errors: List<String> = emptyList(),
    val resolverSchemaVersion: Int = SemanticClassifier.SCHEMA_VERSION,
    val buildFingerprintSha256: String = baseSha256,
    val packageVersionCode: Long = 0,
    val packageArtifactSha256: Map<String, String> = emptyMap(),
)
