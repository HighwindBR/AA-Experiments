package io.github.highwindbr.aaxp.core

import io.github.highwindbr.aaxp.ExportFormat
import io.github.highwindbr.aaxp.discovery.DiscoveredIdentifier
import io.github.highwindbr.aaxp.discovery.DiscoveredType
import io.github.highwindbr.aaxp.discovery.IdentifierNamespace
import io.github.highwindbr.aaxp.discovery.ResolutionConfidence
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.io.StringWriter

class CatalogExportWriterTest {
    @Test fun emptyJsonIsValidAndDoesNotLoadEveryRow() {
        val out = StringWriter()
        var called = 0
        val count = CatalogExportWriter.write(out, ExportFormat.JSON,
            true, true, true, emptyMap(), pages = { offset, limit ->
                called++; assertEquals(0, offset)
                assertEquals(250, limit)
                emptyList()
            })
        assertEquals(0, count)
        assertEquals(1, called)
        assertEquals(0, JSONArray(out.toString()).length())
    }

    @Test fun csvHeaderRemainsBackwardCompatible() {
        val out = StringWriter()
        CatalogExportWriter.write(out, ExportFormat.CSV,
            true, false, false, emptyMap(), pages = { _, _ -> emptyList() })
        assertTrue(out.toString().startsWith("key,kind,value_type,storage_type,semantic_type,resolution,editable,runtime_consumer,semantics_reviewed,getters,consumers"))
        assertEquals("\"a\"\"b\"", CatalogExportWriter.csv("a\"b"))
    }

    @Test fun exportsMultiplePagesAsOneValidJsonArray() {
        val data = (0 until 511).map { i ->
            DiscoveredIdentifier("Feature__$i", IdentifierNamespace.FENOTYPE_FLAG,
                DiscoveredType.BOOLEAN, "false", emptyList(), emptyList(),
                ResolutionConfidence.UNIQUE_GETTER, null, true)
        }
        val pages = mutableListOf<Int>()
        val out = StringWriter()
        val exported = CatalogExportWriter.write(out, ExportFormat.JSON,
            false, true, false, emptyMap(), pages = { offset, limit ->
                assertEquals(CatalogExportWriter.PAGE_SIZE, limit)
                pages += offset
                data.drop(offset).take(limit)
            })
        assertEquals(511, exported)
        assertEquals(listOf(0, 250, 500), pages)
        val json = JSONArray(out.toString())
        assertEquals(511, json.length())
        assertEquals("Feature__0", json.getJSONObject(0).getString("key"))
        assertEquals("Feature__510", json.getJSONObject(510).getString("key"))
    }
}
