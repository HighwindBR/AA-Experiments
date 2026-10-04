package io.github.aaexperiments.resolver

import io.github.aaexperiments.discovery.DiscoveredType
import io.github.aaexperiments.discovery.MethodFingerprint
import io.github.aaexperiments.scanner.ArchiveInputResolver
import io.github.aaexperiments.test.fixtureRootOrSkip
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import org.jf.dexlib2.iface.instruction.ReferenceInstruction
import org.jf.dexlib2.iface.reference.FieldReference
import org.jf.dexlib2.iface.reference.MethodReference
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Exact obfuscated names are confined to this reference-layer regression. */
class SubgraphOracleRegressionTest {
    companion object { private val methodCache = mutableMapOf<String, List<MethodFingerprint>>() }
    @Test fun realWrapperOracleRejectsKnownFalsePositive() {
        val root = fixtureRootOrSkip("aa-17.8.163744.apkm", "aa-17.8.663814.apkm")
        // Section 19 of the final report is explicitly a daily -> stable 17.8 oracle.
        val oldApk = ArchiveInputResolver.baseApk(File(root, "fixtures/apks/aa-17.8.163744.apkm"), File(root, "app/build/tmp/subgraph-oracle/daily"))
        val newApk = ArchiveInputResolver.baseApk(File(root, "fixtures/apks/aa-17.8.663814.apkm"), File(root, "app/build/tmp/subgraph-oracle/new"))
        val oldMethods = methods(oldApk)
        val newMethods = methods(newApk)
        val oldRoots = oldMethods.filter { it.className == "Llzl;" && it.methodName == "n" }
        val expectedRoots = newMethods.filter { it.className == "Lmav;" && it.methodName == "n" }
        val falseRoots = newMethods.filter { it.className == "Llyt;" && it.methodName == "q" }
        assertTrue("Missing report oracle methods", oldRoots.isNotEmpty() && expectedRoots.isNotEmpty() && falseRoots.isNotEmpty())
        val oldProfiles = CompactMethodGraphProfiler.profileRoots(oldMethods, oldRoots).values
        val currentProfiles = CompactMethodGraphProfiler.profileRoots(newMethods, expectedRoots + falseRoots)
        val bestExpected = oldProfiles.flatMap { old -> expectedRoots.mapNotNull { currentProfiles[CompactMethodGraphProfiler.signature(it)]?.let { GlobalSubgraphMatcher.score(old, it).score } } }.maxOrNull() ?: 0
        val bestFalse = oldProfiles.flatMap { old -> falseRoots.mapNotNull { currentProfiles[CompactMethodGraphProfiler.signature(it)]?.let { GlobalSubgraphMatcher.score(old, it).score } } }.maxOrNull() ?: 0
        assertTrue("Expected wrapper score $bestExpected must beat known false positive $bestFalse", bestExpected > bestFalse)
    }

    @Test fun realMessagingMetaCacheComponentUsesGlobalAssignment() {
        val root = fixtureRootOrSkip("aa-17.8.163744.apkm", "aa-17.8.663814.apkm")
        val dailyApk = ArchiveInputResolver.baseApk(File(root, "fixtures/apks/aa-17.8.163744.apkm"), File(root, "app/build/tmp/subgraph-component/daily"))
        val stableApk = ArchiveInputResolver.baseApk(File(root, "fixtures/apks/aa-17.8.663814.apkm"), File(root, "app/build/tmp/subgraph-component/stable"))
        val daily = methods(dailyApk)
        val stable = methods(stableApk)
        val oldRoots = daily.filter { it.className == "Lmax;" || it.className == "Lmaz;" }
        val newRoots = stable.filter { it.className == "Lmcg;" || it.className == "Lmce;" }
        assertTrue("Missing MessagingMetaCache oracle classes", oldRoots.isNotEmpty() && newRoots.isNotEmpty())
        val oldProfiles = CompactClassGraphProfiler.profiles(daily, setOf("Lmax;", "Lmaz;")).values
        val newProfiles = CompactClassGraphProfiler.profiles(stable, setOf("Lmcg;", "Lmce;")).values
        val result = GlobalClassSubgraphMatcher.match(oldProfiles, newProfiles, minimumMargin = 5)
        assertTrue("Expected global class pairing was not uniquely proven: $result old=$oldProfiles new=$newProfiles",
            result.matches["Lmax;"] == "Lmcg;" && result.matches["Lmaz;"] == "Lmce;")
    }

    private fun methods(apk: File): List<MethodFingerprint> = methodCache.getOrPut(apk.absolutePath) { ZipFile(apk).use { zip -> buildList {
        zip.entries().asSequence().filter { it.name.matches(Regex("classes(\\d*)\\.dex")) }.forEach { entry ->
            val dex = DexBackedDexFile.fromInputStream(Opcodes.getDefault(), BufferedInputStream(zip.getInputStream(entry)))
            dex.classes.forEach { cls -> cls.methods.forEach { method ->
                val instructions = method.implementation?.instructions?.toList() ?: return@forEach
                val fields = linkedSetOf<String>(); val calls = linkedSetOf<String>(); val opcodes = StringBuilder()
                instructions.forEach { instruction ->
                    opcodes.append(instruction.opcode.name).append(';')
                    when (val ref = (instruction as? ReferenceInstruction)?.reference) {
                        is FieldReference -> fields += "${ref.definingClass}->${ref.name}:${ref.type}"
                        is MethodReference -> calls += "${ref.definingClass}->${ref.name}(${ref.parameterTypes.joinToString("")})${ref.returnType}"
                    }
                }
                val descriptor = "(${method.parameterTypes.joinToString("")})${method.returnType}"
                add(MethodFingerprint(entry.name, method.definingClass, method.name, descriptor, type(method.returnType),
                    method.parameterTypes.map { it.toString() }, instructions.size, sha(opcodes.toString()), method.name == "<clinit>", fields.toList(), calls.toList()))
            } }
        }
    } } }

    private fun type(value: String) = when (value) {
        "Z" -> DiscoveredType.BOOLEAN; "B", "S", "C", "I" -> DiscoveredType.INT; "J" -> DiscoveredType.LONG
        "F" -> DiscoveredType.FLOAT; "D" -> DiscoveredType.DOUBLE; "V" -> DiscoveredType.VOID
        "Ljava/lang/String;" -> DiscoveredType.STRING; "[B" -> DiscoveredType.BYTES
        else -> if (value.startsWith("L") || value.startsWith("[")) DiscoveredType.OBJECT else DiscoveredType.UNKNOWN
    }
    private fun sha(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
