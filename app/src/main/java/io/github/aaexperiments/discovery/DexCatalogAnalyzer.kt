package io.github.aaexperiments.discovery

import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import org.jf.dexlib2.iface.Method
import org.jf.dexlib2.iface.instruction.Instruction
import org.jf.dexlib2.iface.instruction.ReferenceInstruction
import org.jf.dexlib2.iface.instruction.OneRegisterInstruction
import org.jf.dexlib2.iface.instruction.TwoRegisterInstruction
import org.jf.dexlib2.iface.instruction.NarrowLiteralInstruction
import org.jf.dexlib2.iface.instruction.WideLiteralInstruction
import org.jf.dexlib2.iface.instruction.RegisterRangeInstruction
import org.jf.dexlib2.iface.instruction.FiveRegisterInstruction
import org.jf.dexlib2.iface.reference.FieldReference
import org.jf.dexlib2.iface.reference.MethodReference
import org.jf.dexlib2.iface.reference.StringReference
import java.io.BufferedInputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import io.github.aaexperiments.resolver.CompatibilityRediscovery
import io.github.aaexperiments.core.BuildFingerprint
import io.github.aaexperiments.core.PackageBuildIdentity

class DexCatalogAnalyzer {
    enum class RegistryResolutionMode { PROVEN_DATA_FLOW, LINEAR_DATA_FLOW_REFERENCE, LEGACY_PROXIMITY }
    enum class FieldReaderResolutionMode { PROVEN_RETURN_FLOW, REFERENCED_FIELD_LEGACY }
    private data class MutableIdentifier(
        val key: String,
        val namespace: IdentifierNamespace,
        val occurrences: MutableList<IdentifierOccurrence> = mutableListOf()
    )
    private data class MethodInspection(
        val fingerprint: MethodFingerprint,
        val category: ConsumerGraphAnalyzer.ConsumerCategory,
        val decisions: List<DecisionUse>,
        val cacheWrites: List<CacheWrite>,
        val fieldFlow: FieldFlowEvidence?
    )
    internal data class PositionalRegistration(val defaultValue: String?, val registryId: String?)

    fun analyze(baseApk: File): DexInventory = analyze(baseApk, emptyList())

    fun analyze(
        baseApk: File,
        splitApks: List<File>,
        mode: RegistryResolutionMode = RegistryResolutionMode.PROVEN_DATA_FLOW,
        fieldReaderMode: FieldReaderResolutionMode = FieldReaderResolutionMode.PROVEN_RETURN_FLOW,
        previousMappings: List<DiscoveredIdentifier> = emptyList(),
        previousBaseSha256: String? = null,
        buildIdentity: PackageBuildIdentity? = null,
        deepResolveKeys: Set<String> = emptySet(),
        streamDeepResolveResults: Boolean = false,
        shouldContinueDeepResolve: () -> Boolean = { true },
        onDeepResolveResult: (DiscoveredIdentifier, Int, Int) -> Unit = { _, _, _ -> },
        onProgress: (AnalysisProgress) -> Unit = {}
    ): DexInventory {
        val identity = buildIdentity ?: BuildFingerprint.compute(0, baseApk, splitApks)
        val baseHash = baseApk.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
        val identifiers = linkedMapOf<String, MutableIdentifier>()
        val fieldReturnEvidence = mutableListOf<Pair<MethodFingerprint, FieldReturnDataFlow.Result>>()
        val methodRecords = mutableListOf<ConsumerGraphAnalyzer.MethodRecord>()
        val compiledResources = mutableListOf<DiscoveredIdentifier>()
        val dexHashes = linkedMapOf<String, String>()
        val errors = mutableListOf<String>()
        val apkFiles = listOf(baseApk) + splitApks
        val totalDex = apkFiles.sumOf { apk -> ZipFile(apk).use { zip -> zip.entries().asSequence().count { it.name.matches(Regex("classes(\\d*)\\.dex")) } } }
        var dexIndex = 0
        onProgress(AnalysisProgress(AnalysisStage.READING_DEX, 0, totalDex))
        apkFiles.forEach { apk -> ZipFile(apk).use { zip ->
            zip.entries().asSequence().filter { it.name.matches(Regex("classes(\\d*)\\.dex")) }.forEach { entry ->
                dexIndex++
                onProgress(AnalysisProgress(AnalysisStage.READING_DEX, dexIndex, totalDex, if (apk == baseApk) entry.name else "${apk.name}!${entry.name}"))
                try {
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    val dexName = if (apk == baseApk) entry.name else "${apk.name}!${entry.name}"
                    dexHashes[dexName] = sha256(bytes)
                    val dex = DexBackedDexFile.fromInputStream(Opcodes.getDefault(), BufferedInputStream(bytes.inputStream()))
                    dex.classes.forEach { cls -> cls.methods.forEach { method ->
                        inspectMethod(dexName, method, identifiers, fieldReturnEvidence, mode,
                            deepResolveKeys.takeIf { it.isNotEmpty() })?.let { inspection ->
                            methodRecords += ConsumerGraphAnalyzer.MethodRecord(inspection.fingerprint, cls.interfaces.map { it.toString() }, cls.superclass,
                                inspection.category, inspection.decisions, inspection.cacheWrites, inspection.fieldFlow)
                        }
                    } }
                } catch (t: Throwable) {
                    errors += "${apk.name}!${entry.name}: ${t.javaClass.simpleName}: ${t.message}"
                }
            }
        } }
        onProgress(AnalysisProgress(AnalysisStage.RESOLVING_IDENTIFIERS, detail = "${identifiers.size} identifiers discovered"))
        val registeredFields = identifiers.values.asSequence().flatMap { item -> item.occurrences.asSequence().flatMap { it.associatedFields.asSequence() } }.toSet()
        val provenFieldReaders = mutableMapOf<String, MutableList<MethodFingerprint>>()
        fieldReturnEvidence.asSequence().filter { (fingerprint, _) -> fingerprint.referencedFields.any { it in registeredFields } }.forEach { (fingerprint, returned) ->
            val returnedFields = when (fieldReaderMode) {
                FieldReaderResolutionMode.PROVEN_RETURN_FLOW -> returned.takeUnless { it.ambiguousReturn }?.fields.orEmpty()
                FieldReaderResolutionMode.REFERENCED_FIELD_LEGACY -> fingerprint.referencedFields.toSet()
            }
            returnedFields.filter { it in registeredFields }.forEach { field ->
                provenFieldReaders.getOrPut(field) { mutableListOf() } += fingerprint
            }
        }
        fieldReturnEvidence.clear()
        val resolvedIdentifiers = identifiers.values.map { resolve(it, provenFieldReaders) }.toMutableList()
        identifiers.clear()
        onProgress(AnalysisProgress(AnalysisStage.REDISCOVERING_MAPPINGS, detail = if (previousMappings.isEmpty()) "No compatible previous catalog" else "Comparing ${previousMappings.size} previous mappings"))
        val rediscoveredIdentifiers = CompatibilityRediscovery.apply(previousMappings, resolvedIdentifiers, previousBaseSha256)
        // Compatibility rediscovery decorates the complete current catalog and therefore creates
        // a second generation of identifier objects. Once that generation exists, retaining the
        // undecorated 54k-item list through consumer classification needlessly overlaps two large
        // heaps. The empty-baseline path returns the original list and must not be cleared.
        if (previousMappings.isNotEmpty() && rediscoveredIdentifiers !== resolvedIdentifiers) {
            resolvedIdentifiers.clear()
        }
        // Structural rediscovery creates temporary candidate/matching tables. On a real
        // 256 MiB manager heap they must be reclaimed before the consumer graph begins;
        // otherwise two individually bounded phases overlap at their memory peaks.
        if (previousMappings.isNotEmpty()) System.gc()
        onProgress(AnalysisProgress(AnalysisStage.CLASSIFYING_EXPERIMENTS, detail = "Tracing getters and consumers"))
        val analysisInput = if (deepResolveKeys.isEmpty()) rediscoveredIdentifiers else rediscoveredIdentifiers.filter { it.key in deepResolveKeys }
        if (deepResolveKeys.isNotEmpty()) onProgress(AnalysisProgress(AnalysisStage.DEEP_RESOLVING, 0, analysisInput.size, "Directed reanalysis"))
        var deepCompleted = 0
        val consumerEnriched = ConsumerGraphAnalyzer.enrich(
            analysisInput,
            methodRecords,
            baseHash,
            maxVisitedNodes = if (deepResolveKeys.isEmpty()) ConsumerGraphAnalyzer.DEFAULT_MAX_VISITED_NODES else DEEP_RESOLVE_VISITED_NODES,
            shouldContinue = if (deepResolveKeys.isEmpty()) ({ true }) else ({
                shouldContinueDeepResolve() && (deepCompleted == 0 || hasDeepResolveHeadroom())
            }),
            onIdentifier = if (deepResolveKeys.isEmpty()) ({ _ -> }) else ({ raw ->
                val decorated = withDeepResolveResult(raw)
                val records = SemanticDependencyAnalyzer.selectRelevantRecords(listOf(decorated), methodRecords)
                val finalized = SemanticDependencyAnalyzer.enrich(listOf(decorated), records).single()
                deepCompleted++
                onDeepResolveResult(finalized, deepCompleted, analysisInput.size)
                onProgress(AnalysisProgress(AnalysisStage.DEEP_RESOLVING, deepCompleted, analysisInput.size, finalized.key))
            }),
            retainResults = !(deepResolveKeys.isNotEmpty() && streamDeepResolveResults),
            targetedTraversal = deepResolveKeys.isNotEmpty()
        ).map { identifier ->
            if (deepResolveKeys.isEmpty()) identifier else withDeepResolveResult(identifier)
        }
        resolvedIdentifiers.clear()
        if (deepResolveKeys.isNotEmpty()) onProgress(AnalysisProgress(AnalysisStage.DEEP_RESOLVING, deepCompleted, analysisInput.size, "Directed reanalysis complete"))
        val semanticRecords = SemanticDependencyAnalyzer.selectRelevantRecords(consumerEnriched, methodRecords)
        methodRecords.clear()
        val enrichedIdentifiers = SemanticDependencyAnalyzer.enrich(consumerEnriched, semanticRecords)

        // Resources do not participate in getter/consumer resolution. Loading them only after
        // the DEX graph is released keeps the scan below Android's 256 MiB process heap.
        if (deepResolveKeys.isEmpty()) {
            onProgress(AnalysisProgress(AnalysisStage.READING_RESOURCES, detail = "Reading packaged resources"))
            apkFiles.forEach { apk -> ZipFile(apk).use { zip ->
                zip.getEntry("resources.arsc")?.let { entry ->
                    runCatching { ArscCatalogParser(zip.getInputStream(entry).use { it.readBytes() }).parse() }
                        .onSuccess(compiledResources::addAll).onFailure { errors += "${apk.name}!resources.arsc: ${it.javaClass.simpleName}: ${it.message}" }
                }
                zip.entries().asSequence().filter { !it.isDirectory && it.name.startsWith("res/") }.forEach { entry ->
                    compiledResources += DiscoveredIdentifier(entry.name, IdentifierNamespace.RESOURCE_NAME, DiscoveredType.UNKNOWN, null,
                        emptyList(), emptyList(), ResolutionConfidence.CATALOGUED,
                        "Packaged Android resource; diagnostic only.", false)
                }
            } }
        }
        return DexInventory(
            baseSha256 = baseHash,
            dexSha256 = dexHashes,
            generatedAtEpochMs = System.currentTimeMillis(),
            identifiers = enrichedIdentifiers.plus(compiledResources).distinctBy { "${it.namespace}:${it.key}" }.sortedBy { it.key },
            errors = errors,
            buildFingerprintSha256 = identity.fingerprintSha256,
            packageVersionCode = identity.versionCode,
            packageArtifactSha256 = identity.artifactSha256
        )
    }

    private fun hasDeepResolveHeadroom(): Boolean {
        if (availableDeepResolveMemory() >= deepResolveReserve()) return true
        System.gc()
        return availableDeepResolveMemory() >= deepResolveReserve()
    }

    private fun availableDeepResolveMemory(): Long {
        val runtime = Runtime.getRuntime()
        val used = runtime.totalMemory() - runtime.freeMemory()
        return runtime.maxMemory() - used
    }

    private fun deepResolveReserve(): Long = maxOf(2L * 1024L * 1024L, Runtime.getRuntime().maxMemory() / 128L)

    private fun withDeepResolveResult(identifier: DiscoveredIdentifier): DiscoveredIdentifier {
        val reason = identifier.metadata["targetedResolutionReason"]
        val resolved = !identifier.metadata["consumerBudgetExhausted"].toBoolean() && reason == "SEMANTIC_ENDPOINT_FOUND"
        return identifier.copy(metadata = identifier.metadata + mapOf(
            "deepResolveAttempted" to "true",
            "deepResolveBudget" to DEEP_RESOLVE_VISITED_NODES.toString(),
            "deepResolveStatus" to if (resolved) "RESOLVED" else "STILL_INCONCLUSIVE"
        ))
    }

    private fun inspectMethod(
        dexName: String,
        method: Method,
        output: MutableMap<String, MutableIdentifier>,
        fieldReturnEvidence: MutableList<Pair<MethodFingerprint, FieldReturnDataFlow.Result>>,
        mode: RegistryResolutionMode,
        identifierFilter: Set<String>? = null
    ): MethodInspection? {
        val implementation = method.implementation ?: return null
        val instructions = implementation.instructions.toList()
        val fingerprint = fingerprint(dexName, method, instructions)
        if (!fingerprint.isStaticInitializer && fingerprint.parameterTypes.isEmpty() && fingerprint.returnType in EDITABLE_TYPES) {
            // Keep only compact proof. Retaining instruction objects until the end of the scan
            // exceeds the 256 MiB heap available to the manager process on real devices.
            fieldReturnEvidence += fingerprint to FieldReturnDataFlow.trace(instructions)
        }
        val registrations = if (method.name != "<clinit>") emptyMap() else when (mode) {
            RegistryResolutionMode.PROVEN_DATA_FLOW -> RegistrationDataFlow.traceDetailed(instructions)
            RegistryResolutionMode.LINEAR_DATA_FLOW_REFERENCE, RegistryResolutionMode.LEGACY_PROXIMITY ->
                RegistrationDataFlow.traceDetailedOps(RegistrationDataFlow.decodeAll(instructions))
        }
        val candidateDefault = inferReturnedConstant(instructions, method.returnType)
        val methodStrings = instructions.asSequence().mapNotNull { instruction ->
            (((instruction as? ReferenceInstruction)?.reference) as? StringReference)?.string
        }.toList()
        // Base64 probing is intentionally scoped to methods that also reference a protobuf-like
        // setting key. Trying every string in every DEX is both expensive and prone to false hits.
        val methodProtoDefaults = if (methodStrings.any(::looksLikeProtoSetting)) {
            methodStrings.asSequence()
                .filter { ProtobufInspector.inspectBase64(it)?.level == ProtobufResolutionLevel.PROTOBUF_WIRE_PARSED }
                .distinct()
                .toList()
        } else emptyList()
        var ordinal = 0
        instructions.forEachIndexed { index, instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference
            val literal = (reference as? StringReference)?.string ?: return@forEachIndexed
            if (identifierFilter != null && literal !in identifierFilter) return@forEachIndexed
            val namespace = classify(literal)
            if (namespace == IdentifierNamespace.STRING_LITERAL && !looksLikeTechnicalIdentifier(literal, fingerprint)) return@forEachIndexed
            val item = output.getOrPut(literal) { MutableIdentifier(literal, namespace) }
            val associatedFields = when (mode) {
                RegistryResolutionMode.PROVEN_DATA_FLOW, RegistryResolutionMode.LINEAR_DATA_FLOW_REFERENCE ->
                    registrations[literal]?.takeUnless { it.ambiguous }?.fields.orEmpty().toList()
                RegistryResolutionMode.LEGACY_PROXIMITY -> instructions.subList((index - 3).coerceAtLeast(0), (index + 13).coerceAtMost(instructions.size)).mapNotNull { nearby ->
                    ((nearby as? ReferenceInstruction)?.reference as? FieldReference)?.let { "${it.definingClass}->${it.name}:${it.type}" }
                }.distinct()
            }
            // Positional factory analysis is useful only for configuration namespaces. Running
            // it for every catalogued string creates tens of thousands of short-lived register
            // maps and exceeds the manager's 256 MiB process budget.
            val positional = if (namespace in POSITIONAL_REGISTRATION_NAMESPACES) {
                inferInvocationRegistration(instructions, index, literal)
            } else null
            val registeredDefault = registrations[literal]?.takeUnless { it.ambiguous }?.candidateDefaults?.let { selectRegistrationDefault(literal, it) }
            val localDefault = methodProtoDefaults.singleOrNull()?.takeIf { looksLikeProtoSetting(literal) }
                ?: positional?.defaultValue ?: candidateDefault ?: registeredDefault
                ?: if (namespace != IdentifierNamespace.STRING_LITERAL && fingerprint.returnType != DiscoveredType.STRING) inferNearbyRegistrationDefault(instructions, index) else null
            val role = SemanticClassifier.occurrenceRole(literal, namespace, registrations.containsKey(literal))
            val occurrenceKind = SemanticClassifier.kindOfOccurrence(literal, namespace, role)
            item.occurrences += IdentifierOccurrence(
                method = fingerprint,
                literalOrdinal = ordinal++,
                candidateDefault = localDefault,
                associatedFields = associatedFields,
                instructionOffset = index,
                role = role,
                kind = occurrenceKind,
                storageType = SemanticClassifier.storageTypeOf(fingerprint.returnType),
                semanticType = SemanticClassifier.semanticTypeOf(literal, fingerprint.returnType),
                classificationConfidence = when {
                    registrations.containsKey(literal) -> EvidenceStrength.HIGH
                    role != LiteralRole.UNKNOWN -> EvidenceStrength.MEDIUM
                    else -> EvidenceStrength.LOW
                },
                registryId = positional?.registryId,
                defaultSource = when {
                    methodProtoDefaults.singleOrNull()?.takeIf { looksLikeProtoSetting(literal) } != null -> "PROTOBUF_LITERAL"
                    positional?.defaultValue != null -> "INVOKE_ARGUMENT"
                    candidateDefault != null -> "DIRECT_RETURN"
                    registeredDefault != null -> "REGISTRY_FLOW"
                    else -> null
                }
            )
        }
        val category = when {
            method.definingClass.contains("test", true) || method.name.startsWith("test", true) -> ConsumerGraphAnalyzer.ConsumerCategory.TEST
            method.name in setOf("dump", "dumpDebug") -> ConsumerGraphAnalyzer.ConsumerCategory.DUMP
            method.name == "toString" -> ConsumerGraphAnalyzer.ConsumerCategory.DIAGNOSTIC
            fingerprint.invokedMethods.any { it.contains("Landroid/util/Log;") } -> ConsumerGraphAnalyzer.ConsumerCategory.LOGGING
            fingerprint.invokedMethods.any { it.contains("Telemetry", true) } -> ConsumerGraphAnalyzer.ConsumerCategory.TELEMETRY
            method.definingClass.contains("Activity", true) -> ConsumerGraphAnalyzer.ConsumerCategory.ACTIVITY
            method.definingClass.contains("View", true) || method.definingClass.contains("Renderer", true) || method.definingClass.contains("Template", true) -> ConsumerGraphAnalyzer.ConsumerCategory.RENDERER
            fingerprint.invokedMethods.any { it.contains("toByteArray", true) || it.contains("serialize", true) } -> ConsumerGraphAnalyzer.ConsumerCategory.CONFIG_SNAPSHOT
            instructions.size <= 8 && fingerprint.invokedMethods.size <= 1 && fingerprint.referencedFields.isEmpty() -> ConsumerGraphAnalyzer.ConsumerCategory.WRAPPER_ONLY
            else -> ConsumerGraphAnalyzer.ConsumerCategory.RUNTIME
        }
        val ownerMethod = "${method.definingClass}->${method.name}(${method.parameterTypes.joinToString("")})${method.returnType}"
        val hasFieldRead = instructions.any { instruction ->
            instruction.opcode.name.startsWith("sget", true) || instruction.opcode.name.startsWith("iget", true)
        }
        val hasFieldWrite = instructions.any { instruction ->
            instruction.opcode.name.startsWith("sput", true) || instruction.opcode.name.startsWith("iput", true)
        }
        val fieldFlow = if (hasFieldRead && hasFieldWrite && instructions.size <= MAX_LINEAGE_METHOD_INSTRUCTIONS) {
            FieldLineageAnalyzer.analyze(ownerMethod, method.name, instructions).let { flow ->
                val booleanEdges = flow.propagations.filter { edge ->
                    edge.sourceField.endsWith(":Z") && edge.targetField.endsWith(":Z")
                }
                // Read-only field sets duplicate MethodFingerprint.referencedFields across tens of
                // thousands of methods. This layer models boolean feature gates/latches; numeric
                // aggregation remains with the existing direct cache analyzer.
                if (booleanEdges.isEmpty()) null
                else flow.copy(reads = emptySet(), propagations = booleanEdges, constantWrites = emptyList())
            }
        } else null
        val flowFingerprint = if (identifierFilter != null && fingerprint.invokedMethods.isNotEmpty()) {
            val summary = InvokeResultFlowAnalyzer.analyze(instructions)
            fingerprint.copy(
                invokeOutcomeCodes = ByteArray(fingerprint.invokedMethods.size) { targetIndex ->
                    val target = fingerprint.invokedMethods[targetIndex]
                    val outcomes = summary.callsites.indices.asSequence()
                        .filter { summary.callsites[it] == target }
                        .map { summary.outcomes.getOrElse(it) { InvokeCallOutcome.UNKNOWN } }
                        .toList()
                    when {
                        outcomes.any { it.proven } -> outcomes.filter { it.proven }.minBy { it.code }
                        InvokeCallOutcome.CFG_ORIGIN_AMBIGUOUS in outcomes -> InvokeCallOutcome.CFG_ORIGIN_AMBIGUOUS
                        else -> outcomes.maxByOrNull { it.code } ?: InvokeCallOutcome.UNKNOWN
                    }.code
                },
                unsupportedFlowOpcodes = summary.unsupportedOpcodes.toList().sorted()
            )
        } else fingerprint
        return MethodInspection(flowFingerprint, category, DecisionRegionAnalyzer.analyze(instructions),
            CacheFlowAnalyzer.analyze(ownerMethod, method.name, instructions), fieldFlow)
    }

    private fun fingerprint(dexName: String, method: Method, instructions: List<Instruction>): MethodFingerprint {
        val fields = linkedSetOf<String>()
        val calls = linkedSetOf<String>()
        val opcodes = StringBuilder()
        instructions.forEach { instruction ->
            opcodes.append(instruction.opcode.name).append(';')
            when (val ref = (instruction as? ReferenceInstruction)?.reference) {
                is FieldReference -> fields += "${ref.definingClass}->${ref.name}:${ref.type}"
                is MethodReference -> calls += "${ref.definingClass}->${ref.name}(${ref.parameterTypes.joinToString("")})${ref.returnType}"
            }
        }
        val descriptor = "(${method.parameterTypes.joinToString("")})${method.returnType}"
        return MethodFingerprint(
            dexName, method.definingClass, method.name, descriptor, typeOf(method.returnType),
            method.parameterTypes.map { it.toString() }, instructions.size, sha256(opcodes.toString().toByteArray()),
            method.name == "<clinit>", fields.toList(), calls.toList()
        )
    }

    private fun resolve(item: MutableIdentifier, fieldReaders: Map<String, List<MethodFingerprint>>): DiscoveredIdentifier {
        val methods = item.occurrences.map { it.method }.distinctBy { "${it.className}->${it.methodName}${it.descriptor}" }
        val positionalMethods = item.occurrences.asSequence().filter { it.defaultSource == "INVOKE_ARGUMENT" }.map { it.method }
            .distinctBy { "${it.className}->${it.methodName}${it.descriptor}" }.toList()
        fun compatibleGetters(candidates: List<MethodFingerprint>) = candidates.filter {
            !it.isStaticInitializer && it.parameterTypes.isEmpty() && it.returnType in EDITABLE_TYPES && it.methodName !in DIAGNOSTIC_METHOD_NAMES
        }
        val directGetters = compatibleGetters(positionalMethods).ifEmpty { compatibleGetters(methods) }
        val associatedFields = item.occurrences.filter { it.method.isStaticInitializer }.flatMap { it.associatedFields }.toSet()
        val registryGetters = if (directGetters.isEmpty() && associatedFields.isNotEmpty()) associatedFields.flatMap { fieldReaders[it].orEmpty() }
            .distinctBy { "${it.className}->${it.methodName}${it.descriptor}" } else emptyList()
        val getters = directGetters.ifEmpty { registryGetters }
        val inferred = getters.map { it.returnType }.distinct().singleOrNull() ?: DiscoveredType.UNKNOWN
        val confidence = when {
            getters.size == 1 && directGetters.isEmpty() -> ResolutionConfidence.STRUCTURALLY_VALIDATED
            getters.size == 1 -> ResolutionConfidence.UNIQUE_GETTER
            getters.size > 1 -> ResolutionConfidence.AMBIGUOUS
            methods.any { it.isStaticInitializer } -> ResolutionConfidence.CATALOGUED
            else -> ResolutionConfidence.UNSUPPORTED
        }
        val safetyReason = SafetyPolicy.blockedReason(item.key)
        val reason = when {
            item.namespace == IdentifierNamespace.RESOURCE_NAME -> "Compiled resource catalogued by name; no universal API 101 resource hook exists."
            safetyReason != null -> safetyReason
            confidence == ResolutionConfidence.UNIQUE_GETTER -> null
            confidence == ResolutionConfidence.STRUCTURALLY_VALIDATED -> "Getter linked through proven register flow from registration literal to registry field."
            confidence == ResolutionConfidence.AMBIGUOUS -> "Multiple compatible getter candidates reference this identifier."
            confidence == ResolutionConfidence.CATALOGUED -> "Identifier is registered in a static initializer; no individual getter was proven."
            else -> "No supported zero-argument getter was located."
        }
        val getterRegistrations = item.occurrences.filter { occurrence ->
            occurrence.method in directGetters && occurrence.defaultSource == "INVOKE_ARGUMENT"
        }
        val defaultOccurrences = getterRegistrations.ifEmpty { item.occurrences }
        val defaults = defaultOccurrences.mapNotNull { normalizeDefault(it.candidateDefault, inferred, item.key) }.distinct()
        val configurableNamespace = item.namespace !in setOf(IdentifierNamespace.STRING_LITERAL, IdentifierNamespace.RESOURCE_NAME, IdentifierNamespace.MANIFEST_COMPONENT, IdentifierNamespace.NATIVE_REFERENCE)
        val metadata = buildMap {
            if (safetyReason != null) put("risk", "BLOCKED_BY_SAFETY_POLICY")
            if (associatedFields.isNotEmpty()) {
                put("registrationEvidence", "REGISTER_DATA_FLOW")
                put("fieldReaderEvidence", "FIELD_TO_RETURN_DATA_FLOW")
                put("registryFields", associatedFields.sorted().joinToString("|"))
            }
            val registrationEvidence = getterRegistrations.ifEmpty { item.occurrences }
            registrationEvidence.mapNotNull { it.registryId }.distinct().singleOrNull()?.let { put("registryId", it) }
            registrationEvidence.mapNotNull { it.defaultSource }.distinct().singleOrNull()?.let { put("defaultSource", it) }
        }
        val stringArgumentRolesProven = directGetters.isNotEmpty() || inferred != DiscoveredType.STRING
        val raw = DiscoveredIdentifier(item.key, item.namespace, inferred, defaults.singleOrNull(), item.occurrences.toList(), getters, confidence,
            if (!stringArgumentRolesProven) "String registration was resolved to a field, but key/default argument roles are not yet proven." else reason,
            safetyReason == null && configurableNamespace && confidence in setOf(ResolutionConfidence.UNIQUE_GETTER, ResolutionConfidence.STRUCTURALLY_VALIDATED) && inferred in EDITABLE_TYPES && stringArgumentRolesProven,
            metadata)
        return SemanticClassifier.normalize(raw)
    }

    private fun inferReturnedConstant(instructions: List<Instruction>, returnDescriptor: String): String? {
        val registers = mutableMapOf<Int, Any>()
        for (instruction in instructions) {
            val one = instruction as? OneRegisterInstruction
            when {
                instruction is NarrowLiteralInstruction && one != null -> registers[one.registerA] = instruction.narrowLiteral
                instruction is WideLiteralInstruction && one != null -> registers[one.registerA] = instruction.wideLiteral
                instruction is ReferenceInstruction && instruction.reference is StringReference && one != null -> registers[one.registerA] = (instruction.reference as StringReference).string
                instruction is TwoRegisterInstruction && instruction.opcode.name.startsWith("MOVE") -> registers[instruction.registerA] = registers[instruction.registerB] ?: continue
            }
            if (instruction.opcode.name.startsWith("RETURN") && one != null) {
                val value = registers[one.registerA] ?: return null
                return when (returnDescriptor) {
                    "Z" -> when ((value as? Number)?.toInt()) { 0 -> "false"; 1 -> "true"; else -> null }
                    "B", "S", "C", "I", "J" -> (value as? Number)?.toLong()?.toString()
                    "F" -> (value as? Number)?.toInt()?.let { Float.fromBits(it).toString() }
                    "D" -> (value as? Number)?.toLong()?.let { Double.fromBits(it).toString() }
                    "Ljava/lang/String;" -> value as? String
                    else -> null
                }
            }
        }
        return null
    }

    private fun inferNearbyRegistrationDefault(instructions: List<Instruction>, literalIndex: Int): String? {
        for (index in literalIndex - 1 downTo (literalIndex - 8).coerceAtLeast(0)) {
            val instruction = instructions[index]
            if (instruction is NarrowLiteralInstruction) return instruction.narrowLiteral.toString()
            if (instruction is WideLiteralInstruction) return instruction.wideLiteral.toString()
        }
        for (instruction in instructions.subList((literalIndex + 1).coerceAtMost(instructions.size), (literalIndex + 14).coerceAtMost(instructions.size))) {
            if (instruction.opcode.name.startsWith("INVOKE")) break
            if (instruction is NarrowLiteralInstruction) return instruction.narrowLiteral.toString()
            if (instruction is WideLiteralInstruction) return instruction.wideLiteral.toString()
        }
        return null
    }

    internal fun inferInvocationRegistration(instructions: List<Instruction>, literalIndex: Int, key: String): PositionalRegistration? {
        data class Value(val raw: String)
        val registers = mutableMapOf<Int, Value>()
        val start = (literalIndex - 16).coerceAtLeast(0)
        val end = (literalIndex + 24).coerceAtMost(instructions.lastIndex)
        for (index in start..end) {
            val instruction = instructions[index]
            val opcode = instruction.opcode.name.uppercase().replace('-', '_')
            val one = instruction as? OneRegisterInstruction
            when {
                instruction is ReferenceInstruction && instruction.reference is StringReference && one != null && opcode.startsWith("CONST_STRING") -> registers[one.registerA] = Value((instruction.reference as StringReference).string)
                instruction is WideLiteralInstruction && one != null && opcode.startsWith("CONST_WIDE") -> registers[one.registerA] = Value(instruction.wideLiteral.toString())
                instruction is NarrowLiteralInstruction && one != null && opcode.startsWith("CONST") -> registers[one.registerA] = Value(instruction.narrowLiteral.toString())
                instruction is TwoRegisterInstruction && opcode.startsWith("MOVE") -> registers[instruction.registerA] = registers[instruction.registerB] ?: continue
            }
            if (index < literalIndex || !opcode.startsWith("INVOKE")) continue
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference ?: continue
            val words = invokeRegisters(instruction)
            var wordIndex = if (opcode.startsWith("INVOKE_STATIC")) 0 else 1
            val arguments = reference.parameterTypes.map { descriptor ->
                val register = words.getOrNull(wordIndex)
                wordIndex += if (descriptor == "J" || descriptor == "D") 2 else 1
                register?.let(registers::get)
            }
            val keyIndex = arguments.indexOfFirst { it?.raw == key }
            if (keyIndex < 0) continue
            val defaultValue = arguments.getOrNull(keyIndex + 1)?.raw
            val registryId = (keyIndex - 1 downTo 0).firstNotNullOfOrNull { candidate ->
                if (reference.parameterTypes.getOrNull(candidate) in setOf("I", "J")) arguments.getOrNull(candidate)?.raw else null
            }
            return PositionalRegistration(defaultValue, registryId)
        }
        return null
    }

    private fun invokeRegisters(instruction: Instruction): List<Int> = when (instruction) {
        is RegisterRangeInstruction -> (instruction.startRegister until instruction.startRegister + instruction.registerCount).toList()
        is FiveRegisterInstruction -> listOf(instruction.registerC, instruction.registerD, instruction.registerE, instruction.registerF, instruction.registerG).take(instruction.registerCount)
        else -> emptyList()
    }

    private fun normalizeDefault(raw: String?, type: DiscoveredType, key: String): String? = when (type) {
        DiscoveredType.BOOLEAN -> when (raw) { "0" -> "false"; "1" -> "true"; "false", "true" -> raw; else -> null }
        DiscoveredType.INT, DiscoveredType.LONG, DiscoveredType.FLOAT, DiscoveredType.DOUBLE, DiscoveredType.STRING, DiscoveredType.ENUM -> raw
        else -> raw?.takeIf { looksLikeProtoSetting(key) && ProtobufInspector.inspectBase64(it) != null }
    }

    private fun selectRegistrationDefault(key: String, candidates: Set<String>): String? {
        if (looksLikeProtoSetting(key)) candidates.singleOrNull { ProtobufInspector.inspectBase64(it) != null }?.let { return it }
        return candidates.singleOrNull()
    }

    private fun looksLikeProtoSetting(key: String) =
        key.contains("config", true) || key.contains("widgets", true) || key.contains("manufacturer_", true) || key.contains("protobuf", true)

    internal fun classify(value: String): IdentifierNamespace = when {
        "__" in value -> IdentifierNamespace.FENOTYPE_FLAG
        value.startsWith("GH_") || value.endsWith("_ENABLED") -> IdentifierNamespace.DECISION_GATE
        value.startsWith("key_") || value.startsWith("pref_") || value.contains("_settings_") -> IdentifierNamespace.PREFERENCE_KEY
        value.startsWith("res/") || value.matches(Regex("(bool|string|integer|drawable|layout|dimen)/.+")) -> IdentifierNamespace.RESOURCE_NAME
        value.contains("protobuf", true) || value.split('_', '.', '-').any { it.equals("proto", true) } -> IdentifierNamespace.PROTOBUF_CONFIG
        else -> IdentifierNamespace.STRING_LITERAL
    }

    private fun looksLikeTechnicalIdentifier(value: String, method: MethodFingerprint): Boolean {
        if (value.length !in 3..240 || value.any { it == '\n' || it == '\r' }) return false
        if (method.isStaticInitializer && value.matches(Regex("[A-Za-z][A-Za-z0-9_.:/-]+"))) return true
        if (method.returnType in EDITABLE_TYPES && method.parameterTypes.isEmpty() && value.matches(Regex("[A-Za-z][A-Za-z0-9_.:/-]+"))) return true
        return false
    }

    private fun typeOf(descriptor: String): DiscoveredType = when (descriptor) {
        "Z" -> DiscoveredType.BOOLEAN; "B", "S", "C", "I" -> DiscoveredType.INT
        "J" -> DiscoveredType.LONG; "F" -> DiscoveredType.FLOAT; "D" -> DiscoveredType.DOUBLE; "Ljava/lang/String;" -> DiscoveredType.STRING
        "[B" -> DiscoveredType.BYTES; "V" -> DiscoveredType.VOID; else -> if (descriptor.startsWith("L") || descriptor.startsWith("[")) DiscoveredType.OBJECT else DiscoveredType.UNKNOWN
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        private const val DEEP_RESOLVE_VISITED_NODES = 512
        private const val MAX_LINEAGE_METHOD_INSTRUCTIONS = 16
        private val DIAGNOSTIC_METHOD_NAMES = setOf("toString", "dump", "dumpDebug")
        private val POSITIONAL_REGISTRATION_NAMESPACES = setOf(
            IdentifierNamespace.FENOTYPE_FLAG, IdentifierNamespace.PREFERENCE_KEY,
            IdentifierNamespace.PROTOBUF_CONFIG, IdentifierNamespace.DECISION_GATE
        )
        val EDITABLE_TYPES = setOf(DiscoveredType.BOOLEAN, DiscoveredType.INT, DiscoveredType.LONG, DiscoveredType.FLOAT, DiscoveredType.DOUBLE, DiscoveredType.STRING)
    }
}
