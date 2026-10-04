package io.github.aaexperiments.resolver

import io.github.aaexperiments.discovery.*
import kotlin.math.abs

data class ScoredCandidate(val method: MethodFingerprint, val score: Int, val reasons: List<String>)
data class StructuralResolution(val selected: MethodFingerprint?, val candidates: List<ScoredCandidate>, val state: ResolutionConfidence, val reason: String)
data class BatchStructuralResolution(val byKey: Map<String, StructuralResolution>, val rejectedMethodReuse: Set<String>)

object StructuralResolver {
    private fun signature(method: MethodFingerprint) = "${method.dex}|${method.className}->${method.methodName}${method.descriptor}"

    fun resolve(previous: DiscoveredIdentifier?, current: DiscoveredIdentifier): StructuralResolution {
        val candidates = current.getterCandidates
        if (candidates.isEmpty()) return StructuralResolution(null, emptyList(), ResolutionConfidence.UNSUPPORTED, current.reason ?: "No getter candidates")
        if (previous == null || previous.getterCandidates.size != 1) {
            return if (candidates.size == 1) StructuralResolution(candidates.single(), listOf(ScoredCandidate(candidates.single(), 100, listOf("unique literal getter"))), ResolutionConfidence.UNIQUE_GETTER, "Unique getter in current APK")
            else StructuralResolution(null, candidates.map { ScoredCandidate(it, 0, listOf("no historical baseline")) }, ResolutionConfidence.AMBIGUOUS, "Multiple candidates and no unique baseline")
        }
        val baseline = previous.getterCandidates.single()
        val scored = candidates.map { score(baseline, it) }.sortedByDescending { it.score }
        val winner = scored.first()
        val margin = winner.score - (scored.getOrNull(1)?.score ?: 0)
        val safe = winner.score >= 65 && (scored.size == 1 || margin >= 15)
        return if (safe) StructuralResolution(winner.method, scored, ResolutionConfidence.STRUCTURALLY_VALIDATED, "Structural match score ${winner.score}, margin $margin")
        else StructuralResolution(null, scored, ResolutionConfidence.AMBIGUOUS, "No candidate crossed the confidence and uniqueness thresholds")
    }

    fun score(old: MethodFingerprint, new: MethodFingerprint): ScoredCandidate {
        var score = 0; val reasons = mutableListOf<String>()
        if (old.returnType == new.returnType) { score += 25; reasons += "same return type" }
        if (old.parameterTypes == new.parameterTypes) { score += 20; reasons += "same parameter types" }
        if (old.opcodeSha256 == new.opcodeSha256) { score += 35; reasons += "identical opcode sequence" }
        else {
            val delta = abs(old.instructionCount - new.instructionCount)
            when { delta == 0 -> { score += 20; reasons += "same instruction count" }; delta <= 2 -> { score += 12; reasons += "instruction count within 2" }; delta <= 8 -> { score += 5; reasons += "instruction count within 8" } }
        }
        val oldFieldShapes = old.referencedFields.map(::fieldShape).toSet(); val newFieldShapes = new.referencedFields.map(::fieldShape).toSet()
        if (oldFieldShapes.isNotEmpty() && oldFieldShapes == newFieldShapes) { score += 10; reasons += "same referenced field types" }
        val oldCallShapes = old.invokedMethods.map(::methodShape).toSet(); val newCallShapes = new.invokedMethods.map(::methodShape).toSet()
        if (oldCallShapes.isNotEmpty() && oldCallShapes == newCallShapes) { score += 10; reasons += "same invoked signatures" }
        else if (oldCallShapes.isNotEmpty() && newCallShapes.isNotEmpty()) {
            val similarity = jaccard(oldCallShapes, newCallShapes)
            if (similarity >= .5) { score += (similarity * 8).toInt(); reasons += "similar one-hop call subgraph" }
        }
        return ScoredCandidate(new, score.coerceAtMost(100), reasons)
    }

    /**
     * Conservative global pass. A method can be assigned to only one identifier and a match is
     * accepted only when it is locally strong and globally uncontested.
     */
    fun resolveBatch(previous: List<DiscoveredIdentifier>, current: List<DiscoveredIdentifier>): BatchStructuralResolution {
        val oldByKey = previous.associateBy { it.key }
        val preliminary = current.associate { identifier -> identifier.key to resolve(oldByKey[identifier.key], identifier) }.toMutableMap()
        val claims = preliminary.mapNotNull { (key, result) -> result.selected?.let { signature(it) to key } }.groupBy({ it.first }, { it.second })
        val collisions = claims.filterValues { it.size > 1 }.keys
        if (collisions.isNotEmpty()) preliminary.entries.forEach { entry ->
            val selected = entry.value.selected ?: return@forEach
            if (signature(selected) in collisions) entry.setValue(entry.value.copy(
                selected = null,
                state = ResolutionConfidence.AMBIGUOUS,
                reason = "Candidate rejected because another identifier claimed the same method in the global one-to-one pass."
            ))
        }
        return BatchStructuralResolution(preliminary, collisions)
    }

    private fun fieldShape(value: String) = value.substringAfter(':')
    private fun methodShape(value: String) = value.substringAfter("->").substringAfter('(').let { "($it" }
    private fun <T> jaccard(left: Set<T>, right: Set<T>): Double = (left intersect right).size.toDouble() / (left union right).size.coerceAtLeast(1)
}
