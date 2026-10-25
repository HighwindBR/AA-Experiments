package io.github.highwindbr.aaxp.discovery

import io.github.highwindbr.aaxp.scanner.ArchiveInputResolver
import io.github.highwindbr.aaxp.test.fixtureRootOrSkip
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.json.JSONArray
import java.io.File

/** Explicit release gate. Run with -Daa.full.deep=true; omitted from routine unit runs. */
class FullDeepResolveDistributionTest {
    @Test fun stableBuildHasNoMassUnknownCallsiteBucket() {
        assumeTrue(System.getProperty("aa.full.deep") == "true" || System.getenv("AA_FULL_DEEP") == "true")
        val root = fixtureRootOrSkip("aa-17.8.663814.apkm")
        val archive = File(root, "fixtures/apks/aa-17.8.663814.apkm")
        // The previous untracked outputs/discovery inventory is not shipped
        // to a clean Actions checkout; assumeTrue made this release gate
        // silently skip. The committed compatibility oracle is immutable,
        // contains the exact 919 beta3 editable keys, and is SHA-256 verified
        // by the private workflow. Missing/corrupt input must FAIL.
        val baselineFile = File(root, "fixtures/compatibility-17.8.663814.json")
        assertTrue("Frozen 17.8 compatibility reference missing", baselineFile.isFile)
        val rows = JSONArray(baselineFile.readText())
        val keys = (0 until rows.length())
            .map { rows.getJSONObject(it).getString("key") }.toSet()
        assertEquals("Frozen 17.8 baseline must contain 919 distinct keys", 919, keys.size)
        System.gc()
        val resolved = ArchiveInputResolver.resolve(archive, File(root, "app/build/tmp/full-deep-distribution"))
        val reasons = linkedMapOf<String, Int>()
        val limitations = linkedMapOf<String, Int>()
        val blockingUnknownKeys = mutableListOf<String>()
        var completed = 0

        val inventory = DexCatalogAnalyzer().analyze(
            resolved.base,
            resolved.splits,
            deepResolveKeys = keys,
            streamDeepResolveResults = true,
            onDeepResolveResult = { identifier, _, _ ->
                completed++
                val reason = identifier.metadata["targetedResolutionReason"]
                val limitation = identifier.metadata["valueFlowLimitation"]
                reason?.let { reasons[it] = reasons.getOrDefault(it, 0) + 1 }
                limitation?.let { limitations[it] = limitations.getOrDefault(it, 0) + 1 }
                if (reason == "VALUE_FLOW_NOT_PROVEN" && limitation == "UNKNOWN") blockingUnknownKeys += identifier.key
            },
        )

        println("FULL_DEEP completed=$completed reasons=$reasons limitations=$limitations blockingUnknown=$blockingUnknownKeys")
        assertTrue(inventory.errors.joinToString(), inventory.errors.isEmpty())
        assertTrue("Only $completed of ${keys.size} editable keys completed", completed == keys.size)
        assertEquals("Frozen 17.8 fixture editable-key count changed", 919, keys.size)
        assertTrue("Blocking UNKNOWN callsites remain: $blockingUnknownKeys", blockingUnknownKeys.isEmpty())
    }

}
