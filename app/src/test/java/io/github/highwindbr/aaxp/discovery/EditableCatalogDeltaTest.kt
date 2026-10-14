package io.github.highwindbr.aaxp.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditableCatalogDeltaTest {
    private fun state(
        key: String,
        type: DiscoveredType = DiscoveredType.BOOLEAN,
        default: String? = "false",
        confidence: ResolutionConfidence = ResolutionConfidence.UNIQUE_GETTER,
        resolution: MappingResolution = MappingResolution.DIRECT,
        consumer: RuntimeConsumerStatus = RuntimeConsumerStatus.PRESENT,
        reviewed: Boolean = false
    ) = EditableCatalogSnapshot(key, type, default, confidence, resolution, consumer, reviewed)

    @Test fun addedRemovedAndChangedArePerBuildCatalogDifferences() {
        val before = listOf(state("removed"), state("changed"), state("same"))
        val after = listOf(state("added"), state("changed", default = "true"), state("same"))
        val changes = compareEditableSnapshots(before, after)
        assertEquals(listOf("added", "changed", "removed"), changes.map { it.key })
        assertEquals(
            listOf(EditableDeltaKind.ADDED, EditableDeltaKind.CHANGED, EditableDeltaKind.REMOVED),
            changes.map { it.kind }
        )
        assertEquals("false", changes.single { it.key == "changed" }.before?.compiledDefault)
        assertEquals("true", changes.single { it.key == "changed" }.after?.compiledDefault)
        assertEquals(null, changes.single { it.key == "removed" }.after)
    }

    @Test fun allFivePublishedChangeFieldsTriggerChanged() {
        val old = state("test")
        val variants = listOf(
            old.copy(type = DiscoveredType.LONG),
            old.copy(compiledDefault = null),
            old.copy(confidence = ResolutionConfidence.STRUCTURALLY_VALIDATED),
            old.copy(resolution = MappingResolution.STRUCTURAL),
            old.copy(runtimeConsumerStatus = RuntimeConsumerStatus.DORMANT)
        )
        variants.forEach {
            assertEquals(EditableDeltaKind.CHANGED,
                compareEditableSnapshots(listOf(old), listOf(it)).single().kind)
        }
        assertTrue(compareEditableSnapshots(listOf(old), listOf(old.copy(semanticsReviewed = true))).isEmpty())
    }

    @Test fun noPriorCatalogIsNotReportedAsAllNew() {
        // Caller intentionally skips comparison when the previous build does not exist.
        val delta = compareEditableSnapshots(emptyList(), listOf(state("first")))
        assertEquals(EditableDeltaKind.ADDED, delta.single().kind)
    }
}
