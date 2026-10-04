package io.github.aaexperiments.discovery

/** Builds a cost-bounded reverse call graph without imposing an arbitrary semantic depth. */
object ConsumerGraphAnalyzer {
    enum class ConsumerCategory {
        RUNTIME, RENDERER, ACTIVITY, CONFIG_SNAPSHOT,
        WRAPPER_ONLY, DUMP, LOGGING, DIAGNOSTIC, TELEMETRY, TEST, UNKNOWN
    }
    data class MethodRecord(
        val method: MethodFingerprint,
        val interfaces: List<String>,
        val superclass: String?,
        val category: ConsumerCategory = ConsumerCategory.UNKNOWN,
        val decisions: List<DecisionUse> = emptyList(),
        val cacheWrites: List<CacheWrite> = emptyList(),
        val fieldFlow: FieldFlowEvidence? = null
    )

    private fun signature(method: MethodFingerprint) = "${method.className}->${method.methodName}${method.descriptor}"
    private fun metadataSuffix(outcome: InvokeCallOutcome) = outcome.name.lowercase().split('_')
        .joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }

    fun enrich(
        identifiers: List<DiscoveredIdentifier>,
        records: List<MethodRecord>,
        baseSha256: String = "",
        maxDepth: Int = Int.MAX_VALUE,
        maxVisitedNodes: Int = DEFAULT_MAX_VISITED_NODES,
        directedRetryVisitedNodes: Int? = null,
        maxDirectedRetries: Int = Int.MAX_VALUE,
        shouldContinue: () -> Boolean = { true },
        onIdentifier: (DiscoveredIdentifier) -> Unit = {},
        retainResults: Boolean = true,
        targetedTraversal: Boolean = false
    ): List<DiscoveredIdentifier> {
        val reverseCalls = mutableMapOf<String, MutableList<MethodFingerprint>>()
        records.forEach { record -> record.method.invokedMethods.forEach { invoked ->
            reverseCalls.getOrPut(invoked) { mutableListOf() } += record.method
        } }
        val superclassByClass = records.asSequence().mapNotNull { record -> record.superclass?.let { record.method.className to it } }.distinct().toMap()
        // Keep category indexes sparse. Materializing one signature string for every method adds
        // tens of MiB on a full AA scan; RUNTIME/UNKNOWN are the overwhelmingly common defaults.
        val specialCategories = records.asSequence()
            .filter { it.category !in setOf(ConsumerCategory.RUNTIME, ConsumerCategory.UNKNOWN) }
            .associate { signature(it.method) to it.category }
        val exceptionalCategories = specialCategories.filterValues { it in TECHNICAL_CATEGORIES }
        // Build one reusable semantic corridor for the whole directed session. Starting at
        // concrete UI/config endpoints and walking down their callees marks only methods that
        // can actually participate in a path ending at such an endpoint. Alpha32 rediscovered
        // that information independently from every getter and commonly spent all 512 nodes in
        // shared infrastructure before reaching the useful branch.
        val semanticDistance = if (targetedTraversal) buildSemanticDistances(
            specialCategories.filterValues { it in SEMANTIC_ENDPOINT_CATEGORIES }.keys,
            records
        ) else emptyMap()
        val decisionsBySignature = records.asSequence().filter { it.decisions.isNotEmpty() }.associate { signature(it.method) to it.decisions }
        val cacheWritesBySource = records.asSequence().flatMap { record -> record.cacheWrites.asSequence() }
            .groupBy { it.sourceMethod }
        val fieldEdgesBySource = records.asSequence().flatMap { it.fieldFlow?.propagations.orEmpty().asSequence() }.groupBy { it.sourceField }
        val interfaceDispatch = mutableMapOf<String, MutableSet<String>>()
        records.forEach { record ->
            record.interfaces.forEach { iface ->
                interfaceDispatch.getOrPut(signature(record.method)) { linkedSetOf() }
                    .add("$iface->${record.method.methodName}${record.method.descriptor}")
            }
        }

        var directedRetries = 0
        val enriched = identifiers.asSequence().takeWhile { shouldContinue() }.map { identifier ->
            val getters = identifier.getterCandidates
            // Consumer analysis is intentionally a cold diagnostic for hookable entries only.
            // Catalog-only literals must not multiply the reverse-graph working set.
            val reviewed = ManualReferenceLayer.find(baseSha256, identifier.key)
            if (getters.isEmpty() || (!identifier.editable && reviewed == null)) return@map identifier.also(onIdentifier)
            val normalWouldExhaust = directedRetryVisitedNodes != null && directedRetryVisitedNodes > maxVisitedNodes &&
                wouldExhaust(getters, maxVisitedNodes, reverseCalls, interfaceDispatch, superclassByClass, maxDepth)
            val directedRetry = directedRetries < maxDirectedRetries && normalWouldExhaust
            if (directedRetry) directedRetries++
            val effectiveBudget = if (directedRetry) directedRetryVisitedNodes!! else maxVisitedNodes
            val queue = ArrayDeque<Triple<String, Int, List<String>>>()
            val seenSignatures = linkedSetOf<String>()
            val depthBySignature = mutableMapOf<String, Int>()
            val traversedCallEdges = linkedSetOf<Pair<String, String>>()
            val scheduledSignatures = linkedSetOf<String>()
            fun schedule(target: String, depth: Int, path: () -> List<String>): Boolean {
                // BFS needs only the first (shortest) path to a signature. Scheduling duplicate
                // interface/inheritance paths caused an exponential queue and device OOM.
                if (target in scheduledSignatures) return false
                if (scheduledSignatures.size >= effectiveBudget) return false
                scheduledSignatures.add(target)
                queue += Triple(target, depth, path())
                return true
            }
            getters.forEach { getter ->
                val concrete = signature(getter)
                schedule(concrete, 0) { listOf(concrete) }
                interfaceDispatch[concrete].orEmpty().forEach { dispatch ->
                    schedule(dispatch, 0) { listOf(concrete, dispatch) }
                }
            }
            val evidence = linkedMapOf<String, ConsumerEvidence>()
            var visitedNodes = 0
            var recursiveEdges = 0
            var prunedFanoutCount = 0
            var prunedTechnicalCount = 0
            var semanticCorridorScheduled = 0
            var fallbackScheduled = 0
            var semanticCorridorExhausted = false
            var fallbackBudgetExhausted = false
            var unprovenValueEdges = 0
            val unprovenOutcomeCounts = mutableMapOf<InvokeCallOutcome, Int>()
            val provenOutcomeCounts = mutableMapOf<InvokeCallOutcome, Int>()
            val unsupportedFlowOpcodes = linkedSetOf<String>()
            val classifiedUnprovenEdges = hashSetOf<String>()
            val classifiedProvenEdges = hashSetOf<String>()
            while (queue.isNotEmpty() && visitedNodes++ < effectiveBudget) {
                val (target, depth, path) = queue.removeFirst()
                if (!seenSignatures.add(target) || depth >= maxDepth) continue
                depthBySignature[target] = depth
                val callers = reverseCalls[target].orEmpty()
                fun visitCaller(caller: MethodFingerprint, expand: Boolean) {
                    val callerSignature = signature(caller)
                    val nextDepth = depth + 1
                    // The traversal budget is also a memory budget. Alpha28 used to retain
                    // evidence and edges for every caller even after the worklist was full,
                    // allowing a 256-node traversal to materialize thousands of objects.
                    if (expand && !schedule(callerSignature, nextDepth) { appendPath(path, callerSignature) }) return
                    val nextPath = appendPath(path, callerSignature)
                    if (expand) traversedCallEdges += target to callerSignature
                    if (callerSignature in path) recursiveEdges++
                    val key = "${caller.dex}|$callerSignature"
                    evidence.putIfAbsent(key, ConsumerEvidence(
                        method = caller,
                        depth = nextDepth,
                        linkKind = when {
                            path.size > 1 && depth == 0 -> ConsumerLinkKind.INTERFACE_DISPATCH
                            nextDepth == 1 -> ConsumerLinkKind.DIRECT_CALL
                            else -> ConsumerLinkKind.TRANSITIVE_CALL
                        },
                        path = nextPath,
                        semanticSummary = reviewed?.summary
                    ))
                    if (!expand) return
                    interfaceDispatch[callerSignature].orEmpty().forEach { dispatch ->
                        schedule(dispatch, nextDepth) { appendPath(nextPath, dispatch) }
                    }
                    // Include inherited declarations as dispatch targets when a wrapper is typed as its parent.
                    superclassByClass[caller.className]?.takeIf { it != "Ljava/lang/Object;" }?.let { parent ->
                        schedule("$parent->${caller.methodName}${caller.descriptor}", nextDepth) { nextPath }
                    }
                }
                if (!targetedTraversal) callers.forEach { visitCaller(it, true) } else {
                    fun proven(caller: MethodFingerprint): Boolean {
                        val outcomes = caller.invokedMethods.indices.asSequence()
                            .filter { caller.invokedMethods[it] == target }
                            .map { index -> caller.invokeOutcomeCodes?.getOrNull(index)?.let(InvokeCallOutcome::fromCode) ?: InvokeCallOutcome.UNKNOWN }
                            .toList()
                        val outcome = when {
                            outcomes.any { it.proven } -> outcomes.filter { it.proven }.minBy { it.code }
                            InvokeCallOutcome.CFG_ORIGIN_AMBIGUOUS in outcomes -> InvokeCallOutcome.CFG_ORIGIN_AMBIGUOUS
                            else -> outcomes.maxByOrNull { it.code } ?: InvokeCallOutcome.UNKNOWN
                        }
                        val found = outcome.proven
                        val edgeKey = "$target|${signature(caller)}"
                        if (found && classifiedProvenEdges.add(edgeKey)) {
                            provenOutcomeCounts[outcome] = provenOutcomeCounts.getOrDefault(outcome, 0) + 1
                        } else if (!found && classifiedUnprovenEdges.add(edgeKey)) {
                            unprovenValueEdges++
                            unprovenOutcomeCounts[outcome] = unprovenOutcomeCounts.getOrDefault(outcome, 0) + 1
                            if (outcome == InvokeCallOutcome.UNSUPPORTED_OPCODE) unsupportedFlowOpcodes += caller.unsupportedFlowOpcodes
                        }
                        return found
                    }
                    // Semantic endpoints are destinations, never bridges. Alpha32 scheduled them
                    // and then continued into their callers, consuming most of the budget in UI or
                    // service infrastructure after the useful answer had already been reached.
                    callers.forEach { caller ->
                        if (specialCategories[signature(caller)] in SEMANTIC_ENDPOINT_CATEGORIES && proven(caller)) visitCaller(caller, false)
                    }
                    // Follow callers known to lie on a route to a semantic endpoint before any
                    // fallback exploration. This is a graph summary shared by every identifier,
                    // not a hardcoded class or obfuscated name.
                    callers.asSequence()
                        .filter { caller ->
                            val callerSignature = signature(caller)
                            specialCategories[callerSignature] !in SEMANTIC_ENDPOINT_CATEGORIES &&
                                semanticDistance.containsKey(callerSignature) && proven(caller)
                        }
                        .sortedBy { semanticDistance[signature(it)] ?: Int.MAX_VALUE }
                        .forEach { caller ->
                            if (semanticCorridorScheduled++ < MAX_TARGETED_SEMANTIC_CORRIDOR) visitCaller(caller, true)
                            else semanticCorridorExhausted = true
                        }
                    var wrapperCount = 0
                    callers.forEach { caller ->
                        val callerSignature = signature(caller)
                        if (!semanticDistance.containsKey(callerSignature) && specialCategories[callerSignature] == ConsumerCategory.WRAPPER_ONLY && proven(caller)) {
                            if (wrapperCount++ < MAX_TARGETED_WRAPPER_FANOUT && fallbackScheduled < MAX_TARGETED_FALLBACK_NODES) {
                                fallbackScheduled++
                                visitCaller(caller, true)
                            } else {
                                prunedFanoutCount++
                                if (fallbackScheduled >= MAX_TARGETED_FALLBACK_NODES) fallbackBudgetExhausted = true
                            }
                        }
                    }
                    var genericCount = 0
                    callers.forEach { caller ->
                        val callerSignature = signature(caller)
                        val category = specialCategories[callerSignature]
                        if (!semanticDistance.containsKey(callerSignature) && (category == null || category in setOf(ConsumerCategory.RUNTIME, ConsumerCategory.UNKNOWN)) && proven(caller)) {
                            if (genericCount++ < MAX_TARGETED_GENERIC_FANOUT && fallbackScheduled < MAX_TARGETED_FALLBACK_NODES) {
                                fallbackScheduled++
                                visitCaller(caller, true)
                            } else {
                                prunedFanoutCount++
                                if (fallbackScheduled >= MAX_TARGETED_FALLBACK_NODES) fallbackBudgetExhausted = true
                            }
                        }
                    }
                    var technicalEvidence = 0
                    callers.forEach { caller ->
                        val category = specialCategories[signature(caller)]
                        if (category in TERMINAL_TECHNICAL_CATEGORIES) {
                            prunedTechnicalCount++
                            if (technicalEvidence++ < MAX_TARGETED_TECHNICAL_EVIDENCE) visitCaller(caller, false)
                        }
                    }
                }
            }
            // Keep semantic endpoints even when dozens of shallow shared callers were observed.
            // Alpha32 sorted only by depth, so a real renderer at depth 2 could be discarded by
            // the evidence cap behind unrelated depth-1 infrastructure. Twelve independently
            // classified paths are enough for diagnostics; retaining 24 paths for every editable
            // entry kept hundreds of thousands of path references alive during compatibility scans.
            val consumers = evidence.values.sortedWith(
                compareBy<ConsumerEvidence> {
                    if (specialCategories[signature(it.method)] in SEMANTIC_ENDPOINT_CATEGORIES) 0 else 1
                }.thenBy { it.depth }.thenBy { it.method.className }
            ).take(MAX_RETAINED_CONSUMERS)
            val runtimeConsumers = consumers.filter { specialCategories[signature(it.method)] !in TECHNICAL_CATEGORIES }
            val decisionUses = consumers.sumOf { evidenceItem ->
                val pathTarget = evidenceItem.path.getOrNull(evidenceItem.path.lastIndex - 1)
                decisionsBySignature[signature(evidenceItem.method)].orEmpty().count { it.invokedMethod == pathTarget }
            }
            val getterDispatchTargets = getters.flatMap { getter ->
                val concrete = signature(getter)
                listOf(concrete) + interfaceDispatch[concrete].orEmpty()
            }.distinct()
            // A cache often reads through one or more generated interface/static wrappers rather
            // than invoking the concrete literal-bearing getter. The already bounded traversal is
            // therefore the correct search space for cache writes as well.
            val cacheWrites = depthBySignature.asSequence().filter { it.value <= MAX_CACHE_SOURCE_DEPTH }
                .flatMap { (source, depth) -> cacheWritesBySource[source].orEmpty().asSequence()
                    .filter { write -> write.ownerMethod.isEmpty() ||
                        (source to write.ownerMethod) in traversedCallEdges ||
                        depthBySignature[write.ownerMethod] == depth + 1 } }
                .distinct().toList()
            val reachableFields = linkedSetOf<String>()
            val lineageEdges = mutableListOf<FieldPropagation>()
            val fieldQueue = ArrayDeque<String>()
            cacheWrites.asSequence().filter { !it.targetField.startsWith("METHOD:") }.map { it.targetField }
                .forEach { if (reachableFields.add(it)) fieldQueue += it }
            while (fieldQueue.isNotEmpty() && lineageEdges.size < MAX_FIELD_LINEAGE_EDGES) {
                val source = fieldQueue.removeFirst()
                fieldEdgesBySource[source].orEmpty().forEach { edge ->
                    if (lineageEdges.size >= MAX_FIELD_LINEAGE_EDGES) return@forEach
                    if (edge !in lineageEdges) lineageEdges += edge
                    if (reachableFields.add(edge.targetField)) fieldQueue += edge.targetField
                }
            }
            val latchWrites = lineageEdges.asSequence().filter { it.path == ConsumptionPath.RUNTIME_LATCH }
                .map { FieldConstantWrite(it.targetField, -1L, it.ownerMethod, false) }.distinct().toList()
            val directRuntimeCallers = getterDispatchTargets.flatMap { target -> reverseCalls[target].orEmpty() }
                .filter { caller -> caller.methodName !in setOf("<init>", "<clinit>") &&
                    cacheWrites.none { it.sourceMethod == signature(caller) } }
            val paths = buildSet {
                if (directRuntimeCallers.isNotEmpty()) add(ConsumptionPath.LIVE)
                addAll(cacheWrites.map { it.path })
                addAll(lineageEdges.map { it.path })
                if (evidence.values.any { it.method.methodName == "<clinit>" }) add(ConsumptionPath.STATIC_STARTUP)
                if (latchWrites.isNotEmpty()) add(ConsumptionPath.RUNTIME_LATCH)
            }
            val consumption = when {
                paths.isEmpty() -> ConsumptionPath.UNKNOWN
                paths.size > 1 -> ConsumptionPath.MIXED
                else -> paths.single()
            }
            val activationScopes = buildSet {
                if (ConsumptionPath.LIVE in paths) add(ActivationScope.LIVE)
                if (ConsumptionPath.INSTANCE_CACHE in paths) add(ActivationScope.NEXT_INSTANCE)
                if (ConsumptionPath.STATIC_CACHE in paths) { add(ActivationScope.NEXT_CLASS_LOAD); add(ActivationScope.NEXT_PROCESS) }
                if (ConsumptionPath.STATIC_STARTUP in paths) { add(ActivationScope.NEXT_CLASS_LOAD); add(ActivationScope.NEXT_PROCESS) }
                if (ConsumptionPath.INSTANCE_DERIVED_CACHE in paths) add(ActivationScope.NEXT_INSTANCE)
                if (ConsumptionPath.RUNTIME_LATCH in paths) add(ActivationScope.NEXT_LIFECYCLE_START)
            }
            val activation = when {
                activationScopes.isEmpty() -> ActivationScope.UNKNOWN
                activationScopes.size > 1 -> ActivationScope.MIXED
                else -> activationScopes.single()
            }
            val endpointCategories = runtimeConsumers.mapNotNull { specialCategories[signature(it.method)] }
                .filter { it in SEMANTIC_ENDPOINT_CATEGORIES }.distinct()
            val globalBudgetExhausted = queue.isNotEmpty() || scheduledSignatures.size >= effectiveBudget
            val valueFlowLimitation = unprovenOutcomeCounts.maxByOrNull { it.value }?.key?.name
            val targetedReason = when {
                !targetedTraversal -> null
                semanticCorridorExhausted || globalBudgetExhausted -> "SEMANTIC_CORRIDOR_EXHAUSTED"
                endpointCategories.size > 1 -> "MULTIPLE_SEMANTIC_ENDPOINTS"
                endpointCategories.size == 1 -> "SEMANTIC_ENDPOINT_FOUND"
                fallbackBudgetExhausted -> "FALLBACK_BUDGET_EXHAUSTED"
                unprovenValueEdges > 0 && runtimeConsumers.isEmpty() -> "VALUE_FLOW_NOT_PROVEN"
                prunedFanoutCount > 0 -> "SHARED_INFRASTRUCTURE_FANOUT"
                runtimeConsumers.isEmpty() && prunedTechnicalCount > 0 -> "ONLY_TECHNICAL_CONSUMERS"
                consumers.isEmpty() -> "NO_CONSUMER_FOUND"
                else -> "GENERIC_RUNTIME_PATH_ONLY"
            }
            identifier.copy(
                runtimeConsumerStatus = if (runtimeConsumers.isEmpty()) RuntimeConsumerStatus.DORMANT else RuntimeConsumerStatus.PRESENT,
                semanticsReviewed = reviewed != null,
                semanticsProvenance = if (reviewed != null) SemanticsProvenance.MANUAL_EXACT_BUILD else SemanticsProvenance.NONE,
                evidenceConfidence = identifier.evidenceConfidence.copy(
                    runtimeConsumer = if (consumers.isEmpty()) EvidenceStrength.LOW else EvidenceStrength.MEDIUM,
                    semantics = if (reviewed != null) EvidenceStrength.PROVEN else identifier.evidenceConfidence.semantics,
                    evidence = identifier.evidenceConfidence.evidence + buildList {
                        if (consumers.isNotEmpty()) add("reverse call graph consumer")
                        if (reviewed != null) add("exact-build manual semantics reference")
                    }
                ),
                consumers = consumers,
                metadata = identifier.metadata + buildMap {
                    put("consumerTraversal", "REVERSE_CALL_GRAPH_WORKLIST_V2"); put("consumerCount", consumers.size.toString())
                    val exhausted = semanticCorridorExhausted || globalBudgetExhausted
                    put("consumerVisitedNodes", visitedNodes.toString()); put("consumerBudget", effectiveBudget.toString())
                    put("consumerBudgetExhausted", exhausted.toString())
                    targetedReason?.let {
                        put("targetedResolveAttempted", "true")
                        put("targetedStrategyVersion", TARGETED_STRATEGY_VERSION)
                        put("targetedResolutionReason", it)
                        put("targetedFanoutPruned", prunedFanoutCount.toString())
                        put("targetedTechnicalPruned", prunedTechnicalCount.toString())
                        put("targetedSemanticCorridorScheduled", semanticCorridorScheduled.toString())
                        put("targetedFallbackScheduled", fallbackScheduled.toString())
                        put("targetedFallbackBudgetExhausted", fallbackBudgetExhausted.toString())
                        put("targetedUnprovenValueEdges", unprovenValueEdges.toString())
                        put("valueFlowLimitation", if (it == "VALUE_FLOW_NOT_PROVEN") valueFlowLimitation ?: "UNKNOWN" else "NONE")
                        InvokeCallOutcome.entries.filter { !it.proven }.forEach { outcome ->
                            put("unproven${metadataSuffix(outcome)}Count", unprovenOutcomeCounts.getOrDefault(outcome, 0).toString())
                        }
                        InvokeCallOutcome.entries.filter { it.proven }.forEach { outcome ->
                            put("proven${metadataSuffix(outcome)}Count", provenOutcomeCounts.getOrDefault(outcome, 0).toString())
                        }
                        if (unsupportedFlowOpcodes.isNotEmpty()) put("unsupportedFlowOpcodes", unsupportedFlowOpcodes.take(12).joinToString("|"))
                    }
                    if (directedRetry) {
                        put("deepResolveAttempted", "true")
                        put("deepResolveBudget", effectiveBudget.toString())
                        put("deepResolveStatus", if (exhausted) "STILL_INCONCLUSIVE" else "RESOLVED")
                    } else if (normalWouldExhaust) {
                        put("deepResolveAttempted", "false")
                        put("deepResolveStatus", "DEFERRED_BATCH_LIMIT")
                    }
                    put("recursiveConsumerCount", recursiveEdges.toString())
                    put("semanticConsumerCategories", runtimeConsumers.map { specialCategories[signature(it.method)] ?: ConsumerCategory.RUNTIME }.distinct().joinToString("|") { it.name })
                    put("semanticConsumerProven", runtimeConsumers.isNotEmpty().toString())
                    put("wrapperOnlyConsumerCount", consumers.count { exceptionalCategories[signature(it.method)] == ConsumerCategory.WRAPPER_ONLY }.toString())
                    put("dumpConsumerCount", consumers.count { exceptionalCategories[signature(it.method)] == ConsumerCategory.DUMP }.toString())
                    put("loggingConsumerCount", consumers.count { exceptionalCategories[signature(it.method)] == ConsumerCategory.LOGGING }.toString())
                    put("diagnosticConsumerCount", consumers.count { exceptionalCategories[signature(it.method)] == ConsumerCategory.DIAGNOSTIC }.toString())
                    put("telemetryConsumerCount", consumers.count { exceptionalCategories[signature(it.method)] == ConsumerCategory.TELEMETRY }.toString())
                    put("testConsumerCount", consumers.count { exceptionalCategories[signature(it.method)] == ConsumerCategory.TEST }.toString())
                    put("directDecisionUseCount", decisionUses.toString())
                    put("consumptionPath", consumption.name)
                    put("consumptionPaths", paths.joinToString("|") { it.name })
                    put("activationScope", activation.name)
                    put("activationScopes", activationScopes.joinToString("|") { it.name })
                    if (cacheWrites.isNotEmpty()) {
                        put("cacheTargets", cacheWrites.joinToString("|") { it.targetField })
                        val transformations = cacheWrites.flatMap { it.transformations }.distinct()
                        if (transformations.isNotEmpty()) put("cacheTransformations", transformations.joinToString("|"))
                    }
                    if (lineageEdges.isNotEmpty()) {
                        put("fieldLineage", lineageEdges.joinToString("|") { "${it.sourceField}->${it.targetField}" })
                        put("fieldLineageEdgeCount", lineageEdges.size.toString())
                        put("fieldLineageBudgetExhausted", (lineageEdges.size >= MAX_FIELD_LINEAGE_EDGES).toString())
                    }
                    if (latchWrites.isNotEmpty()) put("runtimeLatchTargets", latchWrites.joinToString("|") { it.targetField })
                    val finalReaders = lineageEdges.map { it.ownerMethod }.distinct().take(16)
                    if (finalReaders.isNotEmpty()) put("fieldConsumerMethods", finalReaders.joinToString("|"))
                    reviewed?.let {
                        put("reviewedSemantics", it.summary); put("reviewEvidence", it.evidence); put("reviewedForBaseSha256", baseSha256)
                        it.trueMeaning?.let { meaning -> put("trueMeaning", meaning) }
                        it.falseMeaning?.let { meaning -> put("falseMeaning", meaning) }
                    }
                }
            ).also(onIdentifier)
        }
        return if (retainResults) enriched.toList() else {
            // Directed sessions persist through onIdentifier. Consuming the lazy sequence without
            // collecting it keeps memory flat while one prepared graph serves the whole queue.
            enriched.forEach { }
            emptyList()
        }
    }

    const val DEFAULT_MAX_VISITED_NODES = 256
    private const val MAX_EVIDENCE_PATH = 16
    private const val MAX_RETAINED_CONSUMERS = 12
    private const val MAX_CACHE_SOURCE_DEPTH = 3
    private const val MAX_FIELD_LINEAGE_EDGES = 128
    private const val MAX_TARGETED_WRAPPER_FANOUT = 24
    private const val MAX_TARGETED_GENERIC_FANOUT = 32
    private const val MAX_TARGETED_TECHNICAL_EVIDENCE = 8
    private const val MAX_TARGETED_SEMANTIC_CORRIDOR = 384
    private const val MAX_TARGETED_FALLBACK_NODES = 96
    const val TARGETED_STRATEGY_VERSION = "ALPHA38_INSTANCE_RESULT_ORIGIN_V1"
    private val SEMANTIC_ENDPOINT_CATEGORIES = setOf(ConsumerCategory.RENDERER, ConsumerCategory.ACTIVITY, ConsumerCategory.CONFIG_SNAPSHOT)
    private val TERMINAL_TECHNICAL_CATEGORIES = setOf(
        ConsumerCategory.DUMP, ConsumerCategory.LOGGING, ConsumerCategory.DIAGNOSTIC,
        ConsumerCategory.TELEMETRY, ConsumerCategory.TEST
    )
    private val TECHNICAL_CATEGORIES = setOf(
        ConsumerCategory.WRAPPER_ONLY, ConsumerCategory.DUMP, ConsumerCategory.LOGGING,
        ConsumerCategory.DIAGNOSTIC, ConsumerCategory.TELEMETRY, ConsumerCategory.TEST
    )

    private fun buildSemanticDistances(
        endpoints: Set<String>,
        records: List<MethodRecord>
    ): Map<String, Int> {
        // Deliberately trade a few linear passes for a much smaller peak heap. Keeping another
        // full signature -> callees index beside reverseCalls exceeded the 256 MiB device budget.
        val distance = HashMap<String, Int>()
        var frontier = endpoints.toHashSet()
        endpoints.forEach { distance[it] = 0 }
        var depth = 0
        while (frontier.isNotEmpty() && depth++ < MAX_SEMANTIC_CORRIDOR_DEPTH) {
            val next = HashSet<String>()
            records.forEach { record ->
                if (signature(record.method) in frontier) {
                    record.method.invokedMethods.forEach { invoked ->
                        if (distance.putIfAbsent(invoked, depth) == null) next += invoked
                    }
                }
            }
            frontier = next
        }
        return distance
    }

    private const val MAX_SEMANTIC_CORRIDOR_DEPTH = 24

    /** Cheap bounded preflight that reuses the already-built graph and retains no evidence paths. */
    private fun wouldExhaust(
        getters: List<MethodFingerprint>,
        budget: Int,
        reverseCalls: Map<String, List<MethodFingerprint>>,
        interfaceDispatch: Map<String, Set<String>>,
        superclassByClass: Map<String, String>,
        maxDepth: Int
    ): Boolean {
        val queue = ArrayDeque<Pair<String, Int>>()
        val scheduled = linkedSetOf<String>()
        fun schedule(target: String, depth: Int) {
            if (scheduled.size < budget && scheduled.add(target)) queue += target to depth
        }
        getters.forEach { getter ->
            val concrete = signature(getter)
            schedule(concrete, 0)
            interfaceDispatch[concrete].orEmpty().forEach { schedule(it, 0) }
        }
        var visited = 0
        while (queue.isNotEmpty() && visited++ < budget) {
            val (target, depth) = queue.removeFirst()
            if (depth >= maxDepth) continue
            reverseCalls[target].orEmpty().forEach { caller ->
                val callerSignature = signature(caller)
                schedule(callerSignature, depth + 1)
                interfaceDispatch[callerSignature].orEmpty().forEach { schedule(it, depth + 1) }
                superclassByClass[caller.className]?.takeIf { it != "Ljava/lang/Object;" }?.let { parent ->
                    schedule("$parent->${caller.methodName}${caller.descriptor}", depth + 1)
                }
            }
        }
        return queue.isNotEmpty() || scheduled.size >= budget
    }

    private fun appendPath(path: List<String>, value: String): List<String> = when {
        path.size < MAX_EVIDENCE_PATH -> path + value
        else -> buildList(MAX_EVIDENCE_PATH) {
            add(path.first())
            addAll(path.takeLast(MAX_EVIDENCE_PATH - 2))
            add(value)
        }
    }
}
