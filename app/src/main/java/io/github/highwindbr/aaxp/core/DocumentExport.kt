package io.github.highwindbr.aaxp.core

import java.io.OutputStream

/** Call on an I/O dispatcher. Success includes flushing and closing the provider. */
fun writeExportDocument(data: String, openDestination: () -> OutputStream?) {
    checkNotNull(openDestination()) { "Backup destination is not writable" }.use { output ->
        output.write(data.toByteArray(Charsets.UTF_8))
        output.flush()
    }
}
