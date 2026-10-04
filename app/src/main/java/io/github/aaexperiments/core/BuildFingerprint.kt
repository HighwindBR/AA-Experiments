package io.github.aaexperiments.core

import java.io.File
import java.security.MessageDigest

data class PackageBuildIdentity(
    val fingerprintSha256: String,
    val baseSha256: String,
    val versionCode: Long,
    val artifactSha256: Map<String, String>,
)

/** Exact identity of the installed package set, not merely its base APK. */
object BuildFingerprint {
    @JvmStatic
    fun computeFromPaths(versionCode: Long, basePath: String, splitPaths: Array<String>?): PackageBuildIdentity =
        compute(versionCode, File(basePath), splitPaths.orEmpty().map(::File))

    @JvmStatic
    fun compute(versionCode: Long, base: File, splits: List<File>): PackageBuildIdentity {
        val artifacts = linkedMapOf("base" to sha256(base))
        val duplicateNames = mutableMapOf<String, Int>()
        splits.sortedWith(compareBy<File> { it.name }.thenBy { it.absolutePath }).forEach { split ->
            val ordinal = duplicateNames.getOrDefault(split.name, 0)
            duplicateNames[split.name] = ordinal + 1
            val suffix = if (ordinal == 0) "" else "#$ordinal"
            artifacts["split:${split.name}$suffix"] = sha256(split)
        }
        val canonical = buildString {
            append("versionCode=").append(versionCode).append('\n')
            artifacts.forEach { (name, hash) -> append(name).append('=').append(hash).append('\n') }
        }
        return PackageBuildIdentity(
            fingerprintSha256 = sha256(canonical.toByteArray(Charsets.UTF_8)),
            baseSha256 = artifacts.getValue("base"),
            versionCode = versionCode,
            artifactSha256 = artifacts,
        )
    }

    private fun sha256(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        hex(digest.digest())
    }

    private fun sha256(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
}
