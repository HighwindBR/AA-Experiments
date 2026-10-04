package io.github.aaexperiments.discovery

/** Adds semantic diagnostics without flattening unrelated consumer paths into one claim. */
object SemanticDependencyAnalyzer {
    enum class EligibilityGate { SDK_VERSION, SYSTEM_FEATURE, NEGOTIATED_CAPABILITY }
    enum class ValueOrigin { FENOTYPE_REGISTRY, SHARED_PREFERENCES, SYSTEM_PROPERTY, REMOTE_STATE, RUNTIME_STATE, DERIVED_VALUE, UNKNOWN }
    enum class TransportDestination { CONFIG_SNAPSHOT, SERVICE_REGISTRATION, PROJECTION_CONFIGURATION, CAPABILITY_BUNDLE }
    enum class ReevaluationTrigger { LIVE_READ, PREFERENCE_LISTENER, NEXT_RENDER, NEXT_ACTIVITY, NEXT_SERVICE_REGISTRATION, NEXT_PROJECTION_CONFIG, NEXT_PROCESS }

    private fun signature(method: MethodFingerprint) = "${method.className}->${method.methodName}${method.descriptor}"

    fun selectRelevantRecords(identifiers: List<DiscoveredIdentifier>, records: List<ConsumerGraphAnalyzer.MethodRecord>): List<ConsumerGraphAnalyzer.MethodRecord> {
        val relevant = identifiers.asSequence().filter { it.getterCandidates.isNotEmpty() }.flatMap { it.consumers.asSequence() }
            .filter { it.depth <= MAX_SEMANTIC_DEPTH }.map { signature(it.method) }.toHashSet()
        if (relevant.isEmpty()) return emptyList()
        return records.filter { signature(it.method) in relevant }
    }

    fun enrich(identifiers: List<DiscoveredIdentifier>, records: List<ConsumerGraphAnalyzer.MethodRecord>): List<DiscoveredIdentifier> {
        val relevant = identifiers.asSequence().flatMap { it.consumers.asSequence() }.filter { it.depth <= MAX_SEMANTIC_DEPTH }
            .map { signature(it.method) }.toHashSet()
        val recordsBySignature = HashMap<String, ConsumerGraphAnalyzer.MethodRecord>(relevant.size)
        records.forEach { record -> signature(record.method).takeIf { it in relevant }?.let { recordsBySignature[it] = record } }

        return identifiers.map { identifier ->
            if (identifier.getterCandidates.isEmpty()) return@map identifier
            val exhausted = identifier.metadata["consumerBudgetExhausted"].toBoolean()
            val origins = linkedSetOf<ValueOrigin>()
            val originEvidence = linkedSetOf<String>()
            val gates = linkedSetOf<EligibilityGate>()
            val gateEvidence = linkedSetOf<String>()
            val transports = linkedSetOf<TransportDestination>()
            val transportEvidence = linkedSetOf<String>()
            val triggers = linkedSetOf<ReevaluationTrigger>()
            val activation = linkedSetOf<ActivationScope>()

            when (identifier.kind) {
                IdentifierKind.FENOTYPE_FLAG -> origins += ValueOrigin.FENOTYPE_REGISTRY
                IdentifierKind.PREFERENCE_KEY -> origins += ValueOrigin.SHARED_PREFERENCES
                IdentifierKind.DERIVED_RUNTIME_STATE -> origins += ValueOrigin.DERIVED_VALUE
                else -> Unit
            }
            identifier.getterCandidates.forEach { inspectGetter(it, origins, triggers, originEvidence) }
            if (identifier.metadata["consumptionPaths"].orEmpty().split('|').contains(ConsumptionPath.LIVE.name)) {
                triggers += ReevaluationTrigger.LIVE_READ; activation += ActivationScope.LIVE
            }

            if (!exhausted) identifier.consumers.forEach consumerLoop@{ consumer ->
                if (consumer.depth > MAX_SEMANTIC_DEPTH) return@consumerLoop
                val consumerSignature = signature(consumer.method)
                val record = recordsBySignature[consumerSignature] ?: return@consumerLoop
                when (record.category) {
                    ConsumerGraphAnalyzer.ConsumerCategory.RENDERER -> { activation += ActivationScope.NEXT_RENDER; triggers += ReevaluationTrigger.NEXT_RENDER }
                    ConsumerGraphAnalyzer.ConsumerCategory.ACTIVITY -> { activation += ActivationScope.NEXT_ACTIVITY; triggers += ReevaluationTrigger.NEXT_ACTIVITY }
                    ConsumerGraphAnalyzer.ConsumerCategory.CONFIG_SNAPSHOT -> {
                        activation += ActivationScope.NEXT_CONFIG_SNAPSHOT; triggers += ReevaluationTrigger.NEXT_PROJECTION_CONFIG
                        transports += TransportDestination.CONFIG_SNAPSHOT; transportEvidence += consumerSignature
                    }
                    else -> Unit
                }
                if (consumer.depth == 1 && record.method.referencedFields.any { it == "Landroid/os/Build\$VERSION;->SDK_INT:I" }) {
                    gates += EligibilityGate.SDK_VERSION; gateEvidence += "$consumerSignature:Build.VERSION.SDK_INT"
                }
                if (consumer.depth == 1 && record.method.invokedMethods.any { "Landroid/content/pm/PackageManager;->hasSystemFeature(" in it }) {
                    gates += EligibilityGate.SYSTEM_FEATURE; gateEvidence += "$consumerSignature:PackageManager.hasSystemFeature"
                }
                if (consumer.depth == 1 && record.method.invokedMethods.any(::looksLikeCapability)) {
                    gates += EligibilityGate.NEGOTIATED_CAPABILITY; triggers += ReevaluationTrigger.NEXT_SERVICE_REGISTRATION
                    gateEvidence += "$consumerSignature:negotiated-capability"
                }
            }

            val cleaned = identifier.metadata - UNTRUSTED_ALPHA26_KEYS - if (exhausted) EXHAUSTED_GRAPH_KEYS else emptySet()
            val finalActivation = if (exhausted) activation.filter { it == ActivationScope.LIVE }.toSet() else activation
            val enriched = cleaned + buildMap {
                put("analysisStatus", if (exhausted) "INCOMPLETE_CONSUMER_BUDGET" else "COMPLETE_WITHIN_BOUNDS")
                put("analysisConfidence", if (exhausted) "INSUFFICIENT_FOR_CROSS_PATH_SEMANTICS" else "STRUCTURAL")
                put("valueOrigin", origins.singleOrNull()?.name ?: if (origins.isEmpty()) ValueOrigin.UNKNOWN.name else "AMBIGUOUS")
                identifier.compiledDefault?.let { put("compiledFallback", it) }
                put("sourcePrecedence", "NOT_PROVEN")
                put("relationshipStatus", if (exhausted) "SUSPENDED_INCOMPLETE_ANALYSIS" else "DEFERRED_NO_DECISION_REGION_PROOF")
                if (originEvidence.isNotEmpty()) put("originEvidence", originEvidence.take(8).joinToString("|"))
                if (!exhausted && gates.isNotEmpty()) put("downstreamGates", gates.joinToString("|") { it.name })
                if (!exhausted && gateEvidence.isNotEmpty()) put("downstreamGateEvidence", gateEvidence.take(8).joinToString("|"))
                if (!exhausted && transports.isNotEmpty()) put("transportDestinations", transports.joinToString("|") { it.name })
                if (!exhausted && transportEvidence.isNotEmpty()) put("transportEvidence", transportEvidence.take(8).joinToString("|"))
                if (triggers.isNotEmpty()) put("reevaluationTriggers", triggers.joinToString("|") { it.name })
                put("derivedInputs", "NOT_PROVEN")
                put("activationScopes", if (finalActivation.isEmpty()) ActivationScope.UNKNOWN.name else finalActivation.joinToString("|") { it.name })
                put("activationScope", when { finalActivation.isEmpty() -> ActivationScope.UNKNOWN.name; finalActivation.size == 1 -> finalActivation.single().name; else -> ActivationScope.MIXED.name })
            }
            identifier.copy(
                metadata = enriched,
                evidenceConfidence = identifier.evidenceConfidence.copy(
                    semantics = if (exhausted) minOf(identifier.evidenceConfidence.semantics, EvidenceStrength.LOW) else maxOf(identifier.evidenceConfidence.semantics, EvidenceStrength.MEDIUM),
                    evidence = (identifier.evidenceConfidence.evidence + if (exhausted) "cross-path semantics suspended: consumer budget exhausted" else "provenance-isolated semantic pass").distinct()
                )
            )
        }
    }

    private fun inspectGetter(method: MethodFingerprint, origins: MutableSet<ValueOrigin>, triggers: MutableSet<ReevaluationTrigger>, evidence: MutableSet<String>) {
        val methodSignature = signature(method)
        val calls = method.invokedMethods
        val readsPreferences = calls.any { "Landroid/content/SharedPreferences;->get" in it || "Landroidx/preference/" in it }
        if (readsPreferences) { origins += ValueOrigin.SHARED_PREFERENCES; evidence += "$methodSignature:SharedPreferences-read" }
        if (readsPreferences && calls.any { "registerOnSharedPreferenceChangeListener" in it || "addPreferenceChangeListener" in it }) {
            triggers += ReevaluationTrigger.PREFERENCE_LISTENER; evidence += "$methodSignature:same-getter-preference-listener"
        }
        if (calls.any { "Landroid/os/SystemProperties;->" in it }) {
            origins += ValueOrigin.SYSTEM_PROPERTY; evidence += "$methodSignature:SystemProperties-read"
        }
        if (calls.any(::looksLikeCapability) || method.referencedFields.any { it.contains("SupportedVersionInfo") || it.contains("Capability") }) {
            origins += ValueOrigin.REMOTE_STATE; triggers += ReevaluationTrigger.NEXT_SERVICE_REGISTRATION
            evidence += "$methodSignature:remote-state-read"
        }
    }

    private fun looksLikeCapability(method: String): Boolean {
        val lower = method.lowercase()
        return ("capability" in lower || "supportedversion" in lower || "supportedfeature" in lower) && method.endsWith(")Z")
    }

    private const val MAX_SEMANTIC_DEPTH = 2
    private val UNTRUSTED_ALPHA26_KEYS = setOf("valueSources", "sourceRoutingEvidence", "postGetterOverrideRisk", "semanticRelationships", "relationshipConfidence", "strongRelationshipDeferred", "eligibilityGates", "eligibilityGateEvidence", "semanticActivationScopes", "sourcePrecedence", "reevaluationTriggers")
    private val EXHAUSTED_GRAPH_KEYS = setOf("cacheTargets", "cacheTransformations", "fieldLineage", "fieldLineageEdgeCount", "fieldLineageBudgetExhausted", "runtimeLatchTargets", "fieldConsumerMethods")
}
