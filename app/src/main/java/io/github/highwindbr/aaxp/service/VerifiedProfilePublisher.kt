package io.github.highwindbr.aaxp.service

import io.github.highwindbr.aaxp.runtime.ProfileSlotPolicy
import org.json.JSONObject

/** A remote-file writer for which only activation of an already verified slot is durable. */
interface ProfileStorage {
    fun activeSlot(): String?
    fun write(slot: String, content: ByteArray)
    fun read(slot: String): ByteArray
    fun activate(slot: String): Boolean
}

/**
 * The active slot is never overwritten, including during migration from
 * active_profile_v2.json. If write, read-back, validation or pointer commit
 * fails, the old active pointer remains unchanged.
 */
object VerifiedProfilePublisher {
    fun publish(storage: ProfileStorage, bytes: ByteArray, buildFingerprint: String) {
        require(bytes.isNotEmpty() && bytes.size <= ProfileSlotPolicy.MAX_PROFILE_BYTES) {
            "Profile is empty or exceeds maximum allowed size"
        }
        val target = ProfileSlotPolicy.inactiveSlot(storage.activeSlot())
        storage.write(target, bytes)
        val verifiedBytes = storage.read(target)
        require(bytes.contentEquals(verifiedBytes)) { "Remote profile read-back mismatch" }
        val profile = JSONObject(String(verifiedBytes, Charsets.UTF_8))
        require(profile.getInt("schemaVersion") == 3 &&
            profile.getString("buildFingerprintSha256") == buildFingerprint &&
            profile.getJSONArray("mappings").length() >= 0) {
            "Remote profile verification failed"
        }
        check(storage.activate(target)) { "Could not activate verified profile slot" }
    }
}
