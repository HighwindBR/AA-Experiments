package io.github.highwindbr.aaxp.service

import io.github.highwindbr.aaxp.runtime.ProfileSlotPolicy
import org.junit.Assert.*
import org.junit.Test

class VerifiedProfilePublisherTest {
    private val good = """
        {"schemaVersion":3,"buildFingerprintSha256":"build","mappings":[]}
    """.trimIndent().toByteArray()

    private class FakeStorage(private var pointer: String? = null) : ProfileStorage {
        val files = mutableMapOf<String, ByteArray>()
        var interruptWrite = false
        var corruptRead = false
        var refuseCommit = false
        override fun activeSlot() = pointer
        override fun write(slot: String, content: ByteArray) {
            if (interruptWrite) {
                files[slot] = content.copyOfRange(0, content.size / 2)
                throw IllegalStateException("Write interrupted")
            }
            files[slot] = content.copyOf()
        }
        override fun read(slot: String): ByteArray =
            if (corruptRead) byteArrayOf(1) else checkNotNull(files[slot])
        override fun activate(slot: String): Boolean {
            if (refuseCommit) return false
            pointer = slot
            return true
        }
    }

    @Test fun firstPublishDoesNotModifyLegacy() {
        val storage = FakeStorage()
        storage.files[ProfileSlotPolicy.LEGACY_FILE] = "old".toByteArray()
        VerifiedProfilePublisher.publish(storage, good, "build")
        assertEquals(ProfileSlotPolicy.SLOT_A, storage.activeSlot())
        assertArrayEquals("old".toByteArray(), storage.files[ProfileSlotPolicy.LEGACY_FILE])
    }

    @Test fun interruptedWriteLeavesLiveSlotUntouched() {
        val storage = FakeStorage(ProfileSlotPolicy.SLOT_A)
        storage.files[ProfileSlotPolicy.SLOT_A] = good.copyOf()
        storage.interruptWrite = true
        assertThrows(IllegalStateException::class.java) {
            VerifiedProfilePublisher.publish(storage, good, "build")
        }
        assertEquals(ProfileSlotPolicy.SLOT_A, storage.activeSlot())
        assertArrayEquals(good, storage.files[ProfileSlotPolicy.SLOT_A])
    }

    @Test fun readbackMismatchNeverSwitchesPointer() {
        val storage = FakeStorage(ProfileSlotPolicy.SLOT_B)
        storage.files[ProfileSlotPolicy.SLOT_B] = good.copyOf()
        storage.corruptRead = true
        assertThrows(IllegalArgumentException::class.java) {
            VerifiedProfilePublisher.publish(storage, good, "build")
        }
        assertEquals(ProfileSlotPolicy.SLOT_B, storage.activeSlot())
    }

    @Test fun pointerCommitFailurePreservesExistingProfile() {
        val storage = FakeStorage(ProfileSlotPolicy.SLOT_A)
        storage.files[ProfileSlotPolicy.SLOT_A] = good.copyOf()
        storage.refuseCommit = true
        assertThrows(IllegalStateException::class.java) {
            VerifiedProfilePublisher.publish(storage, good, "build")
        }
        assertEquals(ProfileSlotPolicy.SLOT_A, storage.activeSlot())
    }

    @Test fun secondSuccessfulPublishSwitchesToOtherSlot() {
        val storage = FakeStorage()
        VerifiedProfilePublisher.publish(storage, good, "build")
        VerifiedProfilePublisher.publish(storage, good, "build")
        assertEquals(ProfileSlotPolicy.SLOT_B, storage.activeSlot())
        assertArrayEquals(good, storage.files[ProfileSlotPolicy.SLOT_A])
        assertArrayEquals(good, storage.files[ProfileSlotPolicy.SLOT_B])
    }

    @Test fun wrongBuildIsNotCommitted() {
        val storage = FakeStorage()
        assertThrows(IllegalArgumentException::class.java) {
            VerifiedProfilePublisher.publish(storage, good, "otherBuild")
        }
        assertNull(storage.activeSlot())
        // The valid staged bytes cannot be activated by a new process without
        // an explicit committed pointer.
        assertArrayEquals(arrayOf(ProfileSlotPolicy.LEGACY_FILE),
            ProfileSlotPolicy.readOrder(storage.activeSlot()))
    }
}
