package io.github.highwindbr.aaxp

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class GreenLabContrastTest {
    @Test fun textRolesHaveReadableContrastInBothModes() {
        for (scheme in listOf(GreenLabLight, GreenLabDark)) {
            val pairs = listOf(
                "primary" to (scheme.primary to scheme.onPrimary),
                "primaryContainer" to (scheme.primaryContainer to scheme.onPrimaryContainer),
                "secondary" to (scheme.secondary to scheme.onSecondary),
                "secondaryContainer" to (scheme.secondaryContainer to scheme.onSecondaryContainer),
                "tertiary" to (scheme.tertiary to scheme.onTertiary),
                "tertiaryContainer" to (scheme.tertiaryContainer to scheme.onTertiaryContainer),
                "background" to (scheme.background to scheme.onBackground),
                "surface" to (scheme.surface to scheme.onSurface),
                "surfaceVariant" to (scheme.surfaceVariant to scheme.onSurfaceVariant),
                "error" to (scheme.error to scheme.onError),
                "errorContainer" to (scheme.errorContainer to scheme.onErrorContainer),
                "inverse" to (scheme.inverseSurface to scheme.inverseOnSurface)
            ) + listOf(scheme.surfaceContainerLowest, scheme.surfaceContainerLow,
                scheme.surfaceContainer, scheme.surfaceContainerHigh, scheme.surfaceContainerHighest,
                scheme.surfaceBright, scheme.surfaceDim).flatMap { surface -> listOf(
                "container text" to (surface to scheme.onSurface),
                "container secondary text" to (surface to scheme.onSurfaceVariant),
                "container accent" to (surface to scheme.primary),
                "container warning" to (surface to scheme.error)) }
            for ((name, pair) in pairs) {
                val a = luminance(pair.first)
                val b = luminance(pair.second)
                val ratio = (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
                assertTrue("$name contrast $ratio is below 4.5:1", ratio >= 4.5)
            }
        }
    }

    private fun luminance(color: Color): Double {
        fun linear(channel: Float): Double = channel.toDouble().let {
            if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
    }
}
