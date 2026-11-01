package io.github.highwindbr.aaxp.core

import io.github.highwindbr.aaxp.discovery.DiscoveredType
import org.junit.Assert.assertEquals
import org.junit.Test

class AdvancedOverridePresentationTest {
    private val record = AdvancedOverrideRecord("flag", DiscoveredType.LONG, "1", "build-a",
        "Target", "getter", "()J", "opcode", 2, null, null, true, 1L)

    @Test fun missingRecordIsDefault() {
        assertEquals(AdvancedOverrideStatus.DEFAULT, advancedOverrideStatus(null, "build-a"))
    }

    @Test fun currentBuildDistinguishesActiveAndPaused() {
        assertEquals(AdvancedOverrideStatus.ACTIVE, advancedOverrideStatus(record, "build-a"))
        assertEquals(AdvancedOverrideStatus.PAUSED, advancedOverrideStatus(record.copy(active = false), "build-a"))
    }

    @Test fun oldBuildIsSuspendedRegardlessOfSavedActiveIntent() {
        for (active in listOf(true, false)) {
            assertEquals(AdvancedOverrideStatus.SUSPENDED,
                advancedOverrideStatus(record.copy(active = active), "build-b"))
        }
    }

    @Test fun unavailableFingerprintNeverLooksActive() {
        for (build in listOf(null, "", " ")) {
            assertEquals(AdvancedOverrideStatus.SUSPENDED, advancedOverrideStatus(record, build))
        }
        assertEquals(AdvancedOverrideStatus.SUSPENDED,
            advancedOverrideStatus(record.copy(buildFingerprint = ""), ""))
    }
}
