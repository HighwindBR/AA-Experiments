package io.github.aaexperiments.resolver

import io.github.aaexperiments.discovery.DiscoveredType
import io.github.aaexperiments.discovery.MethodFingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubgraphMatcherTest {
    private fun method(cls: String, name: String, descriptor: String = "()Z", count: Int = 10,
                       opcode: String = "root", calls: List<String> = emptyList()) = MethodFingerprint(
        "classes.dex", cls, name, descriptor, if (descriptor.endsWith("Z")) DiscoveredType.BOOLEAN else DiscoveredType.VOID,
        emptyList(), count, opcode, false, emptyList(), calls)

    @Test fun secondLayerDisambiguatesLocallyIdenticalWrappers() {
        val oldLeaf = method("LoldLeaf;", "a", count = 30, opcode = "leaf-a")
        val oldRoot = method("LoldRoot;", "n", calls = listOf("LoldLeaf;->a()Z"))
        val goodLeaf = method("LgoodLeaf;", "x", count = 30, opcode = "leaf-a")
        val badLeaf = method("LbadLeaf;", "x", count = 80, opcode = "leaf-b")
        val goodRoot = method("LgoodRoot;", "n", calls = listOf("LgoodLeaf;->x()Z"))
        val falseRoot = method("LfalseRoot;", "q", calls = listOf("LbadLeaf;->x()Z"))

        val old = CompactMethodGraphProfiler.profiles(listOf(oldRoot, oldLeaf)).getValue("LoldRoot;->n()Z")
        val current = CompactMethodGraphProfiler.profiles(listOf(goodRoot, goodLeaf, falseRoot, badLeaf))
        val good = current.getValue("LgoodRoot;->n()Z")
        val falsePositive = current.getValue("LfalseRoot;->q()Z")
        assertTrue(GlobalSubgraphMatcher.score(old, good).score > GlobalSubgraphMatcher.score(old, falsePositive).score)
    }

    @Test fun globalPassProducesOneToOneAssignment() {
        val oldA = CompactMethodGraphProfiler.profiles(listOf(method("Loa;", "a", opcode = "a"))).values.single()
        val oldB = CompactMethodGraphProfiler.profiles(listOf(method("Lob;", "b", opcode = "b"))).values.single()
        val newA = CompactMethodGraphProfiler.profiles(listOf(method("Lna;", "x", opcode = "a"))).values.single()
        val newB = CompactMethodGraphProfiler.profiles(listOf(method("Lnb;", "y", opcode = "b"))).values.single()
        val result = GlobalSubgraphMatcher.match(listOf(oldA, oldB), listOf(newA, newB), minimumScore = 100, minimumMargin = 10)
        assertEquals("Lna;->x()Z", result.matches.getValue("Loa;->a()Z").new.rootSignature)
        assertEquals("Lnb;->y()Z", result.matches.getValue("Lob;->b()Z").new.rootSignature)
        assertTrue(result.ambiguousOldMethods.isEmpty())
    }

    @Test fun unresolvedGlobalClassTieIsSuspended() {
        fun profile(name: String) = ClassSubgraphProfile(name, 2, setOf("abi:()Z"), mapOf("owner:StableView" to 1), emptyMap())
        val result = GlobalClassSubgraphMatcher.match(
            listOf(profile("LoldA;"), profile("LoldB;")),
            listOf(profile("LnewA;"), profile("LnewB;")),
            minimumMargin = 1
        )
        assertTrue(result.matches.isEmpty())
        assertEquals(setOf("LoldA;", "LoldB;"), result.ambiguousOldClasses)
    }
}
