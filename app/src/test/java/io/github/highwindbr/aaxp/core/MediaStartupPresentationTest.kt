package io.github.highwindbr.aaxp.core

import org.junit.Assert.*
import org.junit.Test

class MediaStartupPresentationTest {
    @Test fun routeSummaryMatchesActualAvailableMethods() {
        assertEquals("A + B", MediaStartupPresentation.availableRoutes(true, true))
        assertEquals("A only", MediaStartupPresentation.availableRoutes(true, false))
        assertEquals("B only", MediaStartupPresentation.availableRoutes(false, true))
        assertEquals("None verified", MediaStartupPresentation.availableRoutes(false, false))
    }

    @Test fun mediaOpeningDoesNotPretendToBeAnOverride() {
        assertEquals("Automatic opening on · Route B", MediaStartupPresentation.summary(
            true, false, true, "B", true, false, true))
        assertTrue(MediaStartupPresentation.hasMediaConfiguration(true, false))
    }

    @Test fun unpublishedProfileIsMoreImportantThanSavedConsent() {
        assertEquals("Routes saved · runtime profile not published", MediaStartupPresentation.summary(
            true, true, true, "A", true, false, false))
    }

    @Test fun bOnlyAndAOnlyAreNotMislabeledAsDualRoute() {
        assertEquals("Automatic opening off · Route A ready", MediaStartupPresentation.summary(
            true, true, false, "A", false, false, true))
        assertEquals("Automatic opening off · Route B ready", MediaStartupPresentation.summary(
            true, false, true, "B", false, false, true))
    }

    @Test fun legacyAndDisabledAreDistinguished() {
        assertEquals("Legacy Spotify opening on", MediaStartupPresentation.summary(
            false, false, false, "A", false, true, true))
        assertEquals("Automatic opening off", MediaStartupPresentation.summary(
            false, false, false, "A", false, false, false))
        assertFalse(MediaStartupPresentation.hasMediaConfiguration(false, false))
    }
}
