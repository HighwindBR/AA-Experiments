package io.github.highwindbr.aaxp.core

import io.github.highwindbr.aaxp.discovery.DiscoveredType
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Distinct from the legacy normal-only schema 3 import/export.
 * A complete snapshot contains ONLY scoped override/baseline preferences,
 * not developer tools, framework state, package files or device identifiers.
 * It is intentionally pinned to the exact build (including split APK hashes).
 */
object FullRecoveryBundle {
    const val SCHEMA = 1
    const val KIND = "AA_EXPERIMENTS_COMPLETE_RECOVERY"
    const val PACKAGE = "com.google.android.projection.gearhead"
    const val MAX_BYTES = 2 * 1024 * 1024
    const val MAX_ENTRIES = 4096
    private val hashSuffix = Regex("[0-9a-f]{40}")

    data class Parsed(val entries: Map<String, String>, val fingerprint: String,
        val versionCode: Long, val normal: Int, val names: Int, val advanced: Int,
        val experimental: Int, val activeAdvanced: Int, val activeExperimental: Int)

    fun managedKey(key: String): Boolean =
        key == ExperimentalBooleanPolicy.RECORD_KEY ||
            (key.startsWith(DynamicOverrideCodec.PREFIX) &&
                hashSuffix.matches(key.removePrefix(DynamicOverrideCodec.PREFIX))) ||
            (key.startsWith(DynamicOverrideCodec.NAME_PREFIX) &&
                hashSuffix.matches(key.removePrefix(DynamicOverrideCodec.NAME_PREFIX))) ||
            (key.startsWith(AdvancedOverridePolicy.PREFIX) &&
                hashSuffix.matches(key.removePrefix(AdvancedOverridePolicy.PREFIX)))

    fun create(all: Map<String, *>, fingerprint: String, versionCode: Long): String {
        require(fingerprint.matches(Regex("[0-9a-fA-F]{64}"))) { "Invalid build fingerprint" }
        val entries = all.filterKeys(::managedKey).mapValues { (_, value) ->
            require(value is String) { "Invalid override preference type" }
            value
        }
        require(entries.size <= MAX_ENTRIES) { "Too many recovery records" }
        validateEntries(entries, fingerprint)
        val values = JSONObject()
        entries.toSortedMap().forEach { (key, value) -> values.put(key, value) }
        val output = JSONObject().apply {
            put("kind", KIND)
            put("schemaVersion", SCHEMA)
            put("package", PACKAGE)
            put("buildFingerprintSha256", fingerprint)
            put("versionCode", versionCode)
            put("entries", values)
            put("sha256", digest(entries))
        }.toString(2)
        require(output.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Recovery bundle is too large" }
        return output
    }

    fun decode(text: String, fingerprint: String, versionCode: Long): Parsed {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Recovery file too large" }
        val root = JSONObject(text)
        require(root.getString("kind") == KIND && root.getInt("schemaVersion") == SCHEMA &&
            root.getString("package") == PACKAGE) { "Not an AA Experiments complete recovery bundle" }
        require(root.getString("buildFingerprintSha256").equals(fingerprint, ignoreCase = true) &&
            root.getLong("versionCode") == versionCode) {
            "Recovery bundle belongs to a different Android Auto build; no records applied"
        }
        val values = root.getJSONObject("entries")
        val names = buildList { val keys = values.keys(); while (keys.hasNext()) add(keys.next()) }
        require(names.size <= MAX_ENTRIES) { "Too many recovery records" }
        val entries = names.associateWith { values.getString(it) }
        require(entries.keys.all(::managedKey)) { "Recovery bundle contains unexpected preference keys" }
        require(digest(entries).equals(root.getString("sha256"), true)) {
            "Recovery bundle checksum mismatch"
        }
        validateEntries(entries, fingerprint)
        val advanced = entries.filterKeys { it.startsWith(AdvancedOverridePolicy.PREFIX) }
            .values.map { checkNotNull(AdvancedOverrideRecord.decode(it)) }
        val experimental = entries[ExperimentalBooleanPolicy.RECORD_KEY]
            ?.let { checkNotNull(ExperimentalBooleanSnapshot.decode(it)) }
        return Parsed(entries, fingerprint, versionCode,
            entries.keys.count { it.startsWith(DynamicOverrideCodec.PREFIX) },
            entries.keys.count { it.startsWith(DynamicOverrideCodec.NAME_PREFIX) },
            advanced.size, if (experimental != null) 1 else 0,
            advanced.count { it.active && it.buildFingerprint == fingerprint },
            if (experimental?.active == true && experimental.buildFingerprint == fingerprint) 1 else 0)
    }

    private fun validateEntries(entries: Map<String, String>, currentBuild: String) {
        entries.forEach { (key, value) ->
            when {
                key.startsWith(DynamicOverrideCodec.PREFIX) ->
                    require(DynamicOverrideCodec.deserialize(value) != null) { "Malformed normal override" }
                key.startsWith(DynamicOverrideCodec.NAME_PREFIX) ->
                    require(DynamicOverrideCodec.namePreferenceKey(value) == key) { "Incorrect name metadata" }
                key.startsWith(AdvancedOverridePolicy.PREFIX) -> {
                    val record = checkNotNull(AdvancedOverrideRecord.decode(value)) { "Invalid Advanced record" }
                    require(AdvancedOverridePolicy.preferenceKey(record.key) == key) { "Advanced key mismatch" }
                    val normal = DynamicOverrideCodec.preferenceKey(record.key)
                    // Paused/historical Advanced snapshots may legitimately
                    // coexist with a live normal value. Only an active
                    // current-build Advanced hook can contend for ownership.
                    require(!(record.active &&
                        record.buildFingerprint.equals(currentBuild, ignoreCase = true) &&
                        record.handoffMode == null && normal in entries)) {
                        "Normal/Advanced ownership collision"
                    }
                }
                key == ExperimentalBooleanPolicy.RECORD_KEY -> {
                    val record = checkNotNull(ExperimentalBooleanSnapshot.decode(value)) {
                        "Invalid Experimental baseline"
                    }
                    require(record.targetClass.isNotBlank() && record.opcodeFingerprint.isNotBlank() &&
                        record.instructionCount > 0) { "Incomplete Experimental baseline" }
                    // A paused or old-build Experimental baseline is
                    // metadata; it does not compete with a normal hook.
                    require(!(record.active &&
                        record.buildFingerprint.equals(currentBuild, ignoreCase = true) &&
                        DynamicOverrideCodec.preferenceKey(ExperimentalBooleanPolicy.KEY) in entries)) {
                        "Normal/Experimental ownership collision"
                    }
                }
                else -> error("Unsupported recovery key")
            }
        }
    }

    private fun digest(entries: Map<String, String>): String {
        val sha = MessageDigest.getInstance("SHA-256")
        // Length framing avoids any ambiguity caused by colons, NUL or newlines in STRING values.
        entries.toSortedMap().forEach { (key, value) ->
            for (part in arrayOf(key, value)) {
                val bytes = part.toByteArray(Charsets.UTF_8)
                sha.update(byteArrayOf((bytes.size ushr 24).toByte(),
                    (bytes.size ushr 16).toByte(), (bytes.size ushr 8).toByte(), bytes.size.toByte()))
                sha.update(bytes)
            }
        }
        return sha.digest().joinToString("") { "%02x".format(it) }
    }
}
