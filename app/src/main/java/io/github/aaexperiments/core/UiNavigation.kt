package io.github.aaexperiments.core

enum class SettingsPage { ROOT, DIAGNOSTICS, CATALOG, ABOUT }

enum class BackAction {
    CLOSE_EXPERIMENT,
    OPEN_DIAGNOSTICS,
    OPEN_SETTINGS,
    OPEN_STATUS,
    EXIT
}

fun backAction(tab: Int, settingsPage: SettingsPage, experimentOpen: Boolean): BackAction = when {
    experimentOpen -> BackAction.CLOSE_EXPERIMENT
    tab == 2 && settingsPage == SettingsPage.CATALOG -> BackAction.OPEN_DIAGNOSTICS
    tab == 2 && settingsPage != SettingsPage.ROOT -> BackAction.OPEN_SETTINGS
    tab != 0 -> BackAction.OPEN_STATUS
    else -> BackAction.EXIT
}
