package io.github.aaexperiments.discovery

import io.github.aaexperiments.scanner.ArchiveInputResolver
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import org.jf.dexlib2.iface.instruction.*
import org.jf.dexlib2.iface.reference.StringReference
import org.jf.dexlib2.iface.reference.FieldReference
import org.junit.Test
import java.io.BufferedInputStream
import java.io.File
import java.util.zip.ZipFile

class CieloInstructionDumpTest {
    @Test fun dump() {
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }.first { File(it, "fixtures/apks").isDirectory }
        val out = StringBuilder()
        listOf("16.9.666314", "17.8.663814").forEach { version ->
            val base = ArchiveInputResolver.baseApk(File(root, "fixtures/apks/aa-$version.apkm"), File(root, "app/build/tmp/cielo-dump/$version"))
            ZipFile(base).use { zip -> zip.entries().asSequence().filter { it.name.matches(Regex("classes(\\d*)\\.dex")) }.forEach { entry ->
                val dex = DexBackedDexFile.fromInputStream(Opcodes.getDefault(), BufferedInputStream(zip.getInputStream(entry)))
                dex.classes.forEach { cls -> cls.methods.forEach { method ->
                    val instructions = method.implementation?.instructions?.toList().orEmpty()
                    if (method.name == "<clinit>" && instructions.any { ((it as? ReferenceInstruction)?.reference as? StringReference)?.string == "NeoplanFeature__enabled" }) {
                        out.appendLine("$version ${entry.name} NEOPLAN ${method.definingClass}->${method.name}")
                        RegistrationDataFlow.decodeAll(instructions).forEach { out.appendLine("FLOW $it") }
                        out.appendLine("RESULT ${RegistrationDataFlow.trace(instructions)}")
                    }
                    if (method.parameterTypes.isEmpty() && method.returnType == "Z" && instructions.any { ((it as? ReferenceInstruction)?.reference as? StringReference)?.string == "CieloFeature__earth_enabled" }) {
                        out.appendLine("$version ${entry.name} ${method.definingClass}->${method.name}(${method.parameterTypes.joinToString("")})${method.returnType}")
                        instructions.forEach { instruction -> out.appendLine(format(instruction)) }
                    }
                    if (version == "17.8.663814" && instructions.any {
                            ((it as? ReferenceInstruction)?.reference as? FieldReference)?.let { field ->
                                "${field.definingClass}->${field.name}:${field.type}" == "Lacxl;->a:Lwhq;"
                            } == true
                        }) {
                        out.appendLine("$version ${entry.name} NEOPLAN_READER ${method.definingClass}->${method.name}(${method.parameterTypes.joinToString("")})${method.returnType}")
                        instructions.forEach { instruction -> out.appendLine(format(instruction)) }
                        out.appendLine("FIELD_RETURN ${FieldReturnDataFlow.trace(instructions)}")
                    }
                } }
            } }
        }
        File(root, "outputs/discovery/cielo-instructions.txt").writeText(out.toString())
    }
    private fun format(value: Instruction): String = buildString {
        append(value.opcode.name)
        (value as? OneRegisterInstruction)?.let { append(" v${it.registerA}") }
        (value as? TwoRegisterInstruction)?.let { append(" v${it.registerA},v${it.registerB}") }
        (value as? NarrowLiteralInstruction)?.let { append(" literal=${it.narrowLiteral}") }
        (value as? WideLiteralInstruction)?.let { append(" literal=${it.wideLiteral}") }
        (value as? ReferenceInstruction)?.let { append(" ref=${it.reference}") }
    }
}
