package io.github.aaexperiments.resolver

import io.github.aaexperiments.discovery.*
import org.junit.Assert.*
import org.junit.Test

class CompatibilityRediscoveryTest {
    @Test fun ambiguousCurrentMappingIsRecoveredOnlyFromSameStableKey() {
        val old = identifier("Feature__enabled", listOf(method("Lold;", "a", 12)), editable = true)
        val good = method("Lnew;", "b", 12)
        val noise = method("Lnoise;", "c", 40, "different")
        val current = identifier("Feature__enabled", listOf(noise, good), editable = false)

        val result = CompatibilityRediscovery.apply(listOf(old), listOf(current), "old-hash").single()

        assertTrue(result.editable)
        assertEquals(MappingResolution.STRUCTURAL, result.resolution)
        assertEquals(good, result.getterCandidates.single())
        assertEquals("REDISCOVERED_STRUCTURAL", result.metadata["rediscoveryStatus"])
        assertEquals("old-hash", result.metadata["rediscoveryPreviousBaseSha256"])
    }

    @Test fun missingStableKeyIsNeverAttachedToSimilarNewKey() {
        val old = identifier("Removed__enabled", listOf(method("Lold;", "a", 12)), editable = true)
        val current = identifier("Different__enabled", listOf(method("Lnew;", "b", 12)), editable = true)

        val result = CompatibilityRediscovery.apply(listOf(old), listOf(current), "old-hash").single()

        assertEquals("NEW_KEY", result.metadata["rediscoveryStatus"])
        assertEquals("Different__enabled", result.key)
    }

    @Test fun unresolvedCollisionRemainsSuspended() {
        val oldA = identifier("A__enabled", listOf(method("La;", "a", 10)), editable = true)
        val oldB = identifier("B__enabled", listOf(method("Lb;", "b", 10)), editable = true)
        val shared = method("Lc;", "c", 10)
        val current = listOf(
            identifier("A__enabled", listOf(shared, method("Ld;", "d", 10)), editable = false),
            identifier("B__enabled", listOf(shared, method("Le;", "e", 10)), editable = false)
        )

        val result = CompatibilityRediscovery.apply(listOf(oldA, oldB), current, "old-hash")

        assertTrue(result.all { !it.editable })
        assertTrue(result.all { it.metadata["rediscoveryStatus"] == "SUSPENDED_AMBIGUOUS" })
    }

    private fun method(cls: String, name: String, count: Int, opcode: String = "hash") = MethodFingerprint(
        "classes.dex", cls, name, "()Z", DiscoveredType.BOOLEAN, emptyList(), count, opcode, false,
        listOf("$cls->x:Ljava/lang/Object;"), listOf("$cls->a()Z")
    )

    private fun identifier(key: String, methods: List<MethodFingerprint>, editable: Boolean) = DiscoveredIdentifier(
        key, IdentifierNamespace.FENOTYPE_FLAG, DiscoveredType.BOOLEAN, "false", emptyList(), methods,
        if (methods.size == 1) ResolutionConfidence.UNIQUE_GETTER else ResolutionConfidence.AMBIGUOUS,
        null, editable
    )
}
