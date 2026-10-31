package io.github.highwindbr.aaxp.scanner

import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveInputResolverTest {
    private val directory = Files.createTempDirectory("aaxp-archive-").toFile()

    @After fun cleanup() { directory.deleteRecursively() }

    private fun archive(vararg entries: Pair<String, ByteArray>): File {
        val target = File(directory, "sample.apkm")
        ZipOutputStream(target.outputStream()).use { zip ->
            entries.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return target
    }

    @Test fun boundedDocumentCopyAcceptsValidInput() {
        val output = File(directory, "copy.apkm")
        val input = ByteArray(16) { it.toByte() }
        assertEquals(16L, ArchiveInputResolver.copyInputBounded(
            ByteArrayInputStream(input), output, 16L))
        assertArrayEquals(input, output.readBytes())
    }

    @Test fun oversizedDocumentIsRejectedAndDeleted() {
        val output = File(directory, "copy.apkm")
        assertThrows(IllegalArgumentException::class.java) {
            ArchiveInputResolver.copyInputBounded(
                ByteArrayInputStream(ByteArray(33)), output, 32L)
        }
        assertFalse(output.exists())
    }

    @Test fun emptyDocumentIsRejectedAndDeleted() {
        val output = File(directory, "copy.apkm")
        assertThrows(IllegalArgumentException::class.java) {
            ArchiveInputResolver.copyInputBounded(ByteArrayInputStream(byteArrayOf()), output)
        }
        assertFalse(output.exists())
    }

    @Test fun directApkRequiresManifestAndDexAndStaysUnextracted() {
        val input = archive("AndroidManifest.xml" to byteArrayOf(1),
            "classes.dex" to byteArrayOf(2))
        val output = File(directory, "unpacked")
        val resolved = ArchiveInputResolver.resolve(input, output)
        assertEquals(input, resolved.base)
        assertTrue(resolved.splits.isEmpty())
        assertFalse(output.exists())
    }

    @Test fun validApkmExtractsBaseFirstAndSplitInOrder() {
        val input = archive("config.arm64.apk" to byteArrayOf(2),
            "base.apk" to byteArrayOf(1))
        val resolved = ArchiveInputResolver.resolve(input, File(directory, "unpacked"))
        assertArrayEquals(byteArrayOf(1), resolved.base.readBytes())
        assertEquals(1, resolved.splits.size)
        assertArrayEquals(byteArrayOf(2), resolved.splits.single().readBytes())
    }

    @Test fun missingBaseCannotCreateAProfile() {
        val input = archive("config.arm64.apk" to byteArrayOf(2))
        val output = File(directory, "unpacked")
        assertThrows(IllegalStateException::class.java) {
            ArchiveInputResolver.resolve(input, output)
        }
        assertTrue(output.listFiles().isNullOrEmpty())
    }

    @Test fun tooManyApksRejectedBeforeExtraction() {
        val entries = (1..ArchiveInputResolver.MAX_APK_ENTRIES).map {
            "split-$it.apk" to byteArrayOf(1)
        } + ("base.apk" to byteArrayOf(2))
        val input = archive(*entries.toTypedArray())
        val output = File(directory, "unpacked")
        assertThrows(IllegalArgumentException::class.java) {
            ArchiveInputResolver.resolve(input, output)
        }
        assertTrue(output.listFiles().isNullOrEmpty())
    }
}
