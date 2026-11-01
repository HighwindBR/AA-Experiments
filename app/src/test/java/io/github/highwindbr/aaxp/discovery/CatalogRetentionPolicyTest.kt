package io.github.highwindbr.aaxp.discovery

import org.junit.Assert.*
import org.junit.Test

class CatalogRetentionPolicyTest {
    private fun row(key: String, at: Long, origin: CatalogOrigin) =
        CatalogRetentionPolicy.Candidate(key, at, origin)

    @Test fun historicalUnpinnedCacheStillRetainsTwo() {
        val rows = listOf(
            row("old", 1, CatalogOrigin.LEGACY),
            row("previous", 2, CatalogOrigin.LEGACY),
            row("current", 3, CatalogOrigin.INSTALLED)
        )
        assertEquals(setOf("current", "previous"),
            CatalogRetentionPolicy.retained(rows, "current", null))
    }

    @Test fun importedBuildNeverEvictsPinnedInstalledOrItsPredecessor() {
        val rows = listOf(
            row("previous-installed", 10, CatalogOrigin.INSTALLED),
            row("installed", 20, CatalogOrigin.INSTALLED),
            row("archive", 30, CatalogOrigin.IMPORTED)
        )
        assertEquals(setOf("installed", "previous-installed", "archive"),
            CatalogRetentionPolicy.retained(rows, "archive", "installed"))
        assertEquals(setOf("installed", "previous-installed", "archive"),
            CatalogRetentionPolicy.retained(rows, "installed", "installed"))
    }

    @Test fun secondArchiveReplacesFirstWithoutEvictingInstalledHistory() {
        val rows = listOf(
            row("old-installed", 10, CatalogOrigin.INSTALLED),
            row("installed", 20, CatalogOrigin.INSTALLED),
            row("archive-one", 30, CatalogOrigin.IMPORTED),
            row("archive-two", 40, CatalogOrigin.IMPORTED)
        )
        assertEquals(setOf("installed", "old-installed", "archive-two"),
            CatalogRetentionPolicy.retained(rows, "archive-two", "installed"))
        assertEquals(setOf("installed", "old-installed", "archive-two"),
            CatalogRetentionPolicy.retained(rows, "installed", "installed"))
    }

    @Test fun installedUpdatePreservesNewBuildAndFormerInstalledBuild() {
        val rows = listOf(
            row("older", 10, CatalogOrigin.INSTALLED),
            row("old-installed", 20, CatalogOrigin.INSTALLED),
            row("new-installed", 40, CatalogOrigin.INSTALLED),
            row("archive", 30, CatalogOrigin.IMPORTED)
        )
        val beforePinMoves = CatalogRetentionPolicy.retained(rows, "new-installed", "old-installed")
        assertTrue(beforePinMoves.containsAll(setOf("new-installed", "old-installed")))
        val afterPinMoves = CatalogRetentionPolicy.retained(rows, "new-installed", "new-installed")
        assertEquals(setOf("new-installed", "old-installed", "archive"), afterPinMoves)
    }

    @Test fun missingPinnedFingerprintFallsBackToBoundedLegacyBehavior() {
        val rows = listOf(
            row("a", 1, CatalogOrigin.LEGACY),
            row("b", 2, CatalogOrigin.IMPORTED),
            row("c", 3, CatalogOrigin.IMPORTED)
        )
        assertEquals(setOf("b", "c"), CatalogRetentionPolicy.retained(rows, "b", "not-present"))
    }

    @Test fun emptyAndInvalidLimitsAreHandled() {
        assertTrue(CatalogRetentionPolicy.retained(emptyList(), "missing", null).isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            CatalogRetentionPolicy.retained(emptyList(), "missing", null, maximumBuilds = 0)
        }
    }
}
