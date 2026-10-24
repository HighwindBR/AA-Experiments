package io.github.highwindbr.aaxp.core

import io.github.highwindbr.aaxp.discovery.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverrideCompatibilityTest {
    private val getter = MethodFingerprint("classes.dex", "La;", "b", "()Z", DiscoveredType.BOOLEAN,
        emptyList(), 2, "hash", false, emptyList(), emptyList())
    private fun identifier(type: DiscoveredType = DiscoveredType.BOOLEAN, editable: Boolean = true, getters: List<MethodFingerprint> = listOf(getter)) =
        DiscoveredIdentifier("Flag__enabled", IdentifierNamespace.FENOTYPE_FLAG, type, "false", emptyList(), getters,
            ResolutionConfidence.UNIQUE_GETTER, null, editable)

    @Test fun compatibleOverrideRemainsActive() {
        assertNull(suspendedOverrideReason(identifier(), TypedOverride(DiscoveredType.BOOLEAN, "true")))
    }

    @Test fun historicalValueIsSuspendedWhenTypeChanges() {
        assertEquals(SuspendedOverrideReason.SUSPENDED_TYPE_CHANGED,
            suspendedOverrideReason(identifier(DiscoveredType.LONG), TypedOverride(DiscoveredType.BOOLEAN, "true")))
    }

    @Test fun incorrectGetterAbiIsSuspended() {
        val value = TypedOverride(DiscoveredType.BOOLEAN, "true")
        assertEquals(SuspendedOverrideReason.SUSPENDED_MAPPING_AMBIGUOUS,
            suspendedOverrideReason(identifier(getters = listOf(getter.copy(descriptor = "()D"))), value))
    }

    @Test fun narrowerIntReturnTypesRejectOverflow() {
        listOf(
            Triple("()B", "-129", "128"),
            Triple("()S", "-32769", "32768"),
            Triple("()C", "-1", "65536")
        ).forEach { (descriptor, below, above) ->
            val typed = getter.copy(descriptor = descriptor, returnType = DiscoveredType.INT)
            val flag = identifier(type = DiscoveredType.INT, getters = listOf(typed))
            assertEquals(SuspendedOverrideReason.SUSPENDED_INVALID_VALUE,
                suspendedOverrideReason(flag, TypedOverride(DiscoveredType.INT, below)))
            assertEquals(SuspendedOverrideReason.SUSPENDED_INVALID_VALUE,
                suspendedOverrideReason(flag, TypedOverride(DiscoveredType.INT, above)))
        }
    }

    @Test fun aliasesDoNotClaimTheSameGetterTwice() {
        val claimed = mutableMapOf<Triple<String, String, String>, String>()
        assertEquals(true, claimNormalGetter(claimed, "Flag__first", "a.b", "c", "()Z"))
        assertEquals(false, claimNormalGetter(claimed, "Flag__second", "a.b", "c", "()Z"))
        assertEquals(true, claimNormalGetter(claimed, "Flag__third", "a.b", "d", "()Z"))
        assertEquals(2, claimed.size)
    }

    @Test fun missingAndAmbiguousMappingsAreSuspended() {
        val value = TypedOverride(DiscoveredType.BOOLEAN, "true")
        assertEquals(SuspendedOverrideReason.SUSPENDED_MAPPING_MISSING, suspendedOverrideReason(null, value))
        assertEquals(SuspendedOverrideReason.SUSPENDED_MAPPING_AMBIGUOUS, suspendedOverrideReason(identifier(getters = emptyList()), value))
    }
}
