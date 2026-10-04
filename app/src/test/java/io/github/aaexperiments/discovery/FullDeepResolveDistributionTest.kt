package io.github.aaexperiments.discovery

import io.github.aaexperiments.scanner.ArchiveInputResolver
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.json.JSONObject
import java.io.File

/** Explicit release gate. Run with -Daa.full.deep=true; omitted from routine unit runs. */
class FullDeepResolveDistributionTest {
    @Test fun stableBuildHasNoMassUnknownCallsiteBucket() {
        assumeTrue(System.getProperty("aa.full.deep") == "true" || System.getenv("AA_FULL_DEEP") == "true")
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "fixtures/apks").isDirectory }
        val archive = File(root, "fixtures/apks/aa-17.8.663814.apkm")
        val baselineFile = File(root, "outputs/discovery/inventory-17.8.663814.json")
        assumeTrue(archive.isFile && baselineFile.isFile)
        val keys = editableKeys(baselineFile)
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

    private fun editableKeys(file: File): Set<String> {
        val marker = "\"identifiers\":["
        val keys = linkedSetOf<String>()
        file.bufferedReader().use { reader ->
            var matched = 0
            while (matched < marker.length) {
                val next = reader.read()
                require(next >= 0) { "identifiers array not found" }
                matched = if (next.toChar() == marker[matched]) matched + 1 else if (next.toChar() == marker[0]) 1 else 0
            }
            var depth = 0
            var inString = false
            var escaped = false
            var objectText: StringBuilder? = null
            while (true) {
                val raw = reader.read()
                if (raw < 0) break
                val ch = raw.toChar()
                if (depth == 0 && ch == ']') break
                if (depth == 0 && ch == '{') objectText = StringBuilder().append(ch)
                else objectText?.append(ch)
                if (escaped) { escaped = false; continue }
                if (inString && ch == '\\') { escaped = true; continue }
                if (ch == '"') { inString = !inString; continue }
                if (inString) continue
                if (ch == '{') depth++
                if (ch == '}') {
                    depth--
                    if (depth == 0) {
                        val value = objectText.toString()
                        if ("\"editable\":true" in value) keys += JSONObject(value).getString("key")
                        objectText = null
                    }
                }
            }
        }
        return keys
    }
}
