package io.github.highwindbr.aaxp.discovery

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

/**
 * Changes in the *editable* catalog, not reasons why an override was suspended.
 * A removed entry is no longer editable in the new build; it may still exist as read-only.
 */
enum class EditableDeltaKind { ADDED, REMOVED, CHANGED }

data class EditableCatalogSnapshot(
    val key: String,
    val type: DiscoveredType,
    val compiledDefault: String?,
    val confidence: ResolutionConfidence,
    val resolution: MappingResolution,
    val runtimeConsumerStatus: RuntimeConsumerStatus,
    val semanticsReviewed: Boolean = false
) {
    companion object {
        fun from(id: DiscoveredIdentifier) = EditableCatalogSnapshot(
            id.key, id.inferredType, id.compiledDefault, id.confidence,
            id.resolution, id.runtimeConsumerStatus, id.semanticsReviewed
        )
    }
}

data class EditableCatalogDelta(
    val key: String,
    val kind: EditableDeltaKind,
    val before: EditableCatalogSnapshot?,
    val after: EditableCatalogSnapshot?
)

/** Mirrors CatalogDatabase.editableChanges: only five fields qualify as CHANGED. */
fun compareEditableSnapshots(
    previous: List<EditableCatalogSnapshot>,
    current: List<EditableCatalogSnapshot>
): List<EditableCatalogDelta> {
    val beforeByKey = previous.associateBy { it.key }
    val afterByKey = current.associateBy { it.key }
    return (beforeByKey.keys + afterByKey.keys).sorted().mapNotNull { key ->
        val before = beforeByKey[key]
        val after = afterByKey[key]
        val kind = when {
            before == null && after != null -> EditableDeltaKind.ADDED
            after == null && before != null -> EditableDeltaKind.REMOVED
            before != null && after != null && (
                before.type != after.type ||
                before.compiledDefault != after.compiledDefault ||
                before.confidence != after.confidence ||
                before.resolution != after.resolution ||
                before.runtimeConsumerStatus != after.runtimeConsumerStatus
            ) -> EditableDeltaKind.CHANGED
            else -> null
        }
        kind?.let { EditableCatalogDelta(key, it, before, after) }
    }
}

fun compareEditableCatalogs(
    previous: List<DiscoveredIdentifier>,
    current: List<DiscoveredIdentifier>
): List<EditableCatalogDelta> = compareEditableSnapshots(
    previous.map(EditableCatalogSnapshot::from), current.map(EditableCatalogSnapshot::from)
)
