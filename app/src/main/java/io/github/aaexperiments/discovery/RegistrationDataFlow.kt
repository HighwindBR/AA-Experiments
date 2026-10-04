package io.github.aaexperiments.discovery

import org.jf.dexlib2.iface.instruction.*
import org.jf.dexlib2.iface.reference.FieldReference
import org.jf.dexlib2.iface.reference.MethodReference
import org.jf.dexlib2.iface.reference.StringReference

/**
 * Conservative, intraprocedural register provenance for static registration code.
 * It proves literal -> factory value -> sput relationships across CFG branches and joins.
 */
object RegistrationDataFlow {
    data class Evidence(val fields: Set<String>, val candidateDefaults: Set<String>, val ambiguous: Boolean = false)

    sealed interface Op {
        data class StringValue(val register: Int, val value: String) : Op
        data class NumericValue(val register: Int, val value: String) : Op
        data class Move(val target: Int, val source: Int) : Op
        data class Invoke(val registers: List<Int>, val returnsValue: Boolean) : Op
        data class MoveResult(val register: Int) : Op
        data class StaticFieldWrite(val register: Int, val field: String) : Op
        data class Clobber(val register: Int) : Op
        data object Other : Op
    }

    fun trace(instructions: List<Instruction>): Map<String, Set<String>> = traceDetailed(instructions).mapValues { it.value.fields }
    fun traceDetailed(instructions: List<Instruction>): Map<String, Evidence> = traceCfg(instructions, decodeAll(instructions))
    internal fun decodeAll(instructions: List<Instruction>): List<Op> = instructions.map(::decode)

    internal fun traceOps(ops: List<Op>): Map<String, Set<String>> = traceDetailedOps(ops).mapValues { it.value.fields }

    internal fun traceDetailedOps(ops: List<Op>): Map<String, Evidence> {
        data class Origin(val literals: Set<String> = emptySet(), val scalars: Set<String> = emptySet())
        val registers = mutableMapOf<Int, Origin>()
        val fields = linkedMapOf<String, MutableSet<String>>()
        val defaults = linkedMapOf<String, MutableSet<String>>()
        var pendingResult = Origin()
        ops.forEach { op ->
            when (op) {
                is Op.StringValue -> { registers[op.register] = Origin(literals = setOf(op.value)); pendingResult = Origin() }
                is Op.NumericValue -> { registers[op.register] = Origin(scalars = setOf(op.value)); pendingResult = Origin() }
                is Op.Move -> { registers[op.target] = registers[op.source] ?: Origin(); pendingResult = Origin() }
                is Op.Invoke -> {
                    val origins = op.registers.mapNotNull(registers::get)
                    pendingResult = if (op.returnsValue) Origin(origins.flatMap { it.literals }.toSet(), origins.flatMap { it.scalars }.toSet()) else Origin()
                }
                is Op.MoveResult -> { registers[op.register] = pendingResult; pendingResult = Origin() }
                is Op.StaticFieldWrite -> {
                    val origin = registers[op.register] ?: Origin()
                    val keys = origin.literals.filter(::looksLikeRegistrationKey)
                    keys.forEach { literal ->
                        fields.getOrPut(literal) { linkedSetOf() } += op.field
                        defaults.getOrPut(literal) { linkedSetOf() } += origin.scalars + (origin.literals - literal)
                    }
                    pendingResult = Origin()
                }
                is Op.Clobber -> { registers.remove(op.register); pendingResult = Origin() }
                Op.Other -> if (pendingResult.literals.isNotEmpty() || pendingResult.scalars.isNotEmpty()) pendingResult = Origin()
            }
        }
        return fields.mapValues { (literal, value) -> Evidence(value.toSet(), defaults[literal].orEmpty().toSet()) }
    }

    private data class CfgOrigin(
        val literals: Set<String> = emptySet(),
        val scalars: Set<String> = emptySet(),
        val ambiguous: Boolean = false
    ) {
        fun merge(other: CfgOrigin): CfgOrigin {
            val mergedLiterals = literals + other.literals
            val mergedScalars = scalars + other.scalars
            val registrationKeys = mergedLiterals.filter(::looksLikeRegistrationKey)
            val stringDefaults = mergedLiterals - registrationKeys.toSet()
            return CfgOrigin(
                mergedLiterals,
                mergedScalars,
                ambiguous || other.ambiguous || registrationKeys.size > 1 || stringDefaults.size > 1 || mergedScalars.size > 1
            )
        }
    }
    private data class CfgState(val registers: Map<Int, CfgOrigin> = emptyMap(), val pending: CfgOrigin = CfgOrigin()) {
        fun merge(other: CfgState): CfgState {
            val keys = registers.keys + other.registers.keys
            return CfgState(keys.associateWith { (registers[it] ?: CfgOrigin(ambiguous = true)).merge(other.registers[it] ?: CfgOrigin(ambiguous = true)) }, pending.merge(other.pending))
        }
    }

    private fun traceCfg(instructions: List<Instruction>, ops: List<Op>): Map<String, Evidence> {
        if (instructions.isEmpty()) return emptyMap()
        val cfg = DexControlFlow(instructions)
        val incoming = arrayOfNulls<CfgState>(instructions.size); incoming[0] = CfgState()
        val queue = ArrayDeque<Int>().apply { add(0) }
        val fields = linkedMapOf<String, MutableSet<String>>()
        val defaults = linkedMapOf<String, MutableSet<String>>()
        val ambiguous = linkedSetOf<String>()
        while (queue.isNotEmpty()) {
            val index = queue.removeFirst(); val input = incoming[index] ?: continue
            val registers = input.registers.toMutableMap(); var pending = input.pending
            when (val op = ops[index]) {
                is Op.StringValue -> { registers[op.register] = CfgOrigin(literals = setOf(op.value)); pending = CfgOrigin() }
                is Op.NumericValue -> { registers[op.register] = CfgOrigin(scalars = setOf(op.value)); pending = CfgOrigin() }
                is Op.Move -> { registers[op.target] = registers[op.source] ?: CfgOrigin(ambiguous = true); pending = CfgOrigin() }
                is Op.Invoke -> {
                    // Unknown receiver/context arguments carry no key provenance. Ambiguity is
                    // introduced only when distinct candidate keys actually merge.
                    pending = if (!op.returnsValue) CfgOrigin() else op.registers.map { registers[it] ?: CfgOrigin() }
                        .fold(CfgOrigin(), CfgOrigin::merge)
                }
                is Op.MoveResult -> { registers[op.register] = pending; pending = CfgOrigin() }
                is Op.StaticFieldWrite -> {
                    val origin = registers[op.register] ?: CfgOrigin(ambiguous = true)
                    val keys = origin.literals.filter(::looksLikeRegistrationKey)
                    keys.forEach { literal ->
                        fields.getOrPut(literal) { linkedSetOf() } += op.field
                        defaults.getOrPut(literal) { linkedSetOf() } += origin.scalars + (origin.literals - literal)
                        if (origin.ambiguous || keys.size > 1) ambiguous += literal
                    }
                    pending = CfgOrigin()
                }
                is Op.Clobber -> { registers.remove(op.register); pending = CfgOrigin() }
                Op.Other -> pending = CfgOrigin()
            }
            val output = CfgState(registers, pending)
            cfg.successors(index).forEach { successor ->
                val merged = incoming[successor]?.merge(output) ?: output
                if (merged != incoming[successor]) { incoming[successor] = merged; queue += successor }
            }
        }
        return fields.mapValues { (literal, value) -> Evidence(value, defaults[literal].orEmpty(), literal in ambiguous) }
    }

    private fun decode(instruction: Instruction): Op {
        val opcode = instruction.opcode.name.uppercase().replace('-', '_')
        val reference = (instruction as? ReferenceInstruction)?.reference
        if (reference is StringReference && instruction is OneRegisterInstruction && opcode.startsWith("CONST_STRING")) {
            return Op.StringValue(instruction.registerA, reference.string)
        }
        if (instruction is org.jf.dexlib2.iface.instruction.WideLiteralInstruction && instruction is OneRegisterInstruction && opcode.startsWith("CONST_WIDE")) {
            return Op.NumericValue(instruction.registerA, instruction.wideLiteral.toString())
        }
        if (instruction is org.jf.dexlib2.iface.instruction.NarrowLiteralInstruction && instruction is OneRegisterInstruction && opcode.startsWith("CONST")) {
            return Op.NumericValue(instruction.registerA, instruction.narrowLiteral.toString())
        }
        if (opcode.startsWith("MOVE_RESULT") && instruction is OneRegisterInstruction) return Op.MoveResult(instruction.registerA)
        if (opcode.startsWith("MOVE") && instruction is TwoRegisterInstruction) return Op.Move(instruction.registerA, instruction.registerB)
        if (opcode.startsWith("INVOKE") && reference is MethodReference) {
            return Op.Invoke(argumentRegisters(instruction), reference.returnType != "V")
        }
        if (opcode.startsWith("SPUT") && reference is FieldReference && instruction is OneRegisterInstruction) {
            return Op.StaticFieldWrite(instruction.registerA, "${reference.definingClass}->${reference.name}:${reference.type}")
        }
        if ((opcode == "NEW_INSTANCE" || opcode.startsWith("SGET")) && instruction is OneRegisterInstruction) {
            return Op.Clobber(instruction.registerA)
        }
        return Op.Other
    }

    private fun argumentRegisters(instruction: Instruction): List<Int> = when (instruction) {
        is RegisterRangeInstruction -> (instruction.startRegister until instruction.startRegister + instruction.registerCount).toList()
        is FiveRegisterInstruction -> listOf(instruction.registerC, instruction.registerD, instruction.registerE, instruction.registerF, instruction.registerG).take(instruction.registerCount)
        else -> emptyList()
    }

    private fun looksLikeRegistrationKey(value: String) =
        "__" in value || value.startsWith("key_") || value.startsWith("pref_") ||
            value.startsWith("GH_") || value.endsWith("_ENABLED") || value.matches(Regex("[A-Z][A-Z0-9_]*"))
}
