package io.github.highwindbr.aaxp.core

import io.github.highwindbr.aaxp.discovery.*
import org.junit.Assert.*
import org.junit.Test

class AdvancedOverridePolicyTest {
    private fun candidate(key: String, type: DiscoveredType, editable: Boolean = false,
        resolution: ResolutionConfidence = ResolutionConfidence.UNIQUE_GETTER): DiscoveredIdentifier {
        val desc = AdvancedOverridePolicy.descriptor(type) ?: "()Ljava/lang/Object;"
        val getter = MethodFingerprint("classes.dex", "Lfoo/Bar;", "a", desc,
            type, emptyList(), 8, "opcodes", false, emptyList(), emptyList())
        return DiscoveredIdentifier(key, IdentifierNamespace.FENOTYPE_FLAG, type, "0",
            emptyList(), listOf(getter), resolution, null, editable)
    }

    @Test fun acceptsKnownTypedFenotypeGetterWithoutBypassingSafety() {
        val id = candidate("SomeFeature__long_control", DiscoveredType.LONG)
        assertNull(AdvancedOverridePolicy.issue(id))
        assertNotNull(AdvancedOverridePolicy.issue(id.copy(editable = true)))
        assertNotNull(AdvancedOverridePolicy.issue(candidate(
            "AppValidation__should_bypass_validation", DiscoveredType.BOOLEAN)))
        assertNotNull(AdvancedOverridePolicy.issue(candidate(
            "SomeFeature__long_control", DiscoveredType.LONG,
            resolution = ResolutionConfidence.AMBIGUOUS)))
        assertNotNull(AdvancedOverridePolicy.issue(candidate(
            "SomeFeature__config", DiscoveredType.BYTES)))
    }

    @Test fun reviewedParkedTimingPassesStructuralEligibilityButSpeedRemainsBlocked() {
        val parkedTiming = candidate(
            "VideoTimingControllerFeature__video_timing_controller_min_delay_parked_exp_ms",
            DiscoveredType.LONG
        )
        assertNull(AdvancedOverridePolicy.issue(parkedTiming))
        assertNotNull(AdvancedOverridePolicy.issue(candidate(
            "CarSensorParameters__max_parked_speed_gps_sensor", DiscoveredType.DOUBLE)))
        assertNotNull(AdvancedOverridePolicy.issue(candidate(
            "VideoTimingControllerFeature__unknown_parked_exp_flag", DiscoveredType.LONG)))
        assertNotNull(AdvancedOverridePolicy.issue(parkedTiming.copy(
            getterCandidates = emptyList()
        )))
        assertNotNull(AdvancedOverridePolicy.issue(parkedTiming.copy(editable = true)))
    }

    @Test fun roundTripsPreviousAbsenceAndValidatesFingerprintAndType() {
        for ((type, value) in listOf(DiscoveredType.BOOLEAN to "false",
            DiscoveredType.LONG to "9223372036854775807",
            DiscoveredType.DOUBLE to "1.25", DiscoveredType.STRING to "Hello:world")) {
            val id = candidate("Feature__test_${type.name}", type)
            val record = AdvancedOverridePolicy.newRecord(id, "build", value, null, null, 123)
            val decoded = AdvancedOverrideRecord.decode(record.encode())!!
            assertEquals(value, decoded.value)
            assertNull(decoded.previousOverride)
            assertTrue(AdvancedOverridePolicy.matches(id, decoded, "build"))
            assertFalse(AdvancedOverridePolicy.matches(id, decoded, "other"))
            assertFalse(AdvancedOverridePolicy.matches(
                id.copy(getterCandidates = id.getterCandidates.map { it.copy(opcodeSha256 = "changed") }),
                decoded, "build"))
        }
    }

    @Test fun rejectsInvalidValuesAndPreservesOriginalSnapshot() {
        val id = candidate("Feature__parameter", DiscoveredType.LONG)
        assertThrows(Exception::class.java) {
            AdvancedOverridePolicy.newRecord(id, "build", "overflow", null, null, 1)
        }
        val record = AdvancedOverridePolicy.newRecord(id, "build", "1", "LONG:7", id.key, 1)
        assertEquals("LONG:7", AdvancedOverrideRecord.decode(record.copy(value = "2").encode())?.previousOverride)
        assertNull(AdvancedOverrideRecord.decode("{broken"))
        assertEquals(AdvancedOverridePolicy.preferenceKey(id.key),
            AdvancedOverridePolicy.preferenceKey(id.key))
    }
}
