package io.github.highwindbr.aaxp.core

import org.junit.Assert.assertEquals
import org.junit.Test

class UiNavigationTest {
    @Test fun `experiment always returns to its preserved list`() {
        assertEquals(BackAction.CLOSE_EXPERIMENT, backAction(1, SettingsPage.ROOT, true))
    }

    @Test fun `catalog returns to diagnostics`() {
        assertEquals(BackAction.OPEN_DIAGNOSTICS, backAction(2, SettingsPage.CATALOG, false))
    }

    @Test fun `settings subpages return to settings root`() {
        assertEquals(BackAction.OPEN_SETTINGS, backAction(2, SettingsPage.DIAGNOSTICS, false))
        assertEquals(BackAction.OPEN_SETTINGS, backAction(2, SettingsPage.ABOUT, false))
        assertEquals(BackAction.OPEN_SETTINGS, backAction(2, SettingsPage.BACKUPS, false))
        assertEquals(BackAction.OPEN_SETTINGS, backAction(2, SettingsPage.MEDIA, false))
    }

    @Test fun `switching away from Settings clears hidden nested page`() {
        assertEquals(SettingsPage.ROOT,
            settingsPageAfterTabSelection(2, 0, SettingsPage.BACKUPS))
        assertEquals(SettingsPage.ROOT,
            settingsPageAfterTabSelection(2, 1, SettingsPage.MEDIA))
        assertEquals(SettingsPage.ROOT,
            settingsPageAfterTabSelection(1, 2, SettingsPage.DIAGNOSTICS))
    }

    @Test fun `tapping active Settings tab preserves open subpage`() {
        assertEquals(SettingsPage.MEDIA,
            settingsPageAfterTabSelection(2, 2, SettingsPage.MEDIA))
    }

    @Test fun `primary tabs return to status before exit`() {
        assertEquals(BackAction.OPEN_STATUS, backAction(1, SettingsPage.ROOT, false))
        assertEquals(BackAction.OPEN_STATUS, backAction(2, SettingsPage.ROOT, false))
        assertEquals(BackAction.EXIT, backAction(0, SettingsPage.ROOT, false))
    }
}
