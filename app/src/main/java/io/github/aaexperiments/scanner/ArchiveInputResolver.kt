package io.github.aaexperiments.scanner

import java.io.File
import java.util.zip.ZipFile

object ArchiveInputResolver {
    data class ResolvedArchive(val base: File, val splits: List<File>)
    fun resolve(input: File, outputDirectory: File): ResolvedArchive {
        val directApk = ZipFile(input).use { zip -> zip.getEntry("AndroidManifest.xml") != null && zip.entries().asSequence().any { it.name.matches(Regex("classes(\\d*)\\.dex")) } }
        if (directApk) return ResolvedArchive(input, emptyList())
        outputDirectory.mkdirs()
        val extracted = mutableListOf<File>()
        ZipFile(input).use { zip ->
            val entries = zip.entries().asSequence().filter { !it.isDirectory && it.name.endsWith(".apk", true) }.toList()
            val baseEntry = entries.firstOrNull { it.name == "base.apk" || it.name.endsWith("/base.apk") }
                ?: error("Archive does not contain base.apk")
            (listOf(baseEntry) + entries.filter { it != baseEntry }).forEachIndexed { index, entry ->
                val output = File(outputDirectory, "${input.nameWithoutExtension}-${if (index == 0) "base" else "split-$index"}.apk")
                zip.getInputStream(entry).use { source -> output.outputStream().use(source::copyTo) }; extracted += output
            }
        }
        return ResolvedArchive(extracted.first(), extracted.drop(1))
    }
    fun baseApk(input: File, outputDirectory: File): File = resolve(input, outputDirectory).base
}
