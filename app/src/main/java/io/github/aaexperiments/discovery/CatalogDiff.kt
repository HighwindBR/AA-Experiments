package io.github.aaexperiments.discovery

enum class ChangeKind { PRESERVED, ADDED, REMOVED, REMAPPED, TYPE_CHANGED, DEFAULT_CHANGED, MOVED_TO_REGISTRY, AMBIGUOUS }
data class IdentifierChange(val key: String, val kind: ChangeKind, val before: DiscoveredIdentifier?, val after: DiscoveredIdentifier?)

object CatalogDiff {
    fun compare(before: DexInventory, after: DexInventory): List<IdentifierChange> {
        val old = before.identifiers.associateBy { it.key }
        val new = after.identifiers.associateBy { it.key }
        return (old.keys + new.keys).sorted().map { key ->
            val a = old[key]; val b = new[key]
            val kind = when {
                a == null -> ChangeKind.ADDED
                b == null -> ChangeKind.REMOVED
                b.confidence == ResolutionConfidence.AMBIGUOUS -> ChangeKind.AMBIGUOUS
                a.inferredType != b.inferredType -> ChangeKind.TYPE_CHANGED
                a.compiledDefault != b.compiledDefault -> ChangeKind.DEFAULT_CHANGED
                a.getterCandidates.isNotEmpty() && b.getterCandidates.isEmpty() && b.occurrences.any { it.method.isStaticInitializer } -> ChangeKind.MOVED_TO_REGISTRY
                signatures(a) != signatures(b) -> ChangeKind.REMAPPED
                else -> ChangeKind.PRESERVED
            }
            IdentifierChange(key, kind, a, b)
        }
    }

    private fun signatures(value: DiscoveredIdentifier) = value.getterCandidates.map { "${it.className}->${it.methodName}${it.descriptor}" }.toSet()
}
