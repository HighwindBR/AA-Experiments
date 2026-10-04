package io.github.aaexperiments.discovery

import org.jf.dexlib2.Opcode
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction10x
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction10t
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction11n
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction11x
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction21t
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction3rc
import org.jf.dexlib2.immutable.reference.ImmutableMethodReference
import org.junit.Assert.assertEquals
import org.junit.Test

class InvokeResultFlowAnalyzerTest {
    private val getter = ImmutableMethodReference("Lflag;", "enabled", emptyList(), "Z")

    @Test fun provesBranchUseOfInvokeResult() {
        val flow = InvokeResultFlowAnalyzer.analyze(listOf(
            ImmutableInstruction3rc(Opcode.INVOKE_STATIC_RANGE, 0, 0, getter),
            ImmutableInstruction11x(Opcode.MOVE_RESULT, 2),
            ImmutableInstruction21t(Opcode.IF_NEZ, 2, 2),
        ))
        assertEquals(InvokeCallOutcome.BRANCHED, flow.outcomes.single())
    }

    @Test fun discardedInvokeResultDoesNotBecomeProvenFlow() {
        val flow = InvokeResultFlowAnalyzer.analyze(listOf(
            ImmutableInstruction3rc(Opcode.INVOKE_STATIC_RANGE, 0, 0, getter),
            ImmutableInstruction10x(Opcode.NOP),
        ))
        assertEquals(InvokeCallOutcome.RESULT_NOT_CAPTURED, flow.outcomes.single())
    }

    @Test fun provesValueForwardedAsArgument() {
        val sink = ImmutableMethodReference("Lconsumer;", "accept", listOf("Z"), "V")
        val flow = InvokeResultFlowAnalyzer.analyze(listOf(
            ImmutableInstruction3rc(Opcode.INVOKE_STATIC_RANGE, 0, 0, getter),
            ImmutableInstruction11x(Opcode.MOVE_RESULT, 2),
            ImmutableInstruction3rc(Opcode.INVOKE_STATIC_RANGE, 2, 1, sink),
            ImmutableInstruction10x(Opcode.RETURN_VOID),
        ))
        assertEquals(InvokeCallOutcome.FORWARDED_ARGUMENT, flow.outcomes.first())
    }

    @Test fun distinguishesCapturedThenOverwritten() {
        val flow = InvokeResultFlowAnalyzer.analyze(listOf(
            ImmutableInstruction3rc(Opcode.INVOKE_STATIC_RANGE, 0, 0, getter),
            ImmutableInstruction11x(Opcode.MOVE_RESULT, 2),
            ImmutableInstruction11n(Opcode.CONST_4, 2, 0),
            ImmutableInstruction11x(Opcode.RETURN, 2),
        ))
        assertEquals(InvokeCallOutcome.CAPTURED_THEN_OVERWRITTEN, flow.outcomes.single())
    }

    @Test fun preservesDuplicateCallsitesInInstructionOrder() {
        val flow = InvokeResultFlowAnalyzer.analyze(listOf(
            ImmutableInstruction3rc(Opcode.INVOKE_STATIC_RANGE, 0, 0, getter),
            ImmutableInstruction10x(Opcode.NOP),
            ImmutableInstruction3rc(Opcode.INVOKE_STATIC_RANGE, 0, 0, getter),
            ImmutableInstruction11x(Opcode.MOVE_RESULT, 2),
            ImmutableInstruction21t(Opcode.IF_NEZ, 2, 2),
        ))
        assertEquals(listOf("Lflag;->enabled()Z", "Lflag;->enabled()Z"), flow.callsites)
        assertEquals(listOf(InvokeCallOutcome.RESULT_NOT_CAPTURED, InvokeCallOutcome.BRANCHED), flow.outcomes)
    }

    @Test fun instanceGetterKeepsOwnResultOriginSeparateFromReceiver() {
        val provider = ImmutableMethodReference("Lprovider;", "config", emptyList(), "Lflag;")
        val flow = InvokeResultFlowAnalyzer.analyze(listOf(
            ImmutableInstruction3rc(Opcode.INVOKE_STATIC_RANGE, 0, 0, provider),
            ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0),
            ImmutableInstruction3rc(Opcode.INVOKE_VIRTUAL_RANGE, 0, 1, getter),
            ImmutableInstruction11x(Opcode.MOVE_RESULT, 2),
            ImmutableInstruction21t(Opcode.IF_NEZ, 2, 2),
        ))
        assertEquals(listOf("Lprovider;->config()Lflag;", "Lflag;->enabled()Z"), flow.callsites)
        assertEquals(listOf(InvokeCallOutcome.USED_AS_RECEIVER, InvokeCallOutcome.BRANCHED), flow.outcomes)
    }

    @Test fun preservesInvokeOriginAcrossCfgJoin() {
        val flow = InvokeResultFlowAnalyzer.analyze(listOf(
            ImmutableInstruction3rc(Opcode.INVOKE_STATIC_RANGE, 0, 0, getter), // offset 0, size 3
            ImmutableInstruction11x(Opcode.MOVE_RESULT, 0),                  // offset 3
            ImmutableInstruction21t(Opcode.IF_NEZ, 1, 4),                    // offset 4 -> 8
            ImmutableInstruction11n(Opcode.CONST_4, 0, 0),                   // offset 6
            ImmutableInstruction10t(Opcode.GOTO, 1),                         // offset 7 -> 8
            ImmutableInstruction11x(Opcode.RETURN, 0),                       // offset 8
        ))
        assertEquals(InvokeCallOutcome.CFG_ORIGIN_AMBIGUOUS, flow.outcomes.single())
    }
}
