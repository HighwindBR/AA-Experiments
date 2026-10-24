package io.github.highwindbr.aaxp.core

import io.github.highwindbr.aaxp.ExportFormat
import io.github.highwindbr.aaxp.discovery.DiscoveredIdentifier
import org.json.JSONObject
import org.json.JSONArray
import java.io.Writer

/** Direct writer with bounded batches. Existing row fields and array layout are retained. */
object CatalogExportWriter {
    const val PAGE_SIZE = 250

    fun write(out: Writer, format: ExportFormat, methods: Boolean, defaults: Boolean,
        overrides: Boolean, values: Map<String, TypedOverride>,
        pages: (offset: Int, limit: Int) -> List<DiscoveredIdentifier>): Int {
        val columns = mutableListOf("key", "kind", "value_type", "storage_type", "semantic_type",
            "resolution", "editable", "runtime_consumer", "semantics_reviewed")
        if (defaults) columns += "compiled_default"
        if (overrides) columns += "override"
        if (methods) columns += listOf("getters", "consumers")
        if (format == ExportFormat.JSON) out.write("[\n")
        else out.write(columns.joinToString(",") + "\n")
        var offset = 0
        var count = 0
        while (true) {
            val batch = pages(offset, PAGE_SIZE)
            require(batch.size <= PAGE_SIZE) { "Export database returned an unbounded page" }
            if (batch.isEmpty()) break
            for (row in batch) {
                if (format == ExportFormat.JSON) {
                    val value = JSONObject().apply {
                        put("key", row.key); put("kind", row.kind.name)
                        put("valueType", row.inferredType.name)
                        put("storageType", row.storageType.name)
                        put("semanticType", row.semanticType.name)
                        put("resolution", row.resolution.name)
                        put("editable", row.editable)
                        put("runtimeConsumer", row.runtimeConsumerStatus.name)
                        put("semanticsReviewed", row.semanticsReviewed)
                        if (defaults) put("compiledDefault", row.compiledDefault ?: JSONObject.NULL)
                        if (overrides) put("override", values[row.key]?.encodedValue ?: JSONObject.NULL)
                        if (methods) {
                            put("getters", JSONArray(row.getterCandidates.map {
                                "${it.className}->${it.methodName}${it.descriptor}"
                            }))
                            put("consumers", JSONArray(row.consumers.map {
                                "${it.method.className}->${it.method.methodName}${it.method.descriptor}"
                            }))
                        }
                    }
                    if (count > 0) out.write(",\n")
                    out.write(value.toString())
                } else {
                    val v = mutableListOf(row.key,row.kind.name,row.inferredType.name,
                        row.storageType.name,row.semanticType.name,row.resolution.name,
                        row.editable.toString(),row.runtimeConsumerStatus.name,row.semanticsReviewed.toString())
                    if (defaults) v += row.compiledDefault.orEmpty()
                    if (overrides) v += values[row.key]?.encodedValue.orEmpty()
                    if (methods) {
                        v += row.getterCandidates.joinToString("|") { "${it.className}->${it.methodName}${it.descriptor}" }
                        v += row.consumers.joinToString("|") { "${it.method.className}->${it.method.methodName}${it.method.descriptor}" }
                    }
                    out.write(v.joinToString(",") { csv(it) } + "\n")
                }
                count++
            }
            offset += batch.size
            if (batch.size < PAGE_SIZE) break
        }
        if (format == ExportFormat.JSON) out.write("\n]")
        out.flush()
        return count
    }

    fun csv(v: String): String = "\"" + v.replace("\"", "\"\"") + "\""
}
