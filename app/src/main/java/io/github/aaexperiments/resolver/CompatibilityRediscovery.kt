package io.github.aaexperiments.resolver

import io.github.aaexperiments.discovery.*

/**
 * Applies only same-key, one-to-one structural rediscovery. Disappeared keys are reported by the
 * catalog diff and never attached to an unrelated current method merely because it looks similar.
 */
object CompatibilityRediscovery {
    fun apply(
        previous: List<DiscoveredIdentifier>,
        current: List<DiscoveredIdentifier>,
        previousBaseSha256: String?
    ): List<DiscoveredIdentifier> {
        if (previous.isEmpty()) return current
        val oldByKey = previous.associateBy { it.key }
        // Unique literal getters do not need scoring. Restrict the global matcher to genuinely
        // ambiguous mappings so it does not allocate scores/results for every editable key.
        val ambiguous = current.filter { it.key in oldByKey && it.getterCandidates.size != 1 }
        val batch = StructuralResolver.resolveBatch(previous, ambiguous)
        return current.map { identifier ->
            val old = oldByKey[identifier.key] ?: return@map if (
                identifier.getterCandidates.isNotEmpty() && SemanticClassifier.isConfigurable(identifier.kind)
            ) identifier.withRediscovery("NEW_KEY", previousBaseSha256) else identifier
            val resolution = batch.byKey[identifier.key]
            val selected = resolution?.selected
            when {
                identifier.getterCandidates.size == 1 -> identifier.withRediscovery(
                    if (sameMethodShape(old.getterCandidates.singleOrNull(), identifier.getterCandidates.single())) "PRESERVED" else "REDISCOVERED_BY_LITERAL",
                    previousBaseSha256,
                    resolution?.reason
                )
                selected != null && old.editable && selected.returnType == old.inferredType && SafetyPolicy.blockedReason(identifier.key) == null -> identifier.copy(
                    getterCandidates = listOf(selected),
                    confidence = ResolutionConfidence.STRUCTURALLY_VALIDATED,
                    resolution = MappingResolution.STRUCTURAL,
                    editable = true,
                    editabilityReason = null,
                    reason = "Recovered from the previous build by a unique structural match.",
                    evidenceConfidence = identifier.evidenceConfidence.copy(
                        getter = EvidenceStrength.PROVEN,
                        evidence = (identifier.evidenceConfidence.evidence + "cross-build one-to-one structural rediscovery").distinct()
                    ),
                    metadata = identifier.metadata + rediscoveryMetadata("REDISCOVERED_STRUCTURAL", previousBaseSha256, resolution.reason)
                )
                else -> identifier.copy(
                    editable = false,
                    editabilityReason = EditabilityReason.MAPPING_NOT_PROVEN,
                    metadata = identifier.metadata + rediscoveryMetadata("SUSPENDED_AMBIGUOUS", previousBaseSha256, resolution?.reason)
                )
            }
        }
    }

    private fun DiscoveredIdentifier.withRediscovery(status: String, previousHash: String?, detail: String? = null) =
        copy(metadata = metadata + rediscoveryMetadata(status, previousHash, detail))

    private fun rediscoveryMetadata(status: String, previousHash: String?, detail: String?) = buildMap {
        put("rediscoveryStatus", status)
        previousHash?.let { put("rediscoveryPreviousBaseSha256", it) }
        detail?.let { put("rediscoveryDetail", it) }
    }

    private fun sameMethodShape(old: MethodFingerprint?, current: MethodFingerprint): Boolean = old != null &&
        old.returnType == current.returnType && old.parameterTypes == current.parameterTypes &&
        old.opcodeSha256 == current.opcodeSha256
}
