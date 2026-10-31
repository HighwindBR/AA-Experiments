package io.github.highwindbr.aaxp.core

/**
 * Shared human-readable presentation for build-scoped Media Startup state.
 * This is not runtime proof: the head unit can still reject the launch.
 */
object MediaStartupPresentation {
    fun availableRoutes(hasA: Boolean, hasB: Boolean): String = when {
        hasA && hasB -> "A + B"
        hasA -> "A only"
        hasB -> "B only"
        else -> "None verified"
    }

    fun summary(
        armed: Boolean,
        hasA: Boolean,
        hasB: Boolean,
        selected: String,
        automatic: Boolean,
        legacyAuto: Boolean,
        published: Boolean
    ): String = when {
        armed && !published -> "Routes saved · runtime profile not published"
        armed && !hasA && !hasB -> "No verified opening route"
        armed && automatic -> "Automatic opening on · Route $selected"
        armed -> "Automatic opening off · Route $selected ready"
        legacyAuto -> "Legacy Spotify opening on"
        else -> "Automatic opening off"
    }

    fun hasMediaConfiguration(armed: Boolean, legacyAuto: Boolean): Boolean =
        armed || legacyAuto
}
