package io.github.highwindbr.aaxp.core

import io.github.highwindbr.aaxp.discovery.*
import org.junit.Assert.*
import org.junit.Test

class ExperimentalBooleanPolicyTest {
    private val key = ExperimentalBooleanPolicy.KEY
    private fun identifier(default: String? = "true", type: DiscoveredType = DiscoveredType.BOOLEAN) =
        DiscoveredIdentifier(key, IdentifierNamespace.FENOTYPE_FLAG, type, default, emptyList(),
            listOf(MethodFingerprint("classes.dex", "Ladic;", "d", "()Z", DiscoveredType.BOOLEAN,
                emptyList(), 8, "opcode-sha", false, emptyList(), emptyList())),
            ResolutionConfidence.UNIQUE_GETTER, null, true)

    @Test fun snapshotRestoresAbsenceInsteadOfWritingCompiledDefault() {
        val snap = ExperimentalBooleanPolicy.snapshot(identifier(), "build", false, null, null, 42)
        val restored = ExperimentalBooleanSnapshot.decode(snap.encode())!!
        assertNull(restored.previousOverride)
        assertNull(restored.previousName)
        assertFalse(restored.value)
        assertTrue(restored.active)
        assertTrue(ExperimentalBooleanPolicy.matches(identifier(), restored, "build"))
        assertFalse(ExperimentalBooleanPolicy.matches(identifier(), restored, "new-build"))
    }

    @Test fun snapshotPreservesExistingOverrideAndTarget() {
        val snap = ExperimentalBooleanPolicy.snapshot(identifier(), "build", true, "BOOLEAN:false", key, 1234)
        val restored = ExperimentalBooleanSnapshot.decode(snap.encode())!!
        assertEquals("BOOLEAN:false", restored.previousOverride)
        assertEquals(key, restored.previousName)
        assertEquals(1234L, restored.createdAtMillis)
        assertFalse(ExperimentalBooleanPolicy.matches(
            identifier().copy(getterCandidates = identifier().getterCandidates.map { it.copy(opcodeSha256 = "other") }),
            restored, "build"))
        assertNull(ExperimentalBooleanSnapshot.decode("{broken"))
    }

    @Test fun rejectsWrongTypeOrUnknownDefault() {
        assertNotNull(ExperimentalBooleanPolicy.issue(identifier(default = null)))
        assertNotNull(ExperimentalBooleanPolicy.issue(identifier(type = DiscoveredType.LONG)))
        assertNotNull(ExperimentalBooleanPolicy.issue(identifier().copy(key = "WirelessLatencyMonitor__warning_threshold_ms")))
    }
}
