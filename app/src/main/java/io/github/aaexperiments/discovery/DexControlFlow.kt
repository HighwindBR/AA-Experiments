package io.github.aaexperiments.discovery

import org.jf.dexlib2.iface.instruction.Instruction
import org.jf.dexlib2.iface.instruction.OffsetInstruction
import org.jf.dexlib2.iface.instruction.SwitchPayload

/** Instruction-level CFG used by the resolver. Offsets are DEX code units, not list indexes. */
internal class DexControlFlow(private val instructions: List<Instruction>) {
    private val offsets = buildList {
        var offset = 0
        instructions.forEach { add(offset); offset += it.codeUnits }
    }
    private val indexByOffset = offsets.withIndex().associate { it.value to it.index }

    fun successors(index: Int): IntArray {
        val instruction = instructions[index]
        val opcode = instruction.opcode.name.uppercase().replace('-', '_')
        if (opcode.startsWith("RETURN") || opcode == "THROW" || opcode.endsWith("PAYLOAD")) return intArrayOf()
        val fallthrough = (index + 1).takeIf { it < instructions.size }
        if (opcode.startsWith("GOTO") && instruction is OffsetInstruction) {
            return indexByOffset[offsets[index] + instruction.codeOffset]?.let { intArrayOf(it) } ?: intArrayOf()
        }
        if (opcode.startsWith("IF_") && instruction is OffsetInstruction) {
            return listOfNotNull(fallthrough, indexByOffset[offsets[index] + instruction.codeOffset]).distinct().toIntArray()
        }
        if ((opcode == "PACKED_SWITCH" || opcode == "SPARSE_SWITCH") && instruction is OffsetInstruction) {
            val payloadIndex = indexByOffset[offsets[index] + instruction.codeOffset]
            val payload = payloadIndex?.let(instructions::get) as? SwitchPayload
            return buildList {
                fallthrough?.let(::add)
                payload?.switchElements?.forEach { element -> indexByOffset[offsets[index] + element.offset]?.let(::add) }
            }.distinct().toIntArray()
        }
        return fallthrough?.let { intArrayOf(it) } ?: intArrayOf()
    }
}
