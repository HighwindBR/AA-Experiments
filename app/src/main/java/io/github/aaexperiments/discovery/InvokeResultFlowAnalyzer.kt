package io.github.aaexperiments.discovery

import org.jf.dexlib2.iface.instruction.*
import org.jf.dexlib2.iface.reference.FieldReference
import org.jf.dexlib2.iface.reference.MethodReference

enum class InvokeCallOutcome(val code: Byte, val proven: Boolean) {
    UNKNOWN(0, false),
    RETURNED(1, true), BRANCHED(2, true), STORED_FIELD(3, true),
    FORWARDED_ARGUMENT(4, true), USED_AS_RECEIVER(5, true),
    STORED_ARRAY_OR_COLLECTION(6, true), TRANSFORMED(7, true),
    RESULT_NOT_CAPTURED(20, false), CAPTURED_THEN_OVERWRITTEN(21, false),
    CAPTURED_BUT_UNUSED(22, false), UNSUPPORTED_OPCODE(23, false),
    CFG_ORIGIN_AMBIGUOUS(24, false);

    companion object {
        fun fromCode(code: Byte) = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

data class InvokeFlowSummary(
    val callsites: List<String>,
    val outcomes: List<InvokeCallOutcome>,
    val unsupportedOpcodes: Set<String> = emptySet()
)

/** Exact callsite diagnostics with CFG-aware register provenance. */
object InvokeResultFlowAnalyzer {
    private const val MAX_ORIGINS_PER_VALUE = 16
    private data class Origin(val sources: Set<Int> = emptySet(), val ambiguous: Boolean = false) {
        fun merge(other: Origin): Origin {
            val combined = sources + other.sources
            return Origin(combined.take(MAX_ORIGINS_PER_VALUE).toSet(),
                ambiguous || other.ambiguous || sources != other.sources || combined.size > MAX_ORIGINS_PER_VALUE)
        }
    }
    private data class State(val registers: Map<Int, Origin> = emptyMap(), val pending: Origin = Origin()) {
        fun merge(other: State): State {
            val keys = registers.keys + other.registers.keys
            return State(keys.associateWith {
                (registers[it] ?: Origin(ambiguous = true)).merge(other.registers[it] ?: Origin(ambiguous = true))
            }, pending.merge(other.pending))
        }
    }
    private data class Event(val source: Int, val outcome: InvokeCallOutcome, val opcode: String? = null)

    fun analyze(instructions: List<Instruction>): InvokeFlowSummary {
        if (instructions.isEmpty()) return InvokeFlowSummary(emptyList(), emptyList())
        val cfg = DexControlFlow(instructions)
        var invokeCount = 0
        val callsites = mutableListOf<String>()
        val invokeOrdinals = IntArray(instructions.size) { index ->
            val instruction = instructions[index]
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            if (instruction.opcode.name.uppercase().startsWith("INVOKE") && reference != null) {
                callsites += method(reference)
                invokeCount++
            } else -1
        }
        val incoming = arrayOfNulls<State>(instructions.size)
        incoming[0] = State()
        val queue = ArrayDeque<Int>().apply { add(0) }
        val events = mutableListOf<Event>()
        while (queue.isNotEmpty()) {
            val index = queue.removeFirst()
            val input = incoming[index] ?: continue
            val (output, emitted) = transfer(input, instructions[index], invokeOrdinals[index])
            events += emitted
            cfg.successors(index).forEach { successor ->
                val merged = incoming[successor]?.merge(output) ?: output
                if (merged != incoming[successor]) { incoming[successor] = merged; queue += successor }
            }
        }
        val outcomes = List(invokeCount) { ordinal ->
            val sourceEvents = events.filter { it.source == ordinal }.map { it.outcome }
            when {
                InvokeCallOutcome.CFG_ORIGIN_AMBIGUOUS in sourceEvents -> InvokeCallOutcome.CFG_ORIGIN_AMBIGUOUS
                else -> sourceEvents.maxWithOrNull(compareBy<InvokeCallOutcome> { if (it.proven) 1 else 0 }.thenBy { outcomePriority(it) })
                    ?: InvokeCallOutcome.UNKNOWN
            }
        }
        return InvokeFlowSummary(callsites, outcomes, events.mapNotNull { it.opcode }.toSet())
    }

    private fun transfer(state: State, instruction: Instruction, invokeOrdinal: Int): Pair<State, List<Event>> {
        val registers = state.registers.toMutableMap()
        var pending = state.pending
        val events = mutableListOf<Event>()
        val opcode = instruction.opcode.name.uppercase().replace('-', '_')
        val reference = (instruction as? ReferenceInstruction)?.reference
        fun emit(origin: Origin?, proven: InvokeCallOutcome, opcodeName: String? = null) {
            origin?.sources.orEmpty().forEach { source ->
                events += Event(source, if (origin?.ambiguous == true && proven.proven) InvokeCallOutcome.CFG_ORIGIN_AMBIGUOUS else proven, opcodeName)
            }
        }
        if (!opcode.startsWith("MOVE_RESULT") && pending.sources.isNotEmpty()) {
            emit(pending, InvokeCallOutcome.RESULT_NOT_CAPTURED)
            pending = Origin()
        }
        when {
            opcode.startsWith("INVOKE") && reference is MethodReference -> {
                val arguments = argumentRegisters(instruction)
                val isStatic = opcode.startsWith("INVOKE_STATIC")
                val valueOrigins = (if (isStatic) arguments else arguments.drop(1)).mapNotNull(registers::get)
                val receiverOrigin = if (!isStatic) arguments.firstOrNull()?.let(registers::get) else null
                valueOrigins.forEach { emit(it, InvokeCallOutcome.FORWARDED_ARGUMENT) }
                if (valueOrigins.isEmpty()) emit(receiverOrigin, InvokeCallOutcome.USED_AS_RECEIVER)
                // The receiver is call context, not the identity of the returned scalar. Carrying
                // it into the result caused instance getters to inherit only the provider call and
                // disappear from their own branch/store evidence.
                val propagatedArguments = valueOrigins.fold(Origin()) { acc, origin -> acc.merge(origin) }
                pending = when {
                    reference.returnType == "V" -> Origin()
                    else -> Origin((propagatedArguments.sources + invokeOrdinal).take(MAX_ORIGINS_PER_VALUE).toSet(),
                        propagatedArguments.ambiguous || propagatedArguments.sources.size >= MAX_ORIGINS_PER_VALUE)
                }
            }
            opcode.startsWith("MOVE_RESULT") && instruction is OneRegisterInstruction -> {
                registers[instruction.registerA]?.let { emit(it, InvokeCallOutcome.CAPTURED_THEN_OVERWRITTEN) }
                registers[instruction.registerA] = pending; pending = Origin()
            }
            opcode.startsWith("MOVE") && instruction is TwoRegisterInstruction -> {
                registers[instruction.registerA]?.let { emit(it, InvokeCallOutcome.CAPTURED_THEN_OVERWRITTEN) }
                registers[instruction.registerA] = registers[instruction.registerB] ?: Origin(); pending = Origin()
            }
            opcode.startsWith("IF_") || opcode.startsWith("PACKED_SWITCH") || opcode.startsWith("SPARSE_SWITCH") -> {
                conditionRegisters(instruction).forEach { emit(registers[it], InvokeCallOutcome.BRANCHED) }; pending = Origin()
            }
            opcode.startsWith("RETURN") && opcode != "RETURN_VOID" && instruction is OneRegisterInstruction -> {
                emit(registers[instruction.registerA], InvokeCallOutcome.RETURNED); pending = Origin()
            }
            opcode == "RETURN_VOID" -> {
                registers.values.forEach { emit(it, InvokeCallOutcome.CAPTURED_BUT_UNUSED) }; pending = Origin()
            }
            (opcode.startsWith("SPUT") || opcode.startsWith("IPUT")) && reference is FieldReference && instruction is OneRegisterInstruction -> {
                emit(registers[instruction.registerA], InvokeCallOutcome.STORED_FIELD); pending = Origin()
            }
            opcode.startsWith("APUT") && instruction is ThreeRegisterInstruction -> {
                emit(registers[instruction.registerA], InvokeCallOutcome.STORED_ARRAY_OR_COLLECTION); pending = Origin()
            }
            instruction is ThreeRegisterInstruction && isTransform(opcode) -> {
                val merged = Origin(listOfNotNull(registers[instruction.registerB], registers[instruction.registerC]).flatMap { it.sources }.toSet())
                registers[instruction.registerA] = merged; emit(merged, InvokeCallOutcome.TRANSFORMED); pending = Origin()
            }
            instruction is TwoRegisterInstruction && opcode.endsWith("_2ADDR") && isTransform(opcode) -> {
                val merged = Origin(listOfNotNull(registers[instruction.registerA], registers[instruction.registerB]).flatMap { it.sources }.toSet())
                registers[instruction.registerA] = merged; emit(merged, InvokeCallOutcome.TRANSFORMED); pending = Origin()
            }
            instruction is TwoRegisterInstruction && isUnaryTransform(opcode) -> {
                val origin = registers[instruction.registerB] ?: Origin(); registers[instruction.registerA] = origin
                emit(origin, InvokeCallOutcome.TRANSFORMED); pending = Origin()
            }
            opcode == "CHECK_CAST" -> pending = Origin()
            writesRegisterA(instruction, opcode) && instruction is OneRegisterInstruction -> {
                registers[instruction.registerA]?.let { emit(it, InvokeCallOutcome.CAPTURED_THEN_OVERWRITTEN) }
                registers.remove(instruction.registerA); pending = Origin()
            }
            isPotentialUnsupportedUse(instruction, opcode) -> {
                operandRegisters(instruction).forEach { emit(registers[it], InvokeCallOutcome.UNSUPPORTED_OPCODE, opcode) }
                pending = Origin()
            }
            else -> if (!opcode.startsWith("MOVE_RESULT")) pending = Origin()
        }
        return State(registers, pending) to events
    }

    private fun outcomePriority(outcome: InvokeCallOutcome) = when (outcome) {
        InvokeCallOutcome.RETURNED -> 90; InvokeCallOutcome.BRANCHED -> 80; InvokeCallOutcome.STORED_FIELD -> 70
        InvokeCallOutcome.FORWARDED_ARGUMENT, InvokeCallOutcome.USED_AS_RECEIVER -> 60
        InvokeCallOutcome.STORED_ARRAY_OR_COLLECTION -> 50; InvokeCallOutcome.TRANSFORMED -> 40
        InvokeCallOutcome.UNSUPPORTED_OPCODE -> 30; InvokeCallOutcome.CFG_ORIGIN_AMBIGUOUS -> 25
        InvokeCallOutcome.CAPTURED_THEN_OVERWRITTEN -> 20; InvokeCallOutcome.CAPTURED_BUT_UNUSED -> 10
        InvokeCallOutcome.RESULT_NOT_CAPTURED -> 5; else -> 0
    }
    private fun argumentRegisters(instruction: Instruction): List<Int> = when (instruction) {
        is RegisterRangeInstruction -> (instruction.startRegister until instruction.startRegister + instruction.registerCount).toList()
        is FiveRegisterInstruction -> listOf(instruction.registerC, instruction.registerD, instruction.registerE, instruction.registerF, instruction.registerG).take(instruction.registerCount)
        else -> emptyList()
    }
    private fun conditionRegisters(instruction: Instruction): List<Int> = when (instruction) {
        is TwoRegisterInstruction -> listOf(instruction.registerA, instruction.registerB)
        is OneRegisterInstruction -> listOf(instruction.registerA)
        else -> emptyList()
    }
    private fun operandRegisters(instruction: Instruction): List<Int> = when (instruction) {
        is ThreeRegisterInstruction -> listOf(instruction.registerA, instruction.registerB, instruction.registerC)
        is TwoRegisterInstruction -> listOf(instruction.registerA, instruction.registerB)
        is OneRegisterInstruction -> listOf(instruction.registerA)
        else -> emptyList()
    }
    private fun writesRegisterA(instruction: Instruction, opcode: String) = instruction is OneRegisterInstruction &&
        (opcode.startsWith("CONST") || opcode.startsWith("SGET") || opcode.startsWith("IGET") || opcode == "NEW_INSTANCE" || opcode.startsWith("NEW_ARRAY"))
    private fun isPotentialUnsupportedUse(instruction: Instruction, opcode: String) =
        instruction is OneRegisterInstruction && opcode !in setOf("NOP", "GOTO") || instruction is TwoRegisterInstruction || instruction is ThreeRegisterInstruction
    private fun isTransform(opcode: String) = listOf("OR_", "AND_", "XOR_", "ADD_", "SUB_", "MUL_", "DIV_", "REM_", "CMP").any(opcode::startsWith)
    private fun isUnaryTransform(opcode: String) = opcode.startsWith("NEG_") || opcode.startsWith("NOT_") || "_TO_" in opcode
    private fun method(value: MethodReference) = "${value.definingClass}->${value.name}(${value.parameterTypes.joinToString("")})${value.returnType}"
}
