package io.github.aaexperiments.discovery

import org.jf.dexlib2.iface.instruction.*
import org.jf.dexlib2.iface.reference.FieldReference
import org.jf.dexlib2.iface.reference.MethodReference

enum class ConsumptionPath { LIVE, INSTANCE_CACHE, STATIC_CACHE, INSTANCE_DERIVED_CACHE, STATIC_STARTUP, RUNTIME_LATCH, MIXED, UNKNOWN }
enum class ActivationScope {
    LIVE, NEXT_RENDER, NEXT_ACTIVITY, NEXT_CONFIG_SNAPSHOT,
    NEXT_INSTANCE, NEXT_LIFECYCLE_START, NEXT_CLASS_LOAD, NEXT_PROCESS, MIXED, UNKNOWN
}

data class CacheWrite(
    val sourceMethod: String,
    val targetField: String,
    val path: ConsumptionPath,
    val transformations: List<String> = emptyList(),
    val ownerMethod: String = ""
)

/**
 * Compact intraprocedural invoke-result flow used while DEX instructions are still available.
 * It records only method -> field cache evidence and never retains instruction objects.
 */
object CacheFlowAnalyzer {
    private data class Origin(val sourceMethods: Set<String> = emptySet(), val transformations: List<String> = emptyList())

    fun analyze(ownerMethod: String, methodName: String, instructions: List<Instruction>): List<CacheWrite> {
        val registers = mutableMapOf<Int, Origin>()
        val controlDependencies = mutableListOf<Origin>()
        var pending = Origin()
        val writes = mutableListOf<CacheWrite>()
        instructions.forEach { instruction ->
            val opcode = instruction.opcode.name.uppercase().replace('-', '_')
            val reference = (instruction as? ReferenceInstruction)?.reference
            when {
                opcode.startsWith("INVOKE") && reference is MethodReference -> {
                    val signature = method(reference)
                    val arguments = argumentRegisters(instruction)
                    val isStatic = opcode.startsWith("INVOKE_STATIC")
                    val valueArguments = if (isStatic) arguments else arguments.drop(1)
                    val inputs = valueArguments.mapNotNull(registers::get).filter { it.sourceMethods.isNotEmpty() }
                    val receiver = if (!isStatic) arguments.firstOrNull()?.let(registers::get) else null
                    val propagated = if (inputs.isNotEmpty()) inputs else listOfNotNull(receiver).filter { it.sourceMethods.isNotEmpty() }
                    pending = if (propagated.isNotEmpty()) Origin(
                        propagated.flatMap { it.sourceMethods }.toSet(),
                        (propagated.flatMap { it.transformations } + signature).distinct().takeLast(8)
                    ) else if (reference.parameterTypes.isEmpty() && reference.returnType in SCALAR_RETURN_TYPES) {
                        Origin(setOf(signature))
                    } else Origin()
                }
                opcode.startsWith("MOVE_RESULT") && instruction is OneRegisterInstruction -> {
                    registers[instruction.registerA] = pending
                    pending = Origin()
                }
                opcode.startsWith("MOVE") && instruction is TwoRegisterInstruction -> {
                    registers[instruction.registerB]?.let { registers[instruction.registerA] = it }
                    pending = Origin()
                }
                instruction is ThreeRegisterInstruction && isCombiningOpcode(opcode) -> {
                    registers[instruction.registerA] = merge(registers[instruction.registerB], registers[instruction.registerC], opcode)
                    pending = Origin()
                }
                instruction is TwoRegisterInstruction && opcode.endsWith("_2ADDR") && isCombiningOpcode(opcode) -> {
                    registers[instruction.registerA] = merge(registers[instruction.registerA], registers[instruction.registerB], opcode)
                    pending = Origin()
                }
                opcode.startsWith("IF_") -> {
                    conditionRegisters(instruction).mapNotNull(registers::get)
                        .filter { it.sourceMethods.isNotEmpty() }
                        .forEach { dependency -> if (dependency !in controlDependencies) controlDependencies += dependency }
                    pending = Origin()
                }
                (opcode.startsWith("SPUT") || opcode.startsWith("IPUT")) && reference is FieldReference && instruction is OneRegisterInstruction -> {
                    val origin = registers[instruction.registerA]
                    origin?.sourceMethods?.forEach { source -> writes += CacheWrite(
                        source, "${reference.definingClass}->${reference.name}:${reference.type}",
                        if (methodName == "<clinit>" || opcode.startsWith("SPUT")) ConsumptionPath.STATIC_CACHE else ConsumptionPath.INSTANCE_CACHE,
                        origin.transformations, ownerMethod
                    ) }
                    controlDependencies.clear()
                    pending = Origin()
                }
                instruction is OneRegisterInstruction && opcode.startsWith("CONST") -> {
                    registers[instruction.registerA] = mergeAll(controlDependencies, "CONTROL_DEPENDENCY")
                    pending = Origin()
                }
                instruction is OneRegisterInstruction && (opcode.startsWith("SGET") || opcode.startsWith("IGET") || opcode == "NEW_INSTANCE") -> {
                    registers.remove(instruction.registerA)
                    pending = Origin()
                }
                else -> if (!opcode.startsWith("MOVE_RESULT")) pending = Origin()
            }
        }
        return writes.distinct()
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

    private fun method(value: MethodReference) = "${value.definingClass}->${value.name}(${value.parameterTypes.joinToString("")})${value.returnType}"

    private val SCALAR_RETURN_TYPES = setOf("Z", "B", "S", "C", "I", "J", "F", "D", "Ljava/lang/String;")

    private fun isCombiningOpcode(opcode: String) = listOf("OR_", "AND_", "XOR_", "ADD_", "SUB_", "MUL_", "DIV_", "REM_").any(opcode::startsWith)

    private fun merge(left: Origin?, right: Origin?, operation: String): Origin {
        val values = listOfNotNull(left, right)
        return Origin(values.flatMap { it.sourceMethods }.toSet(), (values.flatMap { it.transformations } + operation).distinct().takeLast(8))
    }

    private fun mergeAll(values: List<Origin>, operation: String): Origin = Origin(
        values.flatMap { it.sourceMethods }.toSet(),
        (values.flatMap { it.transformations } + operation).distinct().takeLast(8)
    )
}

data class FieldPropagation(
    val sourceField: String,
    val targetField: String,
    val path: ConsumptionPath,
    val ownerMethod: String,
    val transformations: List<String> = emptyList()
)

data class FieldConstantWrite(val targetField: String, val value: Long, val ownerMethod: String, val isStatic: Boolean)
data class FieldFlowEvidence(
    val reads: Set<String> = emptySet(),
    val propagations: List<FieldPropagation> = emptyList(),
    val constantWrites: List<FieldConstantWrite> = emptyList()
)

/** Tracks field -> register -> field lineage after the initial getter cache has been proven. */
object FieldLineageAnalyzer {
    private data class Origin(
        val fields: Set<String> = emptySet(),
        val constants: Set<Long> = emptySet(),
        val transformations: List<String> = emptyList()
    )

    fun analyze(ownerMethod: String, methodName: String, instructions: List<Instruction>): FieldFlowEvidence {
        val registers = mutableMapOf<Int, Origin>()
        val controlDependencies = mutableListOf<Origin>()
        val reads = linkedSetOf<String>()
        val edges = mutableListOf<FieldPropagation>()
        val constants = mutableListOf<FieldConstantWrite>()
        var pending = Origin()
        instructions.forEach { instruction ->
            val opcode = instruction.opcode.name.uppercase().replace('-', '_')
            val reference = (instruction as? ReferenceInstruction)?.reference
            val field = (reference as? FieldReference)?.let { "${it.definingClass}->${it.name}:${it.type}" }
            when {
                opcode.startsWith("SGET") && field != null && instruction is OneRegisterInstruction -> {
                    registers[instruction.registerA] = Origin(fields = setOf(field)); reads += field; pending = Origin()
                }
                opcode.startsWith("IGET") && field != null && instruction is TwoRegisterInstruction -> {
                    registers[instruction.registerA] = Origin(fields = setOf(field)); reads += field; pending = Origin()
                }
                instruction is NarrowLiteralInstruction && instruction is OneRegisterInstruction && opcode.startsWith("CONST") -> {
                    registers[instruction.registerA] = merge(
                        controlDependencies + Origin(constants = setOf(instruction.narrowLiteral.toLong())),
                        if (controlDependencies.isEmpty()) "CONST" else "CONTROL_DEPENDENCY"
                    ); pending = Origin()
                }
                instruction is WideLiteralInstruction && instruction is OneRegisterInstruction && opcode.startsWith("CONST_WIDE") -> {
                    registers[instruction.registerA] = merge(
                        controlDependencies + Origin(constants = setOf(instruction.wideLiteral)),
                        if (controlDependencies.isEmpty()) "CONST" else "CONTROL_DEPENDENCY"
                    ); pending = Origin()
                }
                opcode.startsWith("INVOKE") && reference is MethodReference -> {
                    val arguments = argumentRegisters(instruction)
                    val isStatic = opcode.startsWith("INVOKE_STATIC")
                    val valueArguments = if (isStatic) arguments else arguments.drop(1)
                    val inputs = valueArguments.mapNotNull(registers::get).filter { it.fields.isNotEmpty() || it.constants.isNotEmpty() }
                    val receiver = if (!isStatic) arguments.firstOrNull()?.let(registers::get) else null
                    val propagated = when {
                        inputs.isNotEmpty() -> inputs
                        receiver != null -> listOf(receiver)
                        else -> emptyList()
                    }
                    pending = if (propagated.isEmpty()) Origin() else merge(propagated, method(reference))
                }
                opcode.startsWith("MOVE_RESULT") && instruction is OneRegisterInstruction -> {
                    registers[instruction.registerA] = pending; pending = Origin()
                }
                opcode.startsWith("MOVE") && instruction is TwoRegisterInstruction -> {
                    registers[instruction.registerB]?.let { registers[instruction.registerA] = it }; pending = Origin()
                }
                instruction is ThreeRegisterInstruction && isCombiningOpcode(opcode) -> {
                    registers[instruction.registerA] = merge(listOfNotNull(registers[instruction.registerB], registers[instruction.registerC]), opcode); pending = Origin()
                }
                instruction is TwoRegisterInstruction && opcode.endsWith("_2ADDR") && isCombiningOpcode(opcode) -> {
                    registers[instruction.registerA] = merge(listOfNotNull(registers[instruction.registerA], registers[instruction.registerB]), opcode); pending = Origin()
                }
                opcode.startsWith("IF_") -> {
                    controlDependencies.clear()
                    conditionRegisters(instruction).mapNotNull(registers::get)
                        .filter { it.fields.isNotEmpty() }
                        .forEach { dependency -> if (dependency !in controlDependencies) controlDependencies += dependency }
                    pending = Origin()
                }
                (opcode.startsWith("SPUT") || opcode.startsWith("IPUT")) && field != null && instruction is OneRegisterInstruction -> {
                    val origin = registers[instruction.registerA] ?: Origin()
                    val isStatic = methodName == "<clinit>" || opcode.startsWith("SPUT")
                    origin.fields.forEach { source -> edges += FieldPropagation(
                        source, field,
                        when {
                            "CONTROL_DEPENDENCY" in origin.transformations -> ConsumptionPath.RUNTIME_LATCH
                            isStatic -> ConsumptionPath.STATIC_STARTUP
                            else -> ConsumptionPath.INSTANCE_DERIVED_CACHE
                        },
                        ownerMethod, origin.transformations
                    ) }
                    origin.constants.singleOrNull()?.let { constants += FieldConstantWrite(field, it, ownerMethod, isStatic) }
                    controlDependencies.clear()
                    pending = Origin()
                }
                else -> if (!opcode.startsWith("MOVE_RESULT")) pending = Origin()
            }
        }
        return FieldFlowEvidence(reads, edges.distinct(), constants.distinct())
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

    private fun merge(values: List<Origin>, transformation: String) = Origin(
        values.flatMap { it.fields }.toSet(), values.flatMap { it.constants }.toSet(),
        (values.flatMap { it.transformations } + transformation).distinct().takeLast(8)
    )

    private fun isCombiningOpcode(opcode: String) = listOf("OR_", "AND_", "XOR_", "ADD_", "SUB_", "MUL_", "DIV_", "REM_").any(opcode::startsWith)
    private fun method(value: MethodReference) = "${value.definingClass}->${value.name}(${value.parameterTypes.joinToString("")})${value.returnType}"
}
