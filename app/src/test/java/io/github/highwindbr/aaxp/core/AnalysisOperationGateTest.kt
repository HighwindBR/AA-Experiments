package io.github.highwindbr.aaxp.core

import org.junit.Assert.*
import org.junit.Test

class AnalysisOperationGateTest {
    @Test fun aSecondAnalysisCannotStopOrReplaceTheOwner() {
        val gate = AnalysisOperationGate()
        assertTrue(gate.tryBegin())
        assertTrue(gate.isActive())
        assertFalse(gate.tryBegin())
        assertTrue(gate.isActive())
        gate.finish()
        assertFalse(gate.isActive())
        assertTrue(gate.tryBegin())
        gate.finish()
    }

    @Test(expected = IllegalStateException::class)
    fun unmatchedFinishIsRejected() {
        AnalysisOperationGate().finish()
    }
}
