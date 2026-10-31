package io.github.highwindbr.aaxp.scanner

import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Archive imports are untrusted and can contain highly compressed APK entries.
 * Bound both the initial document copy and the extracted bytes; the caller owns
 * the temporary session directory and deletes it on either success or failure.
 */
object ArchiveInputResolver {
    const val MAX_ARCHIVE_BYTES = 256L * 1024 * 1024
    const val MAX_EXTRACTED_APK_BYTES = 256L * 1024 * 1024
    const val MAX_TOTAL_EXTRACTED_BYTES = 512L * 1024 * 1024
    const val MAX_APK_ENTRIES = 32

    data class ResolvedArchive(val base: File, val splits: List<File>)

    /** Copy a content-provider document without ever accepting an unbounded stream. */
    fun copyInputBounded(input: InputStream, output: File, limit: Long = MAX_ARCHIVE_BYTES): Long {
        require(limit > 0) { "Invalid import byte limit" }
        var written = 0L
        try {
            output.outputStream().buffered().use { sink ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (n == 0) continue
                    require(n.toLong() <= limit - written) { "Archive exceeds import size limit" }
                    sink.write(buffer, 0, n)
                    written += n
                }
            }
            require(written > 0) { "Archive is empty" }
            return written
        } catch (error: Throwable) {
            output.delete()
            throw error
        }
    }

    fun resolve(input: File, outputDirectory: File): ResolvedArchive {
        require(input.isFile && input.length() in 1..MAX_ARCHIVE_BYTES) {
            "Archive is empty or exceeds import size limit"
        }
        val directApk = ZipFile(input).use { zip ->
            zip.getEntry("AndroidManifest.xml") != null &&
                zip.entries().asSequence().any { it.name.matches(Regex("classes(\\d*)\\.dex")) }
        }
        if (directApk) return ResolvedArchive(input, emptyList())

        outputDirectory.mkdirs()
        val extracted = mutableListOf<File>()
        try {
            ZipFile(input).use { zip ->
                val entries = zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true) }
                    .toList()
                require(entries.isNotEmpty() && entries.size <= MAX_APK_ENTRIES) {
                    "APK set has no APKs or exceeds the split count limit"
                }
                val baseEntry = entries.firstOrNull { it.name == "base.apk" || it.name.endsWith("/base.apk") }
                    ?: error("Archive does not contain base.apk")
                var total = 0L
                (listOf(baseEntry) + entries.filter { it != baseEntry }).forEachIndexed { index, entry ->
                    require(entry.size < 0 || entry.size <= MAX_EXTRACTED_APK_BYTES) {
                        "APK entry exceeds extraction size limit"
                    }
                    val output = File(outputDirectory,
                        "${input.nameWithoutExtension}-${if (index == 0) "base" else "split-$index"}.apk")
                    // Register before opening the stream, so partial files are cleaned on failure.
                    extracted += output
                    zip.getInputStream(entry).use { source ->
                        val bytes = copyInputBounded(source, output,
                            minOf(MAX_EXTRACTED_APK_BYTES, MAX_TOTAL_EXTRACTED_BYTES - total))
                        total += bytes
                    }
                }
            }
            return ResolvedArchive(extracted.first(), extracted.drop(1))
        } catch (error: Throwable) {
            extracted.forEach(File::delete)
            throw error
        }
    }

    fun baseApk(input: File, outputDirectory: File): File = resolve(input, outputDirectory).base
}
