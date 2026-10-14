package io.github.highwindbr.aaxp.discovery

import android.content.ContentValues
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogDatabaseInstrumentedTest {
    @Test
    fun pruneToRecentBuilds_checkpointsWithoutFailingAndRetainsCurrentAndPrevious() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase("catalog-v1.db")
        val database = CatalogDatabase(context)
        try {
            listOf("old" to 1L, "previous" to 2L, "current" to 3L).forEach { (sha, generatedAt) ->
                database.writableDatabase.insertOrThrow("builds", null, ContentValues().apply {
                    put("base_sha", sha)
                    put("generated_at", generatedAt)
                    put("dex_hashes", "[]")
                    put("identifier_count", 0)
                    put("editable_count", 0)
                    put("error_count", 0)
                    put("resolver_schema", 0)
                    put("base_apk_sha", sha)
                    put("version_code", 0)
                    put("package_artifacts", "[]")
                })
            }

            database.pruneToRecentBuilds("current")

            assertEquals("current", database.summary("current")?.baseSha256)
            assertEquals("previous", database.summary("previous")?.baseSha256)
            assertNull(database.summary("old"))
        } finally {
            database.close()
            context.deleteDatabase("catalog-v1.db")
        }
    }
}
