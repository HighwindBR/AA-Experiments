package io.github.aaexperiments.discovery

import org.jf.dexlib2.Opcode
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction3rc
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction11x
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction21t
import org.jf.dexlib2.immutable.reference.ImmutableMethodReference
import org.junit.Assert.assertEquals
import org.junit.Test

class DecisionRegionAnalyzerTest {
    @Test fun recordsMechanicalBooleanPolarityWithoutInventingSemantics() {
        val getter = ImmutableMethodReference("Lflag;", "enabled", emptyList(), "Z")
        val result = DecisionRegionAnalyzer.analyze(listOf(
            ImmutableInstruction3rc(Opcode.INVOKE_STATIC_RANGE, 0, 0, getter),
            ImmutableInstruction11x(Opcode.MOVE_RESULT, 2),
            ImmutableInstruction21t(Opcode.IF_NEZ, 2, 2)
        )).single()
        assertEquals("Lflag;->enabled()Z", result.invokedMethod)
        assertEquals(BranchPolarity.TRUE_ON_BRANCH, result.polarity)
    }
}
