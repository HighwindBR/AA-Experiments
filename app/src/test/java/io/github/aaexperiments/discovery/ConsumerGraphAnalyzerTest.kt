package io.github.aaexperiments.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsumerGraphAnalyzerTest {
    private fun method(cls: String, name: String, descriptor: String = "()Z", calls: List<String> = emptyList()) = MethodFingerprint(
        "classes.dex", cls, name, descriptor, if (descriptor.endsWith("Z")) DiscoveredType.BOOLEAN else DiscoveredType.VOID,
        emptyList(), 3, name, false, emptyList(), calls
    )

    private fun MethodFingerprint.withFlow(vararg outcomes: Pair<String, InvokeCallOutcome>): MethodFingerprint {
        val byTarget = outcomes.toMap()
        return copy(invokeOutcomeCodes = ByteArray(invokedMethods.size) { index ->
            (byTarget[invokedMethods[index]] ?: InvokeCallOutcome.UNKNOWN).code
        })
    }

    @Test fun followsInterfaceAndWrapperCallsForUxPrototype() {
        val getter = method("Laczi;", "b")
        val wrapper = method("Laczg;", "d", calls = listOf("Laczh;->b()Z"))
        val launcher = method("Llauncher;", "register", "()V", listOf("Laczg;->d()Z"))
        val identifier = DiscoveredIdentifier(
            "UxPrototype__enabled", IdentifierNamespace.FENOTYPE_FLAG, DiscoveredType.BOOLEAN, "false",
            emptyList(), listOf(getter), ResolutionConfidence.UNIQUE_GETTER, null, true
        )
        val enriched = ConsumerGraphAnalyzer.enrich(listOf(identifier), listOf(
            ConsumerGraphAnalyzer.MethodRecord(getter, listOf("Laczh;"), "Ljava/lang/Object;"),
            ConsumerGraphAnalyzer.MethodRecord(wrapper, emptyList(), "Ljava/lang/Object;"),
            ConsumerGraphAnalyzer.MethodRecord(launcher, emptyList(), "Ljava/lang/Object;")
        ), "676205e71a75f38e0565b9be7d462c296b14490cef9a3feda3791098e5baae04").single()
        assertEquals(RuntimeConsumerStatus.PRESENT, enriched.runtimeConsumerStatus)
        assertTrue(enriched.semanticsReviewed)
        assertTrue(enriched.consumers.any { it.method.className == "Laczg;" && it.linkKind == ConsumerLinkKind.INTERFACE_DISPATCH })
        assertTrue(enriched.consumers.any { it.method.className == "Llauncher;" && it.depth == 2 })
    }

    @Test fun reviewedSemanticsAreBoundToExactBuildHash() {
        val getter = method("Lflag;", "a")
        val identifier = DiscoveredIdentifier("Coolwalk__use_light_dark_theme", IdentifierNamespace.FENOTYPE_FLAG, DiscoveredType.BOOLEAN, "false",
            emptyList(), listOf(getter), ResolutionConfidence.UNIQUE_GETTER, null, true)
        val records = listOf(ConsumerGraphAnalyzer.MethodRecord(getter, emptyList(), "Ljava/lang/Object;"))
        val wrongBuild = ConsumerGraphAnalyzer.enrich(listOf(identifier), records, "different").single()
        val auditedBuild = ConsumerGraphAnalyzer.enrich(listOf(identifier), records,
            "676205e71a75f38e0565b9be7d462c296b14490cef9a3feda3791098e5baae04").single()
        assertTrue(!wrongBuild.semanticsReviewed)
        assertTrue(auditedBuild.semanticsReviewed)
        assertEquals("Shows the launcher entry.", ReviewedSemanticsRegistry.find(
            "676205e71a75f38e0565b9be7d462c296b14490cef9a3feda3791098e5baae04", "UxPrototype__enabled")?.trueMeaning)
    }

    @Test fun marksGetterWithoutCallersDormantWithoutChangingEditability() {
        val getter = method("Lflag;", "a")
        val identifier = DiscoveredIdentifier("Family__flag", IdentifierNamespace.FENOTYPE_FLAG, DiscoveredType.BOOLEAN, "false",
            emptyList(), listOf(getter), ResolutionConfidence.UNIQUE_GETTER, null, true)
        val enriched = ConsumerGraphAnalyzer.enrich(listOf(identifier), listOf(
            ConsumerGraphAnalyzer.MethodRecord(getter, emptyList(), "Ljava/lang/Object;")
        )).single()
        assertEquals(RuntimeConsumerStatus.DORMANT, enriched.runtimeConsumerStatus)
        assertTrue(enriched.editable)
    }

    @Test fun diagnosticCallerDoesNotProveRuntimeConsumption() {
        val getter = method("Lflag;", "a")
        val dump = method("Ldebug;", "dump", calls = listOf("Lflag;->a()Z"))
        val identifier = DiscoveredIdentifier("Family__flag", IdentifierNamespace.FENOTYPE_FLAG, DiscoveredType.BOOLEAN, "false",
            emptyList(), listOf(getter), ResolutionConfidence.UNIQUE_GETTER, null, true)
        val enriched = ConsumerGraphAnalyzer.enrich(listOf(identifier), listOf(
            ConsumerGraphAnalyzer.MethodRecord(getter, emptyList(), "Ljava/lang/Object;", ConsumerGraphAnalyzer.ConsumerCategory.RUNTIME),
            ConsumerGraphAnalyzer.MethodRecord(dump, emptyList(), "Ljava/lang/Object;", ConsumerGraphAnalyzer.ConsumerCategory.DIAGNOSTIC)
        )).single()
        assertEquals(RuntimeConsumerStatus.DORMANT, enriched.runtimeConsumerStatus)
        assertEquals("1", enriched.metadata["diagnosticConsumerCount"])
    }

    @Test fun wrapperOnlyCallerDoesNotProveFeatureConsumption() {
        val getter = method("Lflag;", "a")
        val wrapper = method("Lwrapper;", "a", calls = listOf("Lflag;->a()Z"))
        val identifier = DiscoveredIdentifier("CarAppLibrary__conversation_item_enable_custom_actions_ui",
            IdentifierNamespace.FENOTYPE_FLAG, DiscoveredType.BOOLEAN, "false", emptyList(), listOf(getter),
            ResolutionConfidence.UNIQUE_GETTER, null, true)
        val enriched = ConsumerGraphAnalyzer.enrich(listOf(identifier), listOf(
            ConsumerGraphAnalyzer.MethodRecord(getter, emptyList(), "Ljava/lang/Object;"),
            ConsumerGraphAnalyzer.MethodRecord(wrapper, emptyList(), "Ljava/lang/Object;", ConsumerGraphAnalyzer.ConsumerCategory.WRAPPER_ONLY)
        )).single()
        assertEquals(RuntimeConsumerStatus.DORMANT, enriched.runtimeConsumerStatus)
        assertEquals("false", enriched.metadata["semanticConsumerProven"])
        assertEquals("1", enriched.metadata["wrapperOnlyConsumerCount"])
    }

    @Test fun worklistReachesConsumerBeyondFormerDepthFourLimit() {
        val getter = method("Lchain0;", "a")
        val records = mutableListOf(ConsumerGraphAnalyzer.MethodRecord(getter, emptyList(), "Ljava/lang/Object;"))
        var target = "Lchain0;->a()Z"
        repeat(8) { depth ->
            val wrapper = method("Lchain${depth + 1};", "a", calls = listOf(target))
            records += ConsumerGraphAnalyzer.MethodRecord(wrapper, emptyList(), "Ljava/lang/Object;")
            target = "Lchain${depth + 1};->a()Z"
        }
        val identifier = DiscoveredIdentifier("Deep__flag", IdentifierNamespace.FENOTYPE_FLAG, DiscoveredType.BOOLEAN, "false",
            emptyList(), listOf(getter), ResolutionConfidence.UNIQUE_GETTER, null, true)
        val enriched = ConsumerGraphAnalyzer.enrich(listOf(identifier), records).single()
        assertTrue(enriched.consumers.any { it.depth == 8 })
    }

    @Test fun directedRetryCanResolveAPathThatExhaustsTheNormalBudget() {
        val getter = method("Ldeep0;", "a")
        val records = mutableListOf(ConsumerGraphAnalyzer.MethodRecord(getter, emptyList(), "Ljava/lang/Object;"))
        var target = "Ldeep0;->a()Z"
        repeat(300) { depth ->
            val wrapper = method("Ldeep${depth + 1};", "a", calls = listOf(target))
            records += ConsumerGraphAnalyzer.MethodRecord(wrapper, emptyList(), "Ljava/lang/Object;")
            target = "Ldeep${depth + 1};->a()Z"
        }
        val identifier = DiscoveredIdentifier("DeepRetry__flag", IdentifierNamespace.FENOTYPE_FLAG, DiscoveredType.BOOLEAN, "false",
            emptyList(), listOf(getter), ResolutionConfidence.UNIQUE_GETTER, null, true)

        val normal = ConsumerGraphAnalyzer.enrich(listOf(identifier), records, maxVisitedNodes = 64).single()
        val directed = ConsumerGraphAnalyzer.enrich(listOf(identifier), records, maxVisitedNodes = 512).single()

        assertEquals("true", normal.metadata["consumerBudgetExhausted"])
        assertEquals("false", directed.metadata["consumerBudgetExhausted"])
        assertEquals("512", directed.metadata["consumerBudget"])
    }

    @Test fun batchCancellationStopsBetweenIdentifiersAndKeepsCompletedResult() {
        val firstGetter = method("Lfirst;", "a")
        val secondGetter = method("Lsecond;", "a")
        val first = DiscoveredIdentifier("First__flag", IdentifierNamespace.FENOTYPE_FLAG, DiscoveredType.BOOLEAN, "false",
            emptyList(), listOf(firstGetter), ResolutionConfidence.UNIQUE_GETTER, null, true)
        val second = DiscoveredIdentifier("Second__flag", IdentifierNamespace.FENOTYPE_FLAG, DiscoveredType.BOOLEAN, "false",
            emptyList(), listOf(secondGetter), ResolutionConfidence.UNIQUE_GETTER, null, true)
        val completed = mutableListOf<String>()

        val result = ConsumerGraphAnalyzer.enrich(
            listOf(first, second),
            listOf(
                ConsumerGraphAnalyzer.MethodRecord(firstGetter, emptyList(), "Ljava/lang/Object;"),
                ConsumerGraphAnalyzer.MethodRecord(secondGetter, emptyList(), "Ljava/lang/Object;"),
            ),
            shouldContinue = { completed.isEmpty() },
            onIdentifier = { completed += it.key },
        )

        assertEquals(listOf("First__flag"), completed)
        assertEquals(listOf("First__flag"), result.map { it.key })
    }

    @Test fun targetedTraversalPrefersSemanticEndpointAndStopsTechnicalBranch() {
        val getter = method("Lflag;", "a")
        val activity = method("Lfeature/FeatureActivity;", "onCreate", "()V", listOf("Lflag;->a()Z"))
            .withFlow("Lflag;->a()Z" to InvokeCallOutcome.BRANCHED)
        val dump = method("Ldebug/Dumper;", "dump", "()V", listOf("Lflag;->a()Z"))
        val identifier = DiscoveredIdentifier("Targeted__flag", IdentifierNamespace.FENOTYPE_FLAG, DiscoveredType.BOOLEAN, "false",
            emptyList(), listOf(getter), ResolutionConfidence.UNIQUE_GETTER, null, true)

        val enriched = ConsumerGraphAnalyzer.enrich(listOf(identifier), listOf(
            ConsumerGraphAnalyzer.MethodRecord(getter, emptyList(), "Ljava/lang/Object;"),
            ConsumerGraphAnalyzer.MethodRecord(activity, emptyList(), "Ljava/lang/Object;", ConsumerGraphAnalyzer.ConsumerCategory.ACTIVITY),
            ConsumerGraphAnalyzer.MethodRecord(dump, emptyList(), "Ljava/lang/Object;", ConsumerGraphAnalyzer.ConsumerCategory.DUMP),
        ), maxVisitedNodes = 64, targetedTraversal = true).single()

        assertEquals("SEMANTIC_ENDPOINT_FOUND", enriched.metadata["targetedResolutionReason"])
        assertEquals("true", enriched.metadata["targetedResolveAttempted"])
        assertEquals("1", enriched.metadata["targetedTechnicalPruned"])
    }

    @Test fun duplicateGetterCallsitesUseTheirOwnAlignedOutcomes() {
        val getter = method("Lflag;", "a")
        val target = "Lflag;->a()Z"
        val activity = method("Lfeature/FeatureActivity;", "onCreate", "()V", listOf(target)).copy(
            invokeOutcomeCodes = byteArrayOf(InvokeCallOutcome.BRANCHED.code)
        )
        val identifier = DiscoveredIdentifier("Duplicate__flag", IdentifierNamespace.FENOTYPE_FLAG, DiscoveredType.BOOLEAN, "false",
            emptyList(), listOf(getter), ResolutionConfidence.UNIQUE_GETTER, null, true)

        val enriched = ConsumerGraphAnalyzer.enrich(listOf(identifier), listOf(
            ConsumerGraphAnalyzer.MethodRecord(getter, emptyList(), "Ljava/lang/Object;"),
            ConsumerGraphAnalyzer.MethodRecord(activity, emptyList(), "Ljava/lang/Object;", ConsumerGraphAnalyzer.ConsumerCategory.ACTIVITY),
        ), maxVisitedNodes = 64, targetedTraversal = true).single()

        assertEquals("SEMANTIC_ENDPOINT_FOUND", enriched.metadata["targetedResolutionReason"])
        assertEquals("1", enriched.metadata["provenBranchedCount"])
    }

    @Test fun targetedTraversalReportsSharedFanoutInsteadOfPretendingCompleteness() {
        val getter = method("Lflag;", "a")
        val records = mutableListOf(ConsumerGraphAnalyzer.MethodRecord(getter, emptyList(), "Ljava/lang/Object;"))
        repeat(48) { index ->
            records += ConsumerGraphAnalyzer.MethodRecord(
                method("Lshared$index;", "read", "()V", listOf("Lflag;->a()Z"))
                    .withFlow("Lflag;->a()Z" to InvokeCallOutcome.FORWARDED_ARGUMENT), emptyList(), "Ljava/lang/Object;"
            )
        }
        val identifier = DiscoveredIdentifier("Fanout__flag", IdentifierNamespace.FENOTYPE_FLAG, DiscoveredType.BOOLEAN, "false",
            emptyList(), listOf(getter), ResolutionConfidence.UNIQUE_GETTER, null, true)

        val enriched = ConsumerGraphAnalyzer.enrich(listOf(identifier), records, maxVisitedNodes = 128, targetedTraversal = true).single()

        assertEquals("SHARED_INFRASTRUCTURE_FANOUT", enriched.metadata["targetedResolutionReason"])
        assertEquals("16", enriched.metadata["targetedFanoutPruned"])
    }

    @Test fun targetedTraversalUsesSemanticCorridorAndTreatsEndpointAsTerminal() {
        val getter = method("Lflag;", "a")
        val wrapper = method("Lfeature/Wrapper;", "read", "()Z", listOf("Lflag;->a()Z"))
            .withFlow("Lflag;->a()Z" to InvokeCallOutcome.RETURNED)
        val renderer = method("Lfeature/FeatureRenderer;", "render", "()V", listOf("Lfeature/Wrapper;->read()Z"))
            .withFlow("Lfeature/Wrapper;->read()Z" to InvokeCallOutcome.BRANCHED)
        val afterEndpoint = method("Lapp/Host;", "draw", "()V", listOf("Lfeature/FeatureRenderer;->render()V"))
        val records = mutableListOf(
            ConsumerGraphAnalyzer.MethodRecord(getter, emptyList(), "Ljava/lang/Object;"),
            ConsumerGraphAnalyzer.MethodRecord(wrapper, emptyList(), "Ljava/lang/Object;"),
            ConsumerGraphAnalyzer.MethodRecord(renderer, emptyList(), "Ljava/lang/Object;", ConsumerGraphAnalyzer.ConsumerCategory.RENDERER),
            ConsumerGraphAnalyzer.MethodRecord(afterEndpoint, emptyList(), "Ljava/lang/Object;"),
        )
        repeat(40) { index ->
            records += ConsumerGraphAnalyzer.MethodRecord(
                method("Lnoise$index;", "shared", "()V", listOf("Lflag;->a()Z")), emptyList(), "Ljava/lang/Object;"
            )
        }
        val identifier = DiscoveredIdentifier("Corridor__flag", IdentifierNamespace.FENOTYPE_FLAG, DiscoveredType.BOOLEAN, "false",
            emptyList(), listOf(getter), ResolutionConfidence.UNIQUE_GETTER, null, true)

        val enriched = ConsumerGraphAnalyzer.enrich(
            listOf(identifier), records, maxVisitedNodes = 64, targetedTraversal = true
        ).single()

        assertEquals("SEMANTIC_ENDPOINT_FOUND", enriched.metadata["targetedResolutionReason"])
        assertEquals(ConsumerGraphAnalyzer.TARGETED_STRATEGY_VERSION, enriched.metadata["targetedStrategyVersion"])
        assertTrue(enriched.consumers.any { it.method.className == "Lfeature/FeatureRenderer;" })
        assertTrue(enriched.consumers.none { it.method.className == "Lapp/Host;" })
    }

    @Test fun distinguishesLiveInstanceAndStaticConsumption() {
        val getter = method("Lflag;", "a", "()J")
        val live = method("Llive;", "read", "()V", listOf("Lflag;->a()J"))
        val instance = method("Lholder;", "<init>", "()V", listOf("Lflag;->a()J"))
        val static = method("Lconstants;", "<clinit>", "()V", listOf("Lflag;->a()J"))
        val identifier = DiscoveredIdentifier("Family__delay_ms", IdentifierNamespace.FENOTYPE_FLAG, DiscoveredType.LONG, "10",
            emptyList(), listOf(getter), ResolutionConfidence.UNIQUE_GETTER, null, true)
        val enriched = ConsumerGraphAnalyzer.enrich(listOf(identifier), listOf(
            ConsumerGraphAnalyzer.MethodRecord(getter, emptyList(), "Ljava/lang/Object;"),
            ConsumerGraphAnalyzer.MethodRecord(live, emptyList(), "Ljava/lang/Object;"),
            ConsumerGraphAnalyzer.MethodRecord(instance, emptyList(), "Ljava/lang/Object;", cacheWrites = listOf(
                CacheWrite("Lflag;->a()J", "Lholder;->value:J", ConsumptionPath.INSTANCE_CACHE)
            )),
            ConsumerGraphAnalyzer.MethodRecord(static, emptyList(), "Ljava/lang/Object;", cacheWrites = listOf(
                CacheWrite("Lflag;->a()J", "Lconstants;->value:J", ConsumptionPath.STATIC_CACHE)
            ))
        )).single()
        assertEquals(ConsumptionPath.MIXED.name, enriched.metadata["consumptionPath"])
        assertEquals(ActivationScope.MIXED.name, enriched.metadata["activationScope"])
        assertTrue(enriched.metadata["activationScopes"].orEmpty().contains(ActivationScope.NEXT_INSTANCE.name))
        assertTrue(enriched.metadata["activationScopes"].orEmpty().contains(ActivationScope.NEXT_PROCESS.name))
    }

    @Test fun followsCachedFieldIntoStaticStateAndRuntimeLatch() {
        val getter = method("Lflag;", "a")
        val wrapper = method("Lwrapper;", "a", calls = listOf("Lflag;->a()Z"))
        val derived = method("Lderived;", "<init>", "()V", listOf("Lwrapper;->a()Z"))
        val startup = method("Lstartup;", "<clinit>", "()V")
        val start = method("Llifecycle;", "start", "()V")
        val identifier = DiscoveredIdentifier("Messaging__flag", IdentifierNamespace.FENOTYPE_FLAG, DiscoveredType.BOOLEAN, "false",
            emptyList(), listOf(getter), ResolutionConfidence.UNIQUE_GETTER, null, true)
        val enriched = ConsumerGraphAnalyzer.enrich(listOf(identifier), listOf(
            ConsumerGraphAnalyzer.MethodRecord(getter, emptyList(), "Ljava/lang/Object;"),
            ConsumerGraphAnalyzer.MethodRecord(wrapper, emptyList(), "Ljava/lang/Object;"),
            ConsumerGraphAnalyzer.MethodRecord(derived, emptyList(), "Ljava/lang/Object;", cacheWrites = listOf(
                CacheWrite("Lwrapper;->a()Z", "Lderived;->configured:Z", ConsumptionPath.INSTANCE_CACHE)
            )),
            ConsumerGraphAnalyzer.MethodRecord(startup, emptyList(), "Ljava/lang/Object;", fieldFlow = FieldFlowEvidence(
                reads = setOf("Lderived;->configured:Z"), propagations = listOf(
                    FieldPropagation("Lderived;->configured:Z", "Lstartup;->enabled:Z", ConsumptionPath.STATIC_STARTUP, "Lstartup;-><clinit>()V")
                )
            )),
            ConsumerGraphAnalyzer.MethodRecord(start, emptyList(), "Ljava/lang/Object;", fieldFlow = FieldFlowEvidence(
                propagations = listOf(FieldPropagation(
                    "Lstartup;->enabled:Z", "Llifecycle;->running:Z", ConsumptionPath.RUNTIME_LATCH,
                    "Llifecycle;->start()V", listOf("CONTROL_DEPENDENCY")
                ))
            ))
        )).single()
        assertTrue(enriched.metadata["consumptionPaths"].orEmpty().contains(ConsumptionPath.STATIC_STARTUP.name))
        assertTrue(enriched.metadata["consumptionPaths"].orEmpty().contains(ConsumptionPath.RUNTIME_LATCH.name))
        assertTrue(enriched.metadata["activationScopes"].orEmpty().contains(ActivationScope.NEXT_LIFECYCLE_START.name))
        assertEquals("Llifecycle;->running:Z", enriched.metadata["runtimeLatchTargets"])
    }
}
