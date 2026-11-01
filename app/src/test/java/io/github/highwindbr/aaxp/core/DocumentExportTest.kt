package io.github.highwindbr.aaxp.core

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.*
import org.junit.Test

class DocumentExportTest {
    @Test fun writesUtf8AndClosesBeforeReportingSuccess() {
        var flushed = false
        var closed = false
        val output = object : ByteArrayOutputStream() {
            override fun flush() { flushed = true }
            override fun close() { closed = true }
        }
        val data = "{\"name\":\"áudio 🎵\"}"
        writeExportDocument(data) { output }
        assertEquals(data, output.toString("UTF-8"))
        assertTrue(flushed)
        assertTrue(closed)
    }

    @Test fun missingDestinationFailsExplicitly() {
        assertThrows(IllegalStateException::class.java) { writeExportDocument("{}") { null } }
    }

    @Test fun providerOpenFailurePropagates() {
        val error = IOException("open failed")
        assertSame(error, assertThrows(IOException::class.java) { writeExportDocument("{}") { throw error } })
    }

    @Test fun writeAndFlushFailuresCloseTheDestination() {
        for (failDuringWrite in listOf(true, false)) {
            var closed = false
            val error = IOException("provider failed")
            val output = object : OutputStream() {
                override fun write(value: Int) { if (failDuringWrite) throw error }
                override fun flush() { throw error }
                override fun close() { closed = true }
            }
            assertSame(error, assertThrows(IOException::class.java) { writeExportDocument("{}") { output } })
            assertTrue(closed)
        }
    }

    @Test fun closeFailureIsNotReportedAsSuccess() {
        val output = object : ByteArrayOutputStream() {
            override fun close() { throw IOException("close failed") }
        }
        assertThrows(IOException::class.java) { writeExportDocument("{}") { output } }
    }
}
