package io.github.aaexperiments.resolver

import io.github.aaexperiments.discovery.*
import org.junit.Assert.*
import org.junit.Test

class StructuralResolverTest {
    private fun method(cls: String, name: String, count: Int, opcode: String = "hash") = MethodFingerprint(
        "classes.dex", cls, name, "()Z", DiscoveredType.BOOLEAN, emptyList(), count, opcode, false,
        listOf("$cls->x:Ljava/lang/Object;"), listOf("$cls->a()Z")
    )
    private fun identifier(methods: List<MethodFingerprint>) = DiscoveredIdentifier("Flag__enabled", IdentifierNamespace.FENOTYPE_FLAG,
        DiscoveredType.BOOLEAN, "false", emptyList(), methods, if (methods.size == 1) ResolutionConfidence.UNIQUE_GETTER else ResolutionConfidence.AMBIGUOUS, null, false)

    @Test fun obfuscatedClassAndMethodCanBeRemappedStructurally() {
        val old = method("Laclt;", "g", 12)
        val renamed = method("Lacrt;", "i", 12)
        val noise = method("Lzzz;", "x", 40, "different")
        val result = StructuralResolver.resolve(identifier(listOf(old)), identifier(listOf(noise, renamed)))
        assertEquals(renamed, result.selected)
        assertEquals(ResolutionConfidence.STRUCTURALLY_VALIDATED, result.state)
    }

    @Test fun closeCandidatesRemainSuspended() {
        val old = method("La;", "a", 10)
        val one = method("Lb;", "b", 10); val two = method("Lc;", "c", 10)
        assertNull(StructuralResolver.resolve(identifier(listOf(old)), identifier(listOf(one, two))).selected)
    }

    @Test fun globalPassRejectsMethodReuseAcrossIdentifiers() {
        val oldA = identifier(listOf(method("La;", "a", 10))).copy(key = "A__flag")
        val oldB = identifier(listOf(method("Lb;", "b", 10))).copy(key = "B__flag")
        val shared = method("Lc;", "c", 10)
        val newA = identifier(listOf(shared)).copy(key = "A__flag")
        val newB = identifier(listOf(shared)).copy(key = "B__flag")
        val result = StructuralResolver.resolveBatch(listOf(oldA, oldB), listOf(newA, newB))
        assertEquals(1, result.rejectedMethodReuse.size)
        assertNull(result.byKey.getValue("A__flag").selected)
        assertNull(result.byKey.getValue("B__flag").selected)
    }
}
