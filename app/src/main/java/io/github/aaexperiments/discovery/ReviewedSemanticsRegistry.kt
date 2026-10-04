package io.github.aaexperiments.discovery

/**
 * Exact-build manual reference layer.
 *
 * Entries may annotate an already resolved item, but MUST NOT select a getter, change mapping
 * confidence, or make an item editable. Regression expectations live in test sources instead.
 */
object ManualReferenceLayer {
    data class Entry(
        val summary: String,
        val trueMeaning: String? = null,
        val falseMeaning: String? = null,
        val evidence: String
    )

    private const val AA_17_8_663814 = "676205e71a75f38e0565b9be7d462c296b14490cef9a3feda3791098e5baae04"

    private val entries = mapOf(
        AA_17_8_663814 to mapOf(
            "UxPrototype__enabled" to Entry(
                "Controls the UX prototype launcher entry.",
                "Shows the launcher entry.", "Hides the launcher entry.",
                "Static consumer path plus observed launcher and Rick Astley video behavior on 17.8.663814."
            ),
            "UxPrototype__url" to Entry(
                "URL opened by the UX prototype launcher entry.",
                evidence = "Compiled URL and wrapper path audited on 17.8.663814."
            ),
            "Coolwalk__use_light_dark_theme" to Entry(
                "Controls the full-interface light/dark theme preference.",
                "Exposes full-interface Automatic, Light and Dark modes.", "Keeps the legacy maps-only theme behavior.",
                "Smali path and projected UI behavior confirmed during the 17.6 to 17.8 port."
            ),
            "Coolwalk__use_phone_primary_color" to Entry(
                "Controls use of the phone-derived primary color in the projected UI.",
                "Uses the phone-derived primary color.", "Uses the regular Android Auto color source.",
                "Visual behavior confirmed by the user on the patched Android Auto build."
            ),
            "Coolwalk__dashboard_show_3p_notifications_with_actions_enabled" to Entry(
                "Controls action affordances for third-party dashboard notifications.",
                "Allows supported notification actions.", "Keeps those actions hidden.",
                "Behavior confirmed by the user during the experimental APK evaluation."
            ),
            "HeroFeature__use_new_media_ui" to Entry(
                "Selects the newer media-player interface independently of forcing the Hero layout.",
                "Uses the newer media UI.", "Uses the previous media UI when otherwise eligible.",
                "Behavior confirmed visually during the experimental APK evaluation."
            )
        )
    )

    fun find(baseSha256: String, key: String): Entry? = entries[baseSha256]?.get(key)
}

/** Compatibility facade for older callers. It is not part of automatic resolution. */
object ReviewedSemanticsRegistry {
    fun find(baseSha256: String, key: String): ManualReferenceLayer.Entry? = ManualReferenceLayer.find(baseSha256, key)
}
