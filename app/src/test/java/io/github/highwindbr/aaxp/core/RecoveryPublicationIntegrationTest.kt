package io.github.highwindbr.aaxp.core

import io.github.highwindbr.aaxp.service.ProfileStorage
import io.github.highwindbr.aaxp.service.VerifiedProfilePublisher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * JVM fault-injection model of the complete-recovery critical section.
 * Does not touch Android SharedPreferences or a real LSPosed installation.
 */
class RecoveryPublicationIntegrationTest {
    private val fingerprint = "a".repeat(64)
    private val version = 178L
    private val flag = "RecoveryTest__enabled"
    private val managed = DynamicOverrideCodec.preferenceKey(flag)
    private val goodProfile =
        """{"schemaVersion":3,"buildFingerprintSha256":"$fingerprint","mappings":[]}"""
            .toByteArray()

    private class Storage(private var active: String? = "active_profile_a_v3.json") : ProfileStorage {
        val slots = mutableMapOf<String, ByteArray>()
        var corruptRead = false
        var rejectPointer = false
        override fun activeSlot(): String? = active
        override fun write(slot: String, content: ByteArray) { slots[slot] = content.copyOf() }
        override fun read(slot: String): ByteArray =
            if (corruptRead) byteArrayOf(0) else checkNotNull(slots[slot])
        override fun activate(slot: String): Boolean {
            if (rejectPointer) return false
            active = slot
            return true
        }
    }

    @Test fun failedPublicationPreservesRecoveredRecordsAndExistingActiveSlot() = runBlocking {
        val prefs = mutableMapOf(managed to "BOOLEAN:false", "debug.enabled" to "true")
        val bundle = FullRecoveryBundle.create(mapOf(managed to "BOOLEAN:true"), fingerprint, version)
        val storage = Storage().apply {
            slots["active_profile_a_v3.json"] = goodProfile.copyOf()
            rejectPointer = true
        }
        val coordinator = ProfileMutationCoordinator(ProfileRefreshGate())
        val (restored, first) = coordinator.executeWithResult(write = {
            val parsed = FullRecoveryBundle.decode(bundle, fingerprint, version)
            prefs.keys.filter(FullRecoveryBundle::managedKey).toList().forEach(prefs::remove)
            prefs.putAll(parsed.entries)
            parsed.normal
        }, publish = {
            runCatching { VerifiedProfilePublisher.publish(storage, goodProfile, fingerprint) }.isSuccess
        })
        assertEquals(1, restored)
        assertEquals(ProfileMutationCoordinator.Outcome.SAVED_NOT_PUBLISHED, first)
        assertEquals("BOOLEAN:true", prefs[managed])
        assertEquals("true", prefs["debug.enabled"])
        assertEquals("active_profile_a_v3.json", storage.activeSlot())
        assertArrayEquals(goodProfile, storage.slots["active_profile_a_v3.json"])

        // Reconnect/retry can publish the preserved state without another restore.
        storage.rejectPointer = false
        val retry = coordinator.execute(write = {
            assertEquals("BOOLEAN:true", prefs[managed])
        }, publish = {
            VerifiedProfilePublisher.publish(storage, goodProfile, fingerprint)
            true
        })
        assertEquals(ProfileMutationCoordinator.Outcome.PUBLISHED, retry)
        assertEquals("active_profile_b_v3.json", storage.activeSlot())
    }

    @Test fun damagedBackupIsRejectedBeforeAnyPreferenceChanges() = runBlocking {
        val prefs = mutableMapOf(managed to "BOOLEAN:false")
        val backup = FullRecoveryBundle.create(mapOf(managed to "BOOLEAN:true"), fingerprint, version)
            .replace("BOOLEAN:true", "BOOLEAN:false")
        val storage = Storage()
        var published = false
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                ProfileMutationCoordinator(ProfileRefreshGate()).execute(write = {
                    val parsed = FullRecoveryBundle.decode(backup, fingerprint, version)
                    prefs.clear()
                    prefs.putAll(parsed.entries)
                }, publish = {
                    published = true
                    VerifiedProfilePublisher.publish(storage, goodProfile, fingerprint)
                    true
                })
            }
        }
        assertEquals("BOOLEAN:false", prefs[managed])
        assertFalse(published)
        assertEquals("active_profile_a_v3.json", storage.activeSlot())
    }

    @Test fun corruptReadbackCannotActivateCandidateProfile() = runBlocking {
        val storage = Storage().apply {
            slots["active_profile_a_v3.json"] = goodProfile.copyOf()
            corruptRead = true
        }
        val outcome = ProfileMutationCoordinator(ProfileRefreshGate()).execute(
            write = {},
            publish = {
                runCatching { VerifiedProfilePublisher.publish(storage, goodProfile, fingerprint) }
                    .isSuccess
            }
        )
        assertEquals(ProfileMutationCoordinator.Outcome.SAVED_NOT_PUBLISHED, outcome)
        assertEquals("active_profile_a_v3.json", storage.activeSlot())
        assertArrayEquals(goodProfile, storage.slots["active_profile_a_v3.json"])
    }
}
