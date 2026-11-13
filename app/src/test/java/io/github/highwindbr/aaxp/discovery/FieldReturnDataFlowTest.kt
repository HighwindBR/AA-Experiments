package io.github.highwindbr.aaxp.discovery

import org.jf.dexlib2.Opcode
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction21c
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction11x
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction11n
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction12x
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction3rc
import org.jf.dexlib2.immutable.reference.ImmutableFieldReference
import org.jf.dexlib2.immutable.reference.ImmutableMethodReference
import org.jf.dexlib2.immutable.reference.ImmutableTypeReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    @Test fun constNullDoesNotPoisonBooleanWrapperGetter() {
        // Android Auto 18.2: sget-object flag -> config.l(null) -> Boolean.booleanValue().
        val field = ImmutableFieldReference("Lfeature;", "a", "Lxfp;")
        val read = ImmutableMethodReference("Lxfp;", "l", listOf("Ljava/lang/Object;"), "Ljava/lang/Object;")
        val booleanValue = ImmutableMethodReference("Ljava/lang/Boolean;", "booleanValue", emptyList(), "Z")
        val result = FieldReturnDataFlow.trace(listOf(
            ImmutableInstruction21c(Opcode.SGET_OBJECT, 0, field),
            ImmutableInstruction11n(Opcode.CONST_4, 1, 0),
            ImmutableInstruction3rc(Opcode.INVOKE_VIRTUAL_RANGE, 0, 2, read),
            ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0),
            ImmutableInstruction21c(Opcode.CHECK_CAST, 0, ImmutableTypeReference("Ljava/lang/Boolean;")),
            ImmutableInstruction3rc(Opcode.INVOKE_VIRTUAL_RANGE, 0, 1, booleanValue),
            ImmutableInstruction11x(Opcode.MOVE_RESULT, 0),
            ImmutableInstruction11x(Opcode.RETURN, 0)
        ))
        assertEquals(setOf("Lfeature;->a:Lxfp;"), result.fields)
        assertFalse(result.ambiguousReturn)
    }

    @Test fun constantOverwriteClearsPreviousFieldProvenance() {
        val field = ImmutableFieldReference("Lflags;", "old", "Z")
        val result = FieldReturnDataFlow.trace(listOf(
            ImmutableInstruction21c(Opcode.SGET_BOOLEAN, 0, field),
            ImmutableInstruction11n(Opcode.CONST_4, 0, 0),
            ImmutableInstruction11x(Opcode.RETURN, 0)
        ))
        assertEquals(emptySet<String>(), result.fields)
        assertFalse(result.ambiguousReturn)
    }

    @Test fun uninitializedInvocationArgumentIsStillAmbiguous() {
        val field = ImmutableFieldReference("Lfeature;", "a", "Lxfp;")
        val read = ImmutableMethodReference("Lxfp;", "l", listOf("Ljava/lang/Object;"), "Z")
        val result = FieldReturnDataFlow.trace(listOf(
            ImmutableInstruction21c(Opcode.SGET_OBJECT, 0, field),
            ImmutableInstruction12x(Opcode.MOVE_OBJECT, 1, 2),
            ImmutableInstruction3rc(Opcode.INVOKE_VIRTUAL_RANGE, 0, 2, read),
            ImmutableInstruction11x(Opcode.MOVE_RESULT, 0),
            ImmutableInstruction11x(Opcode.RETURN, 0)
        ))
        assertEquals(setOf("Lfeature;->a:Lxfp;"), result.fields)
        assertTrue(result.ambiguousReturn)
    }

    @Test fun invokingWithTwoDifferentFieldsRemainsAmbiguous() {
        val left = ImmutableFieldReference("Lfeature;", "a", "Lxfp;")
        val right = ImmutableFieldReference("Lfeature;", "b", "Ljava/lang/Object;")
        val read = ImmutableMethodReference("Lxfp;", "l", listOf("Ljava/lang/Object;"), "Z")
        val result = FieldReturnDataFlow.trace(listOf(
            ImmutableInstruction21c(Opcode.SGET_OBJECT, 0, left),
            ImmutableInstruction21c(Opcode.SGET_OBJECT, 1, right),
            ImmutableInstruction3rc(Opcode.INVOKE_VIRTUAL_RANGE, 0, 2, read),
            ImmutableInstruction11x(Opcode.MOVE_RESULT, 0),
            ImmutableInstruction11x(Opcode.RETURN, 0)
        ))
        assertEquals(setOf("Lfeature;->a:Lxfp;", "Lfeature;->b:Ljava/lang/Object;"), result.fields)
        assertTrue(result.ambiguousReturn)
    }

}
