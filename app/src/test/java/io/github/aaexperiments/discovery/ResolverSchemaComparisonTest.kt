package io.github.aaexperiments.discovery

import io.github.aaexperiments.scanner.ArchiveInputResolver
import io.github.aaexperiments.test.fixtureRootOrSkip
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ResolverSchemaComparisonTest {
    @Test fun compareReferencedFieldHeuristicWithFieldToReturnProof() {
        val root = fixtureRootOrSkip("aa-17.8.663814.apkm")
        val archive = File(root, "fixtures/apks/aa-17.8.663814.apkm")
        assertTrue(archive.isFile)
        val resolved = ArchiveInputResolver.resolve(archive, File(root, "app/build/tmp/resolver-comparison"))
        val analyzer = DexCatalogAnalyzer()
        val legacy = analyzer.analyze(resolved.base, resolved.splits, DexCatalogAnalyzer.RegistryResolutionMode.LINEAR_DATA_FLOW_REFERENCE,
            DexCatalogAnalyzer.FieldReaderResolutionMode.REFERENCED_FIELD_LEGACY)
        val current = analyzer.analyze(resolved.base, resolved.splits, DexCatalogAnalyzer.RegistryResolutionMode.PROVEN_DATA_FLOW,
            DexCatalogAnalyzer.FieldReaderResolutionMode.PROVEN_RETURN_FLOW)
        val oldEditable = legacy.identifiers.filter { it.editable }.associateBy { it.key }
        val newEditable = current.identifiers.filter { it.editable }.associateBy { it.key }
        val promoted = (newEditable.keys - oldEditable.keys).sorted()
        val demoted = (oldEditable.keys - newEditable.keys).sorted()
        val report = JSONObject().apply {
            put("schemaVersion", 1); put("baseSha256", current.baseSha256)
            put("b1Editable", oldEditable.size); put("b2Editable", newEditable.size)
            put("confirmed", oldEditable.keys.intersect(newEditable.keys).size)
            put("promoted", JSONArray(promoted)); put("demoted", JSONArray(demoted))
            put("promotedCount", promoted.size); put("demotedCount", demoted.size)
        }
        File(root, "outputs/discovery/resolver-b1-to-b2.json").writeText(report.toString(2))
        assertTrue("The new resolver must retain a substantial confirmed baseline", oldEditable.keys.intersect(newEditable.keys).size >= 800)
    }
}
