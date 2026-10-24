package io.github.highwindbr.aaxp.discovery

import io.github.highwindbr.aaxp.scanner.ArchiveInputResolver
import io.github.highwindbr.aaxp.test.fixtureRootOrSkip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Run with -Daa.test.heap=256m to mirror the manager process heap on the device. */
class InstalledHeapRegressionTest {
    @Test fun stableInstalledCatalogFitsSingleProcessHeap() {
        val root = fixtureRootOrSkip("aa-17.8.663814.apkm")
        val archive = File(root, "fixtures/apks/aa-17.8.663814.apkm")
        val resolved = ArchiveInputResolver.resolve(archive, File(root, "app/build/tmp/installed-heap-regression"))
        val inventory = DexCatalogAnalyzer().analyze(resolved.base, resolved.splits)
        // Keep the frozen 919 count as a blocking gate; diagnose differences
        // against the original editable-key fixture rather than blessing a new
        // count without reviewing the exact getter and policy changes.
        val editable = inventory.identifiers.filter { it.editable }
        if (editable.size != 919) {
            val baselineFile = File(root, "fixtures/compatibility-17.8.663814.json")
            val rows = org.json.JSONArray(baselineFile.readText())
            val baseline = (0 until rows.length()).map { rows.getJSONObject(it).getString("key") }.toSet()
            val current = editable.map { it.key }.toSet()
            val added = editable.filter { it.key !in baseline }.sortedBy { it.key }
            val missing = baseline.filter { it !in current }.sorted()
            val diagnostic = buildString {
                append("Frozen beta3 keys=").append(baseline.size)
                append("; added=").append(added.size)
                append("; missing=").append(missing.size).append('\n')
                append("Newly editable keys (review safety policy and getter evidence):\n")
                added.forEach { id ->
                    val oldPolicyTokens = listOf("parked", "ssl", "tls")
                        .filter { it in id.key.lowercase() }.joinToString(",").ifEmpty { "none" }
                    append(" + ").append(id.key).append(" [").append(id.inferredType)
                        .append("] prior-sensitive-substring=").append(oldPolicyTokens)
                        .append(" confidence=").append(id.confidence)
                        .append(" resolution=").append(id.resolution)
                        .append('\n')
                }
                append("No longer editable keys:\n")
                missing.forEach { append(" - ").append(it).append('\n') }
            }
            assertEquals(diagnostic, 919, editable.size)
        }
        assertTrue(inventory.identifiers.size >= 54_000)
        assertTrue(inventory.errors.isEmpty())
        val earthStatus = inventory.identifiers.single { it.key == "CieloFeature__earth_status" }
        assertEquals("", earthStatus.compiledDefault)
        assertEquals("8", earthStatus.metadata["registryId"])
        assertEquals("INVOKE_ARGUMENT", earthStatus.metadata["defaultSource"])
        assertEquals("6", inventory.identifiers.single { it.key == "CieloFeature__earth_tilt" }.compiledDefault)
        inventory.identifiers.filter { it.key == " protocol=" || it.key == "http.protocol.allow-circular-redirects" }.forEach { suspicious ->
            assertTrue(!suspicious.editable)
            assertEquals(MappingResolution.CATALOG_ONLY, suspicious.resolution)
        }
    }
}
