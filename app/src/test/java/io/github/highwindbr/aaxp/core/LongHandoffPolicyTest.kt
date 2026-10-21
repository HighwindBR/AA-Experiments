package io.github.highwindbr.aaxp.core

import io.github.highwindbr.aaxp.discovery.*
import org.junit.Assert.*
import org.junit.Test

class LongHandoffPolicyTest {
    private fun flag() = DiscoveredIdentifier(
        LongHandoffPolicy.KEY, IdentifierNamespace.FENOTYPE_FLAG,
        DiscoveredType.LONG, "0", emptyList(),
        listOf(MethodFingerprint("classes.dex", "Laczh;", "l", "()J",
            DiscoveredType.LONG, emptyList(), 17, "verified_opcode_hash",
            false, emptyList(), emptyList())),
        ResolutionConfidence.UNIQUE_GETTER, null, true
    )

    @Test fun onlyInspectedAssistantLongGetterQualifies() {
        val id = flag()
        assertNull(LongHandoffPolicy.issue(id, LongHandoffPolicy.EXPECTED_BASE))
        assertNotNull(LongHandoffPolicy.issue(id, "different"))
        assertNotNull(LongHandoffPolicy.issue(id.copy(key = "Other__value"), LongHandoffPolicy.EXPECTED_BASE))
        assertNotNull(LongHandoffPolicy.issue(id.copy(inferredType = DiscoveredType.DOUBLE), LongHandoffPolicy.EXPECTED_BASE))
        assertNotNull(LongHandoffPolicy.issue(id.copy(getterCandidates =
            listOf(id.getterCandidates.single().copy(methodName = "a"))), LongHandoffPolicy.EXPECTED_BASE))
        assertNotNull(LongHandoffPolicy.issue(id.copy(editable = false), LongHandoffPolicy.EXPECTED_BASE))
        assertNotNull(AdvancedOverridePolicy.issue(id))
    }

    @Test fun preservesExactNormalBaselineWhileSwitchingOnlyHookOwnership() {
        val id = flag()
        val record = LongHandoffPolicy.record(id, "build", LongHandoffPolicy.EXPECTED_BASE,
            "LONG:3", id.key, 123L)
        assertEquals("LONG:3", record.previousOverride)
        assertEquals("3", record.value)
        assertEquals(LongHandoffPolicy.MODE, record.handoffMode)
        assertTrue(LongHandoffPolicy.baselineIntact(record, "LONG:3", id.key))
        val restored = AdvancedOverrideRecord.decode(record.encode())!!
        assertEquals(record, restored)
        assertTrue(AdvancedOverridePolicy.matches(id, restored, "build", LongHandoffPolicy.EXPECTED_BASE))
        assertFalse(AdvancedOverridePolicy.matches(id, restored, "other", LongHandoffPolicy.EXPECTED_BASE))
        assertFalse(AdvancedOverridePolicy.matches(id, restored, "build", "otherbase"))
        assertFalse(AdvancedOverridePolicy.matches(id.copy(getterCandidates = listOf(
            id.getterCandidates.single().copy(opcodeSha256 = "new"))),
            restored, "build", LongHandoffPolicy.EXPECTED_BASE))
        assertFalse(LongHandoffPolicy.baselineIntact(restored, "LONG:0", id.key))
        assertFalse(LongHandoffPolicy.baselineIntact(restored, "LONG:3", "wrong"))
        assertTrue(LongHandoffPolicy.baselineIntact(restored.copy(active = false), "LONG:3", id.key))
    }

    @Test fun refusesTransferUnlessTheRealOverrideIsThree() {
        val id = flag()
        assertThrows(IllegalArgumentException::class.java) {
            LongHandoffPolicy.record(id, "build", LongHandoffPolicy.EXPECTED_BASE,
                null, id.key, 1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LongHandoffPolicy.record(id, "build", LongHandoffPolicy.EXPECTED_BASE,
                "LONG:4", id.key, 1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LongHandoffPolicy.record(id, "build", LongHandoffPolicy.EXPECTED_BASE,
                "LONG:3", null, 1L)
        }
        val good = LongHandoffPolicy.record(id, "build", LongHandoffPolicy.EXPECTED_BASE,
            "LONG:3", id.key, 1L)
        assertNull(AdvancedOverrideRecord.decode(good.copy(value = "4").encode()))
        assertNull(AdvancedOverrideRecord.decode(good.copy(previousOverride = "LONG:0").encode()))
    }
}
