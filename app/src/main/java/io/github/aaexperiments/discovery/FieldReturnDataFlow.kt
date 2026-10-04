package io.github.aaexperiments.discovery

import org.jf.dexlib2.iface.instruction.*
import org.jf.dexlib2.iface.reference.FieldReference
import org.jf.dexlib2.iface.reference.MethodReference

/** Proves that a field value, possibly through moves/wrappers and CFG joins, reaches a return. */
object FieldReturnDataFlow {
    data class Result(val fields: Set<String>, val ambiguousReturn: Boolean)
    private data class Origin(val fields: Set<String> = emptySet(), val unknown: Boolean = false) {
        fun merge(other: Origin) = Origin(fields + other.fields, unknown || other.unknown)
    }
    private data class State(val registers: Map<Int, Origin> = emptyMap(), val pending: Origin = Origin()) {
        fun merge(other: State): State {
            val keys = registers.keys + other.registers.keys
            return State(keys.associateWith { (registers[it] ?: Origin(unknown = true)).merge(other.registers[it] ?: Origin(unknown = true)) }, pending.merge(other.pending))
        }
    }

    fun trace(instructions: List<Instruction>): Result {
        if (instructions.isEmpty()) return Result(emptySet(), false)
        val cfg = DexControlFlow(instructions)
        val incoming = arrayOfNulls<State>(instructions.size)
        incoming[0] = State()
        val queue = ArrayDeque<Int>().apply { add(0) }
        val returned = linkedSetOf<String>()
        var ambiguous = false
        while (queue.isNotEmpty()) {
            val index = queue.removeFirst()
            val input = incoming[index] ?: continue
            val (output, returnOrigin) = transfer(input, instructions[index])
            returnOrigin?.let { origin -> returned += origin.fields; ambiguous = ambiguous || origin.unknown || origin.fields.size > 1 }
            cfg.successors(index).forEach { successor ->
                val merged = incoming[successor]?.merge(output) ?: output
                if (merged != incoming[successor]) { incoming[successor] = merged; queue += successor }
            }
        }
        return Result(returned, ambiguous)
    }

    private fun transfer(state: State, instruction: Instruction): Pair<State, Origin?> {
        val registers = state.registers.toMutableMap()
        var pending = state.pending
        val opcode = instruction.opcode.name.uppercase().replace('-', '_')
        val reference = (instruction as? ReferenceInstruction)?.reference
        when {
            opcode.startsWith("SGET") && reference is FieldReference && instruction is OneRegisterInstruction -> {
                registers[instruction.registerA] = Origin(setOf(field(reference))); pending = Origin()
            }
            opcode.startsWith("IGET") && reference is FieldReference && instruction is TwoRegisterInstruction -> {
                registers[instruction.registerA] = Origin(setOf(field(reference))); pending = Origin()
            }
            opcode.startsWith("MOVE_RESULT") && instruction is OneRegisterInstruction -> {
                registers[instruction.registerA] = pending; pending = Origin()
            }
            opcode.startsWith("MOVE") && instruction is TwoRegisterInstruction -> {
                registers[instruction.registerA] = registers[instruction.registerB] ?: Origin(unknown = true); pending = Origin()
            }
            opcode.startsWith("INVOKE") && reference is MethodReference -> {
                pending = if (reference.returnType == "V") Origin() else argumentRegisters(instruction)
                    .map { registers[it] ?: Origin(unknown = true) }.fold(Origin(), Origin::merge)
            }
            opcode.startsWith("RETURN") && opcode != "RETURN_VOID" && instruction is OneRegisterInstruction -> {
                return state.copy(registers = registers, pending = Origin()) to (registers[instruction.registerA] ?: Origin(unknown = true))
            }
            writesRegisterA(instruction, opcode) && instruction is OneRegisterInstruction -> {
                registers[instruction.registerA] = Origin(unknown = true); pending = Origin()
            }
            !opcode.startsWith("MOVE_RESULT") -> pending = Origin()
        }
        return State(registers, pending) to null
    }

    private fun writesRegisterA(instruction: Instruction, opcode: String): Boolean =
        instruction is OneRegisterInstruction && (opcode.startsWith("CONST") || opcode == "NEW_INSTANCE" || opcode.startsWith("NEW_ARRAY"))

    private fun field(value: FieldReference) = "${value.definingClass}->${value.name}:${value.type}"
    private fun argumentRegisters(instruction: Instruction): List<Int> = when (instruction) {
        is RegisterRangeInstruction -> (instruction.startRegister until instruction.startRegister + instruction.registerCount).toList()
        is FiveRegisterInstruction -> listOf(instruction.registerC, instruction.registerD, instruction.registerE, instruction.registerF, instruction.registerG).take(instruction.registerCount)
        else -> emptyList()
    }
}
