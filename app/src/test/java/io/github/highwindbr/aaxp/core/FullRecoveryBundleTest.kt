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

    @Test fun completeBundlePreservesAdvancedAndExperimentalAbsentBaselines() {
        val advKey = "AceFeature__mode"
        val adv = AdvancedOverrideRecord(
            advKey, io.github.highwindbr.aaxp.discovery.DiscoveredType.LONG, "0",
            build, "Lexample/Getter;", "a", "()J", "sha", 3,
            previousOverride = null, previousName = null, active = true,
            createdAtMillis = 100L
        )
        val exp = ExperimentalBooleanSnapshot(
            build, "Lexample/PhoneTheme;", "b", "()Z", "sha", 4,
            value = false, previousOverride = null, previousName = null,
            active = true, createdAtMillis = 200L
        )
        val source = mapOf(
            AdvancedOverridePolicy.preferenceKey(advKey) to adv.encode(),
            ExperimentalBooleanPolicy.RECORD_KEY to exp.encode()
        )
        val json = FullRecoveryBundle.create(source, build, 180164204L)
        val bundle = FullRecoveryBundle.decode(json, build, 180164204L)
        assertEquals(1, bundle.advanced)
        assertEquals(1, bundle.experimental)
        assertEquals(1, bundle.activeAdvanced)
        assertEquals(1, bundle.activeExperimental)
        assertNull(AdvancedOverrideRecord.decode(bundle.entries.getValue(
            AdvancedOverridePolicy.preferenceKey(advKey)))?.previousOverride)
        assertNull(ExperimentalBooleanSnapshot.decode(bundle.entries.getValue(
            ExperimentalBooleanPolicy.RECORD_KEY))?.previousOverride)
    }

    @Test fun refusesOwnershipCollisionBetweenNormalAndAdvanced() {
        val flag = "AceFeature__mode"
        val record = AdvancedOverrideRecord(
            flag, io.github.highwindbr.aaxp.discovery.DiscoveredType.LONG, "0",
            build, "La;", "b", "()J", "hash", 2,
            previousOverride = null, previousName = null, active = false,
            createdAtMillis = 100L
        )
        assertThrows(IllegalArgumentException::class.java) {
            FullRecoveryBundle.create(mapOf(
                DynamicOverrideCodec.preferenceKey(flag) to "LONG:3",
                AdvancedOverridePolicy.preferenceKey(flag) to record.encode()
            ), build, 1L)
        }
    }

    @Test fun pausedAdvancedCanCoexistWithRestoredNormalValue() {
        val name = "AceFeature__mode"
        val record = AdvancedOverrideRecord(
            name, io.github.highwindbr.aaxp.discovery.DiscoveredType.LONG, "0",
            build, "La;", "b", "()J", "hash", 2,
            previousOverride = "LONG:3", previousName = name, active = false,
            createdAtMillis = 100L
        )
        val source = mapOf(
            DynamicOverrideCodec.preferenceKey(name) to "LONG:3",
            DynamicOverrideCodec.namePreferenceKey(name) to name,
            AdvancedOverridePolicy.preferenceKey(name) to record.encode()
        )
        val parsed = FullRecoveryBundle.decode(
            FullRecoveryBundle.create(source, build, 1), build, 1)
        assertEquals(1, parsed.normal)
        assertEquals(1, parsed.advanced)
        assertEquals(0, parsed.activeAdvanced)
    }

    @Test fun inactiveExperimentalRecordCanCoexistWithNormalBoolean() {
        val key = ExperimentalBooleanPolicy.KEY
        val baseline = ExperimentalBooleanSnapshot(
            build, "Lexample/PhoneTheme;", "b", "()Z", "sha", 4,
            value = false, previousOverride = "BOOLEAN:true", previousName = key,
            active = false, createdAtMillis = 200L
        )
        val source = mapOf(
            DynamicOverrideCodec.preferenceKey(key) to "BOOLEAN:true",
            ExperimentalBooleanPolicy.RECORD_KEY to baseline.encode()
        )
        val parsed = FullRecoveryBundle.decode(
            FullRecoveryBundle.create(source, build, 1), build, 1)
        assertEquals(1, parsed.normal)
        assertEquals(1, parsed.experimental)
        assertEquals(0, parsed.activeExperimental)
    }

    @Test fun activeExperimentalRejectsNormalBooleanOwnershipCollision() {
        val key = ExperimentalBooleanPolicy.KEY
        val active = ExperimentalBooleanSnapshot(
            build, "Lexample/PhoneTheme;", "b", "()Z", "sha", 4,
            value = false, previousOverride = "BOOLEAN:true", previousName = key,
            active = true, createdAtMillis = 200L
        )
        assertThrows(IllegalArgumentException::class.java) {
            FullRecoveryBundle.create(mapOf(
                DynamicOverrideCodec.preferenceKey(key) to "BOOLEAN:true",
                ExperimentalBooleanPolicy.RECORD_KEY to active.encode()
            ), build, 1)
        }
    }

    @Test fun activeOldBuildAdvancedIsOnlyHistoricalMetadata() {
        val key = "AceFeature__mode"
        val record = AdvancedOverrideRecord(
            key, io.github.highwindbr.aaxp.discovery.DiscoveredType.LONG, "0",
            "a".repeat(64), "La;", "b", "()J", "hash", 2,
            previousOverride = null, previousName = null, active = true,
            createdAtMillis = 100L
        )
        val source = mapOf(
            DynamicOverrideCodec.preferenceKey(key) to "LONG:3",
            AdvancedOverridePolicy.preferenceKey(key) to record.encode()
        )
        val parsed = FullRecoveryBundle.decode(
            FullRecoveryBundle.create(source, build, 1), build, 1)
        assertEquals(1, parsed.normal)
        assertEquals(0, parsed.activeAdvanced)
    }
}
