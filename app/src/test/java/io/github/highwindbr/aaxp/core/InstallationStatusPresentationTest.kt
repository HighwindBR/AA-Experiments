package io.github.highwindbr.aaxp.core

import org.junit.Assert.*
import org.junit.Test

class InstallationStatusPresentationTest {
    @Test fun startupNeverClaimsNotFoundBeforeSearchCompletes() {
        assertEquals("Checking for Android Auto…",
            InstallationStatusPresentation.title(true, false, false, null))
        assertEquals("Finding the active installation and loading its catalog",
            InstallationStatusPresentation.detail(true, false, false, null, null))
    }

    @Test fun parserProgressDistinguishesAnalysisFromPackageSearch() {
        assertEquals("Analyzing Android Auto catalog…",
            InstallationStatusPresentation.title(true, true, false, null))
        assertEquals("Reading and preparing the Android Auto catalog",
            InstallationStatusPresentation.detail(true, true, false, null, null))
    }

    @Test fun trulyMissingInstallationIsOnlyShownAfterLoadingEnds() {
        assertEquals("Android Auto not found",
            InstallationStatusPresentation.title(false, false, false, null))
        assertEquals("Android Auto is hidden by package visibility",
            InstallationStatusPresentation.title(false, false, false,
                "Android Auto is hidden by package visibility"))
    }

    @Test fun aCompletedInstalledBuildShowsItsVersion() {
        assertEquals("Installed and ready",
            InstallationStatusPresentation.title(false, false, true, null))
        assertEquals("18.1 (181234)",
            InstallationStatusPresentation.detail(false, false, true, "18.1", 181234))
    }
}
