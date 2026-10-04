package io.github.aaexperiments.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnalysisProgressTest {
    @Test fun `known work exposes a bounded real fraction`() {
        assertEquals(0.6f, AnalysisProgress(AnalysisStage.READING_DEX, 3, 5).fraction)
        assertEquals(1f, AnalysisProgress(AnalysisStage.SAVING_CATALOG, 20, 10).fraction)
    }

    @Test fun `unknown work stays indeterminate`() {
        assertNull(AnalysisProgress(AnalysisStage.RESOLVING_IDENTIFIERS).fraction)
        assertNull(AnalysisProgress(AnalysisStage.READING_DEX, 0, 0).fraction)
    }
}
