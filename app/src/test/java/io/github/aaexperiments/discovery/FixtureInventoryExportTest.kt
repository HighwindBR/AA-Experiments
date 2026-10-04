package io.github.aaexperiments.discovery

import io.github.aaexperiments.scanner.ArchiveInputResolver
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FixtureInventoryExportTest {
    @Test fun exportThreeVersionCatalogAndDiffs() {
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }.first { File(it, "fixtures/apks").isDirectory }
        val temporary = File(root, "app/build/tmp/fixture-export").apply { mkdirs() }
        val output = File(root, "outputs/discovery").apply { mkdirs() }
        val versions = linkedMapOf(
            "16.9.666314" to File(root, "fixtures/apks/aa-16.9.666314.apkm"),
            "17.8.163744" to File(root, "fixtures/apks/aa-17.8.163744.apkm"),
            "17.8.663814" to File(root, "fixtures/apks/aa-17.8.663814.apkm")
        )
        assertTrue(versions.values.all(File::isFile))
        val analyzer = DexCatalogAnalyzer()
        val inventories = versions.mapValues { (version, archive) ->
            val resolved = ArchiveInputResolver.resolve(archive, File(temporary, version))
            analyzer.analyze(resolved.base, resolved.splits).also { File(output, "inventory-$version.json").writeText(InventoryJson.encode(it)) }
        }
        val summary = JSONObject().apply {
            put("schemaVersion", 1)
            put("versions", JSONObject().apply { inventories.forEach { (version, inventory) -> put(version, counts(inventory)) } })
            put("comparisons", JSONObject().apply {
                put("16.9.666314_to_17.8.163744", changes(CatalogDiff.compare(inventories.getValue("16.9.666314"), inventories.getValue("17.8.163744"))))
                put("16.9.666314_to_17.8.663814", changes(CatalogDiff.compare(inventories.getValue("16.9.666314"), inventories.getValue("17.8.663814"))))
                put("17.8.163744_to_17.8.663814", changes(CatalogDiff.compare(inventories.getValue("17.8.163744"), inventories.getValue("17.8.663814"))))
            })
            put("getterCompatibility", compatibility(inventories.getValue("16.9.666314"), inventories.getValue("17.8.663814")))
            put("resourceIdCompatibility", resourceCompatibility(inventories.getValue("16.9.666314"), inventories.getValue("17.8.663814")))
        }
        File(output, "comparison-summary.json").writeText(summary.toString(2))
        println(summary.toString())
    }

    private fun counts(value: DexInventory) = JSONObject().apply {
        put("baseSha256", value.baseSha256); put("dexCount", value.dexSha256.size); put("identifierCount", value.identifiers.size)
        put("editableCount", value.identifiers.count { it.editable }); put("errors", JSONArray(value.errors))
        put("byNamespace", JSONObject(value.identifiers.groupingBy { it.namespace.name }.eachCount()))
        put("byType", JSONObject(value.identifiers.groupingBy { it.inferredType.name }.eachCount()))
        put("byConfidence", JSONObject(value.identifiers.groupingBy { it.confidence.name }.eachCount()))
        put("byKind", JSONObject(value.identifiers.groupingBy { it.kind.name }.eachCount()))
        put("byResolution", JSONObject(value.identifiers.groupingBy { it.resolution.name }.eachCount()))
        put("byRuntimeConsumerStatus", JSONObject(value.identifiers.groupingBy { it.runtimeConsumerStatus.name }.eachCount()))
        put("consumerPaths", value.identifiers.sumOf { it.consumers.size })
        put("semanticsReviewed", value.identifiers.count { it.semanticsReviewed })
        put("byEditabilityReason", JSONObject(value.identifiers.mapNotNull { it.editabilityReason?.name }.groupingBy { it }.eachCount()))
    }
    private fun changes(value: List<IdentifierChange>) = JSONObject(value.groupingBy { it.kind.name }.eachCount())
    private fun compatibility(before: DexInventory, after: DexInventory): JSONObject {
        val old = before.identifiers.associateBy { it.key }; val new = after.identifiers.associateBy { it.key }
        val pairs = old.keys.intersect(new.keys).mapNotNull { key ->
            val a = old.getValue(key); val b = new.getValue(key)
            if (a.getterCandidates.size == 1 && b.getterCandidates.size == 1 && a.namespace != IdentifierNamespace.STRING_LITERAL) a.getterCandidates.single() to b.getterCandidates.single() else null
        }
        return JSONObject().apply {
            put("getterPairs", pairs.size); put("classRenamed", pairs.count { it.first.className != it.second.className })
            put("methodRenamed", pairs.count { it.first.methodName != it.second.methodName })
            put("instructionCountChanged", pairs.count { it.first.instructionCount != it.second.instructionCount })
        }
    }
    private fun resourceCompatibility(before: DexInventory, after: DexInventory): JSONObject {
        fun ids(value: DexInventory) = value.identifiers.filter { it.namespace == IdentifierNamespace.RESOURCE_NAME && "resourceId" in it.metadata }
            .associate { it.key to it.metadata.getValue("resourceId") }
        val old = ids(before); val new = ids(after); val common = old.keys.intersect(new.keys)
        return JSONObject().apply { put("sameNameCount", common.size); put("sameNumericId", common.count { old[it] == new[it] }); put("sameNumericIdPercent", if (common.isEmpty()) 0.0 else common.count { old[it] == new[it] } * 100.0 / common.size) }
    }
}
