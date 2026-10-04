package io.github.aaexperiments.discovery

import org.junit.Assert.*
import org.junit.Test

class SemanticDependencyAnalyzerTest {
    @Test fun sdkConsumerBecomesGateWithoutInventingRelationshipOrPrecedence() {
        val first = identifier("Feature__master", "Lflags;->a()Z", "Lgate;->d()Z")
        val second = identifier("Feature__variant", "Lflags;->b()Z", "Lgate;->d()Z")
        val gate = method("Lgate;", "d", "()Z", fields = listOf("Landroid/os/Build\$VERSION;->SDK_INT:I"))
        val records = listOf(ConsumerGraphAnalyzer.MethodRecord(gate, emptyList(), "Ljava/lang/Object;",
            decisions = listOf(
                DecisionUse("Lflags;->a()Z", "IF_EQZ", BranchPolarity.TRUE_ON_FALLTHROUGH),
                DecisionUse("Lflags;->b()Z", "IF_EQZ", BranchPolarity.TRUE_ON_FALLTHROUGH))))

        val result = SemanticDependencyAnalyzer.enrich(listOf(first, second), records).associateBy { it.key }
        val master = result.getValue("Feature__master")
        assertEquals("FENOTYPE_REGISTRY", master.metadata["valueOrigin"])
        assertEquals("NOT_PROVEN", master.metadata["sourcePrecedence"])
        assertEquals(SemanticDependencyAnalyzer.EligibilityGate.SDK_VERSION.name, master.metadata["downstreamGates"])
        assertNull(master.metadata["semanticRelationships"])
    }

    @Test fun rendererAndSnapshotProduceActivationWithoutInventingDependency() {
        val renderer = method("LTemplateView;", "render", "()V")
        val snapshot = method("LConfigBuilder;", "build", "()[B", calls = listOf("Lproto;->toByteArray()[B"))
        val value = identifier("TemplateFeature__enabled", "Lflags;->a()Z", "LTemplateView;->render()V").copy(
            consumers = listOf(
                evidence(renderer, listOf("Lflags;->a()Z", "LTemplateView;->render()V")),
                evidence(snapshot, listOf("Lflags;->a()Z", "LConfigBuilder;->build()[B"))))
        val result = SemanticDependencyAnalyzer.enrich(listOf(value), listOf(
            ConsumerGraphAnalyzer.MethodRecord(renderer, emptyList(), "Ljava/lang/Object;", ConsumerGraphAnalyzer.ConsumerCategory.RENDERER),
            ConsumerGraphAnalyzer.MethodRecord(snapshot, emptyList(), "Ljava/lang/Object;", ConsumerGraphAnalyzer.ConsumerCategory.CONFIG_SNAPSHOT)
        )).single()
        assertTrue(result.metadata["activationScopes"].orEmpty().contains(ActivationScope.NEXT_RENDER.name))
        assertTrue(result.metadata["activationScopes"].orEmpty().contains(ActivationScope.NEXT_CONFIG_SNAPSHOT.name))
        assertEquals(SemanticDependencyAnalyzer.TransportDestination.CONFIG_SNAPSHOT.name, result.metadata["transportDestinations"])
        assertNull(result.metadata["semanticRelationships"])
    }

    @Test fun transitiveSharedInfrastructureDoesNotCreateRelationshipsOrGates() {
        val shared = method("Lframework;", "assemble", "()V", fields = listOf("Landroid/os/Build\$VERSION;->SDK_INT:I"))
        val first = identifier("One__enabled", "Lflags;->a()Z", "Lframework;->assemble()V").copy(
            consumers = listOf(ConsumerEvidence(shared, 3, ConsumerLinkKind.TRANSITIVE_CALL,
                listOf("Lflags;->a()Z", "Lone;->x()Z", "Ltwo;->y()Z", "Lframework;->assemble()V"))))
        val second = identifier("Two__enabled", "Lflags;->b()Z", "Lframework;->assemble()V").copy(
            consumers = listOf(ConsumerEvidence(shared, 3, ConsumerLinkKind.TRANSITIVE_CALL,
                listOf("Lflags;->b()Z", "Lthree;->x()Z", "Lfour;->y()Z", "Lframework;->assemble()V"))))
        val record = ConsumerGraphAnalyzer.MethodRecord(shared, emptyList(), "Ljava/lang/Object;", decisions = listOf(
            DecisionUse("Ltwo;->y()Z", "IF_NEZ", BranchPolarity.TRUE_ON_BRANCH),
            DecisionUse("Lfour;->y()Z", "IF_NEZ", BranchPolarity.TRUE_ON_BRANCH)))
        val result = SemanticDependencyAnalyzer.enrich(listOf(first, second), listOf(record))
        assertTrue(result.all { "semanticRelationships" !in it.metadata && "downstreamGates" !in it.metadata })
    }

    @Test fun preferenceAndRemoteCapabilityExposePrecedenceAndReevaluation() {
        val getter = method("Lprefs;", "enabled", "()Z", calls = listOf("Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z"))
        val consumer = method("Lassistant;", "register", "()V", calls = listOf("Lservice;->supportedCapability()Z"))
        val value = identifier("Assistant__enabled", "Lprefs;->enabled()Z", "Lassistant;->register()V").copy(
            kind = IdentifierKind.PREFERENCE_KEY,
            getterCandidates = listOf(getter),
            consumers = listOf(evidence(consumer, listOf("Lprefs;->enabled()Z", "Lassistant;->register()V"))))
        val result = SemanticDependencyAnalyzer.enrich(listOf(value), listOf(
            ConsumerGraphAnalyzer.MethodRecord(consumer, emptyList(), "Ljava/lang/Object;", ConsumerGraphAnalyzer.ConsumerCategory.CONFIG_SNAPSHOT)
        )).single()
        assertEquals("SHARED_PREFERENCES", result.metadata["valueOrigin"])
        assertEquals("NOT_PROVEN", result.metadata["sourcePrecedence"])
        assertTrue(result.metadata.getValue("downstreamGates").contains("NEGOTIATED_CAPABILITY"))
        assertTrue(result.metadata.getValue("reevaluationTriggers").contains("NEXT_SERVICE_REGISTRATION"))
    }

    @Test fun preferenceListenerIsReportedWithoutInventingRemoteSource() {
        val getter = method("Lprefs;", "read", "()Z", calls = listOf(
            "Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z",
            "Landroid/content/SharedPreferences;->registerOnSharedPreferenceChangeListener(Landroid/content/SharedPreferences\$OnSharedPreferenceChangeListener;)V"))
        val value = identifier("Settings__enabled", "Lprefs;->read()Z", "Lscreen;->bind()V").copy(
            kind = IdentifierKind.PREFERENCE_KEY, getterCandidates = listOf(getter))
        val result = SemanticDependencyAnalyzer.enrich(listOf(value), emptyList()).single()
        assertTrue(result.metadata.getValue("reevaluationTriggers").contains("PREFERENCE_LISTENER"))
        assertEquals("SHARED_PREFERENCES", result.metadata["valueOrigin"])
        assertFalse(result.metadata.containsKey("downstreamGates"))
    }

    @Test fun exhaustedConsumerBudgetSuspendsCrossPathClaimsAndCaches() {
        val getter = method("Lflags;", "a", "()Z")
        val consumer = method("Lshared;", "run", "()V", fields = listOf("Landroid/os/Build\$VERSION;->SDK_INT:I"))
        val value = identifier("Feature__enabled", "Lflags;->a()Z", "Lshared;->run()V").copy(
            getterCandidates = listOf(getter),
            consumers = listOf(evidence(consumer, listOf("Lflags;->a()Z", "Lshared;->run()V"))),
            metadata = mapOf("consumerBudgetExhausted" to "true", "cacheTargets" to "Lbad;->a:Z", "semanticRelationships" to "CO_GATED_WITH:Other__enabled"))
        val result = SemanticDependencyAnalyzer.enrich(listOf(value), listOf(
            ConsumerGraphAnalyzer.MethodRecord(consumer, emptyList(), "Ljava/lang/Object;", ConsumerGraphAnalyzer.ConsumerCategory.ACTIVITY)
        )).single()
        assertEquals("INCOMPLETE_CONSUMER_BUDGET", result.metadata["analysisStatus"])
        assertEquals("SUSPENDED_INCOMPLETE_ANALYSIS", result.metadata["relationshipStatus"])
        assertFalse(result.metadata.containsKey("cacheTargets"))
        assertFalse(result.metadata.containsKey("semanticRelationships"))
        assertFalse(result.metadata.containsKey("downstreamGates"))
    }

    private fun identifier(key: String, getterSignature: String, consumerSignature: String): DiscoveredIdentifier {
        val getter = parse(getterSignature)
        val consumer = parse(consumerSignature)
        return DiscoveredIdentifier(key, IdentifierNamespace.FENOTYPE_FLAG, DiscoveredType.BOOLEAN, "false", emptyList(),
            listOf(getter), ResolutionConfidence.UNIQUE_GETTER, null, true,
            consumers = listOf(evidence(consumer, listOf(getterSignature, consumerSignature))))
    }

    private fun evidence(method: MethodFingerprint, path: List<String>) = ConsumerEvidence(method, 1,
        ConsumerLinkKind.DIRECT_CALL, path)

    private fun parse(signature: String): MethodFingerprint {
        val split = signature.indexOf("->")
        val open = signature.indexOf('(', split)
        return method(signature.substring(0, split), signature.substring(split + 2, open), signature.substring(open))
    }

    private fun method(owner: String, name: String, descriptor: String, fields: List<String> = emptyList(), calls: List<String> = emptyList()) =
        MethodFingerprint("classes.dex", owner, name, descriptor,
            when { descriptor.endsWith("Z") -> DiscoveredType.BOOLEAN; descriptor.endsWith("V") -> DiscoveredType.VOID; else -> DiscoveredType.OBJECT },
            emptyList(), 12, "hash", false, fields, calls)
}
