package io.github.aaexperiments.discovery

import org.jf.dexlib2.iface.instruction.*
import org.jf.dexlib2.iface.reference.MethodReference

enum class BranchPolarity { TRUE_ON_BRANCH, TRUE_ON_FALLTHROUGH, UNKNOWN }
data class DecisionUse(val invokedMethod: String, val branchOpcode: String, val polarity: BranchPolarity)

/** Finds direct invoke-result -> Boolean branch decisions without guessing downstream meaning. */
object DecisionRegionAnalyzer {
    fun analyze(instructions: List<Instruction>): List<DecisionUse> {
        var pendingInvoke: String? = null
        val registerOrigins = mutableMapOf<Int, String>()
        val result = mutableListOf<DecisionUse>()
        instructions.forEach { instruction ->
            val opcode = instruction.opcode.name.uppercase().replace('-', '_')
            val reference = (instruction as? ReferenceInstruction)?.reference
            when {
                opcode.startsWith("INVOKE") && reference is MethodReference -> pendingInvoke = method(reference)
                opcode.startsWith("MOVE_RESULT") && instruction is OneRegisterInstruction -> {
                    pendingInvoke?.let { registerOrigins[instruction.registerA] = it }; pendingInvoke = null
                }
                (opcode == "IF_EQZ" || opcode == "IF_NEZ") && instruction is OneRegisterInstruction -> {
                    registerOrigins[instruction.registerA]?.let { invoked -> result += DecisionUse(invoked, opcode,
                        if (opcode == "IF_NEZ") BranchPolarity.TRUE_ON_BRANCH else BranchPolarity.TRUE_ON_FALLTHROUGH) }
                    pendingInvoke = null
                }
                opcode.startsWith("MOVE") && instruction is TwoRegisterInstruction -> {
                    registerOrigins[instruction.registerB]?.let { registerOrigins[instruction.registerA] = it }
                    pendingInvoke = null
                }
                instruction is OneRegisterInstruction && (opcode.startsWith("CONST") || opcode.startsWith("SGET") || opcode.startsWith("IGET") || opcode == "NEW_INSTANCE") -> {
                    registerOrigins.remove(instruction.registerA); pendingInvoke = null
                }
                else -> if (!opcode.startsWith("MOVE_RESULT")) pendingInvoke = null
            }
        }
        return result.distinct()
    }

    private fun method(value: MethodReference) = "${value.definingClass}->${value.name}(${value.parameterTypes.joinToString("")})${value.returnType}"
}
