package io.github.highwindbr.aaxp

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Define every role: Material defaults otherwise introduce purple containers.
internal val GreenLabLight = lightColorScheme(
    primary = Color(0xFF006D3F), onPrimary = Color.White,
    primaryContainer = Color(0xFFA0F4BA), onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF4E6354), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1E8D6), onSecondaryContainer = Color(0xFF0C1F13),
    tertiary = Color(0xFF3A6470), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBDEAF6), onTertiaryContainer = Color(0xFF001F26),
    background = Color(0xFFF7FBF5), onBackground = Color(0xFF181D18),
    surface = Color(0xFFF7FBF5), onSurface = Color(0xFF181D18),
    surfaceVariant = Color(0xFFDDE5DC), onSurfaceVariant = Color(0xFF414941),
    surfaceTint = Color(0xFF006D3F),
    inverseSurface = Color(0xFF2D322D), inverseOnSurface = Color(0xFFEEF2EC),
    inversePrimary = Color(0xFF72D99A),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF717970), outlineVariant = Color(0xFFC1C9BF), scrim = Color.Black,
    surfaceBright = Color(0xFFF7FBF5), surfaceDim = Color(0xFFD7DBD5),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF1F5EF),
    surfaceContainer = Color(0xFFEBEFE9), surfaceContainerHigh = Color(0xFFE5E9E3),
    surfaceContainerHighest = Color(0xFFDFE3DD)
)

internal val GreenLabDark = darkColorScheme(
    primary = Color(0xFF72D99A), onPrimary = Color(0xFF00391D),
    primaryContainer = Color(0xFF00522D), onPrimaryContainer = Color(0xFF8DD8A8),
    secondary = Color(0xFFB5CCBA), onSecondary = Color(0xFF213527),
    secondaryContainer = Color(0xFF374B3D), onSecondaryContainer = Color(0xFFD1E8D6),
    tertiary = Color(0xFFA1CEDA), onTertiary = Color(0xFF003640),
    tertiaryContainer = Color(0xFF214C57), onTertiaryContainer = Color(0xFFBDEAF6),
    background = Color(0xFF101510), onBackground = Color(0xFFDFE3DD),
    surface = Color(0xFF101510), onSurface = Color(0xFFDFE3DD),
    surfaceVariant = Color(0xFF414941), onSurfaceVariant = Color(0xFFC1C9BF),
    surfaceTint = Color(0xFF72D99A),
    inverseSurface = Color(0xFFDFE3DD), inverseOnSurface = Color(0xFF2D322D),
    inversePrimary = Color(0xFF006D3F),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8B9389), outlineVariant = Color(0xFF414941), scrim = Color.Black,
    surfaceBright = Color(0xFF363B35), surfaceDim = Color(0xFF101510),
    surfaceContainerLowest = Color(0xFF0B100B), surfaceContainerLow = Color(0xFF181D18),
    surfaceContainer = Color(0xFF1C211C), surfaceContainerHigh = Color(0xFF262C26),
    surfaceContainerHighest = Color(0xFF313630)
)
