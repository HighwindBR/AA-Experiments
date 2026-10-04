package io.github.aaexperiments.core

import io.github.aaexperiments.discovery.*
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

    @Test fun missingAndAmbiguousMappingsAreSuspended() {
        val value = TypedOverride(DiscoveredType.BOOLEAN, "true")
        assertEquals(SuspendedOverrideReason.SUSPENDED_MAPPING_MISSING, suspendedOverrideReason(null, value))
        assertEquals(SuspendedOverrideReason.SUSPENDED_MAPPING_AMBIGUOUS, suspendedOverrideReason(identifier(getters = emptyList()), value))
    }
}
