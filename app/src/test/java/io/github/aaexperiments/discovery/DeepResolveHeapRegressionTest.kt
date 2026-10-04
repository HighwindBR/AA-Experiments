package io.github.aaexperiments.discovery

import io.github.aaexperiments.scanner.ArchiveInputResolver
import io.github.aaexperiments.test.fixtureRootOrSkip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Run with -Daa.test.heap=256m to validate a real three-key deep-resolve batch. */
class DeepResolveHeapRegressionTest {
    @Test fun streamingBatchPersistsWithoutRetainingResults() {
        val root = fixtureRootOrSkip("aa-17.8.663814.apkm")
        val archive = File(root, "fixtures/apks/aa-17.8.663814.apkm")
        val resolved = ArchiveInputResolver.resolve(archive, File(root, "app/build/tmp/deep-resolve-heap-regression"))
        val keys = linkedSetOf(
            "AapmEducationFeature__aapm_hun_enabled",
            "LauncherShortcuts__enabled",
            "LauncherShortcuts__assistant_shortcut_enabled",
        )
        val persisted = mutableListOf<DiscoveredIdentifier>()

        val inventory = DexCatalogAnalyzer().analyze(
            resolved.base,
            resolved.splits,
            deepResolveKeys = keys,
            streamDeepResolveResults = true,
            onDeepResolveResult = { identifier, _, _ -> persisted += identifier },
        )

        assertTrue(inventory.identifiers.isEmpty())
        assertEquals(keys, persisted.map { it.key }.toSet())
        persisted.forEach { identifier ->
            assertEquals("true", identifier.metadata["deepResolveAttempted"])
            assertEquals("512", identifier.metadata["deepResolveBudget"])
            assertTrue(identifier.metadata["deepResolveStatus"] in setOf("RESOLVED", "STILL_INCONCLUSIVE"))
        }
        assertTrue(inventory.errors.isEmpty())
    }
}
