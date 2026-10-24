package io.github.highwindbr.aaxp.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FullRecoveryBundleTest {
    private val build = "f".repeat(64)

    @Test fun preservesAbsentBaselineAndStringEmptyThroughRoundTrip() {
        val name = "SampleFeature__string"
        val map = mapOf(
            DynamicOverrideCodec.preferenceKey(name) to "STRING:",
            DynamicOverrideCodec.namePreferenceKey(name) to name
        )
        val decoded = FullRecoveryBundle.decode(
            FullRecoveryBundle.create(map, build, 180164204), build, 180164204)
        assertEquals(map, decoded.entries)
        assertEquals(1, decoded.normal)
        assertEquals(1, decoded.names)
    }

    @Test fun wrongBuildRejectsWithoutModifyingAnyPreferences() {
        val json = FullRecoveryBundle.create(emptyMap<String,String>(), build, 12)
        assertThrows(IllegalArgumentException::class.java) {
            FullRecoveryBundle.decode(json, "a".repeat(64), 12)
        }
    }

    @Test fun invalidChecksumAndKeyCannotBeImported() {
        val key = DynamicOverrideCodec.preferenceKey("Test__flag")
        val json = FullRecoveryBundle.create(mapOf(key to "BOOLEAN:true"), build, 1)
        val modified = JSONObject(json).put("sha256", "0".repeat(64)).toString()
        assertThrows(IllegalArgumentException::class.java) {
            FullRecoveryBundle.decode(modified, build, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FullRecoveryBundle.create(mapOf(key to "BOOLEAN:maybe"), build, 1)
        }
    }

    @Test fun unrelatedSettingsAreNeverExported() {
        val backup = FullRecoveryBundle.create(mapOf(
            "profile.active_slot.v1" to "active_profile_b_v3.json",
            "debug.enable" to "true"
        ), build, 22)
        val parsed = FullRecoveryBundle.decode(backup, build, 22)
        assertTrue(parsed.entries.isEmpty())
    }
}
