package io.github.highwindbr.aaxp.discovery

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogDatabaseInstrumentedTest {
    private fun reset(context: Context) {
        context.deleteDatabase("catalog-v1.db")
        context.getSharedPreferences("catalog_retention", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun insertBuild(database: CatalogDatabase, sha: String, generatedAt: Long,
                            origin: CatalogOrigin = CatalogOrigin.LEGACY) {
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
            put("origin", origin.name)
        })
    }

    @Test
    fun pruneToRecentBuilds_checkpointsWithoutFailingAndRetainsCurrentAndPrevious() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        reset(context)
        val database = CatalogDatabase(context)
        try {
            insertBuild(database, "old", 1)
            insertBuild(database, "previous", 2)
            insertBuild(database, "current", 3)
            database.pruneToRecentBuilds("current")

            assertEquals("current", database.summary("current")?.baseSha256)
            assertEquals("previous", database.summary("previous")?.baseSha256)
            assertNull(database.summary("old"))
        } finally {
            database.close()
            reset(context)
        }
    }

    @Test
    fun offlineImportsKeepPinnedInstalledAndComparisonBaselineAcrossRefresh() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        reset(context)
        val database = CatalogDatabase(context)
        try {
            insertBuild(database, "historical-installed", 1, CatalogOrigin.INSTALLED)
            insertBuild(database, "live-installed", 2, CatalogOrigin.INSTALLED)
            database.pinInstalledBuild("live-installed")
            insertBuild(database, "archive-one", 3, CatalogOrigin.IMPORTED)
            database.pruneToRecentBuilds("archive-one")
            assertNotNull(database.summary("live-installed"))
            assertNotNull(database.summary("historical-installed"))
            assertNotNull(database.summary("archive-one"))

            insertBuild(database, "archive-two", 4, CatalogOrigin.IMPORTED)
            database.pruneToRecentBuilds("archive-two")
            assertNull(database.summary("archive-one"))
            assertNotNull(database.summary("archive-two"))
            assertEquals("historical-installed",
                database.previousSummary("live-installed")?.baseSha256)

            // New DB helper instance simulates a manager restart, preserving the pin.
            database.close()
            val reopened = CatalogDatabase(context)
            try {
                reopened.pruneToRecentBuilds("live-installed")
                assertNotNull(reopened.summary("live-installed"))
                assertNotNull(reopened.summary("archive-two"))
                assertEquals("historical-installed",
                    reopened.previousSummary("live-installed")?.baseSha256)
            } finally { reopened.close() }
        } finally {
            database.close()
            reset(context)
        }
    }

    @Test
    fun schemaSevenUpgradePreservesLegacyCatalogAndAddsOrigin() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        reset(context)
        // Construct a minimum valid pre-beta6 v7 schema on disk, then open the
        // v8 helper. onUpgrade(7,8) must not drop the user's old inventory.
        val file = context.getDatabasePath("catalog-v1.db")
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL("CREATE TABLE builds(base_sha TEXT PRIMARY KEY, generated_at INTEGER NOT NULL, dex_hashes TEXT NOT NULL, identifier_count INTEGER NOT NULL, editable_count INTEGER NOT NULL, error_count INTEGER NOT NULL, resolver_schema INTEGER NOT NULL, base_apk_sha TEXT NOT NULL, version_code INTEGER NOT NULL, package_artifacts TEXT NOT NULL)")
            old.execSQL("INSERT INTO builds VALUES('prior',1,'[]',0,0,0,7,'prior',178,'[]')")
            old.version = 7
        }
        val database = CatalogDatabase(context)
        try {
            assertEquals("prior", database.summary("prior")?.baseSha256)
            database.readableDatabase.rawQuery(
                "SELECT origin FROM builds WHERE base_sha='prior'", emptyArray()
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("LEGACY", c.getString(0))
            }
        } finally {
            database.close()
            reset(context)
        }
    }
}
