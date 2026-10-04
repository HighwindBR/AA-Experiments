package io.github.aaexperiments.discovery

import io.github.aaexperiments.scanner.ArchiveInputResolver
import io.github.aaexperiments.test.fixtureRootOrSkip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import org.json.JSONArray

/** Mirrors a schema rebuild/new-build scan that has a compact previous getter baseline. */
class InstalledCompatibilityHeapRegressionTest {
    @Test fun installedCatalogWithPreviousGetterBaselineFitsSingleProcessHeap() {
        val root = fixtureRootOrSkip("aa-17.8.663814.apkm")
        val archive = File(root, "fixtures/apks/aa-17.8.663814.apkm")
        val resolved = ArchiveInputResolver.resolve(archive, File(root, "app/build/tmp/installed-compatibility-heap-regression"))

        val baseline = readBaseline(File(root, "fixtures/compatibility-17.8.663814.json"))
        assertEquals(919, baseline.size)

        val rebuilt = DexCatalogAnalyzer().analyze(
            resolved.base,
            resolved.splits,
            previousMappings = baseline,
            previousBaseSha256 = "676205e71a75f38e0565b9be7d462c296b14490cef9a3feda3791098e5baae04",
        )

        assertEquals(919, rebuilt.identifiers.count { it.editable })
        assertTrue(rebuilt.identifiers.any { it.metadata["rediscoveryStatus"] == "PRESERVED" })
        assertTrue(rebuilt.errors.isEmpty())
    }

    private fun readBaseline(file: File): List<DiscoveredIdentifier> {
        assumeTrue(file.isFile)
        val rows = JSONArray(file.readText())
        return List(rows.length()) { index ->
            val row = rows.getJSONObject(index)
            val getters = row.getJSONArray("getters")
            DiscoveredIdentifier(
                key = row.getString("key"),
                namespace = IdentifierNamespace.valueOf(row.getString("namespace")),
                inferredType = DiscoveredType.valueOf(row.getString("type")),
                compiledDefault = row.optString("compiledDefault").takeUnless { row.isNull("compiledDefault") },
                occurrences = emptyList(),
                getterCandidates = List(getters.length()) { methodIndex -> method(getters.getJSONObject(methodIndex)) },
                confidence = ResolutionConfidence.valueOf(row.getString("confidence")),
                reason = null,
                editable = true,
            )
        }
    }

    private fun method(value: org.json.JSONObject) = MethodFingerprint(
        dex = value.getString("dex"), className = value.getString("class"), methodName = value.getString("name"),
        descriptor = value.getString("descriptor"), returnType = DiscoveredType.valueOf(value.getString("returnType")),
        parameterTypes = value.getJSONArray("parameters").let { array -> List(array.length()) { array.getString(it) } },
        instructionCount = value.getInt("instructions"), opcodeSha256 = value.getString("opcodeSha256"),
        isStaticInitializer = value.getBoolean("clinit"),
        referencedFields = value.getJSONArray("fields").let { array -> List(array.length()) { array.getString(it) } },
        invokedMethods = value.getJSONArray("calls").let { array -> List(array.length()) { array.getString(it) } },
    )
}
