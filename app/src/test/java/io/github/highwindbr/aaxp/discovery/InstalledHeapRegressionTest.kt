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
        // Frozen 919 baseline + exactly 23 reviewed, explainable beta4 changes.
        // A new or lost mapping still fails with the exact differing key names.
        FrozenBeta4EditableCohort.assertExact(inventory, root)
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
