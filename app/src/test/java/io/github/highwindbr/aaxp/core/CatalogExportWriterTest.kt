package io.github.highwindbr.aaxp.core

import io.github.highwindbr.aaxp.ExportFormat
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
}
