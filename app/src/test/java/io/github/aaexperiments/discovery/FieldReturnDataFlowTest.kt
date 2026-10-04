package io.github.aaexperiments.discovery

import org.jf.dexlib2.Opcode
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction21c
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction11x
import org.jf.dexlib2.immutable.reference.ImmutableFieldReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FieldReturnDataFlowTest {
    @Test fun onlyFieldThatReachesReturnIsAccepted() {
        val returned = ImmutableFieldReference("Lflags;", "returned", "Z")
        val incidental = ImmutableFieldReference("Lflags;", "incidental", "Z")
        val result = FieldReturnDataFlow.trace(listOf(
            ImmutableInstruction21c(Opcode.SGET_BOOLEAN, 0, returned),
            ImmutableInstruction21c(Opcode.SGET_BOOLEAN, 1, incidental),
            ImmutableInstruction11x(Opcode.RETURN, 0)
        ))
        assertEquals(setOf("Lflags;->returned:Z"), result.fields)
        assertFalse(result.ambiguousReturn)
    }
}
