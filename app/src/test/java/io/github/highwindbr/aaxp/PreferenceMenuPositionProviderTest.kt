package io.github.highwindbr.aaxp

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferenceMenuPositionProviderTest {
    private val row = IntRect(20, 300, 420, 380)
    private val window = IntSize(500, 800)
    private val popup = IntSize(180, 160)

    @Test fun touchUsesItsRowRelativePositionInsteadOfTheRowCenter() {
        val left = PreferenceMenuPositionProvider(IntOffset(100, 40), 1, 8, 52)
        val right = PreferenceMenuPositionProvider(IntOffset(300, 40), 1, 8, 52)
        assertEquals(IntOffset(30, 262), left.calculatePosition(row, window, LayoutDirection.Ltr, popup))
        assertEquals(IntOffset(230, 262), right.calculatePosition(row, window, LayoutDirection.Ltr, popup))
    }

    @Test fun keyboardAndAccessibilityUseTheRowCenterWithoutATouch() {
        val provider = PreferenceMenuPositionProvider(null, 1, 8, 52)
        assertEquals(IntOffset(130, 262), provider.calculatePosition(row, window, LayoutDirection.Ltr, popup))
    }

    @Test fun touchAnchorStaysInsideWindowEdges() {
        val provider = PreferenceMenuPositionProvider(IntOffset(0, 0), 2, 8, 52)
        assertEquals(IntOffset(8, 8), provider.calculatePosition(
            IntRect(0, 0, 400, 80), window, LayoutDirection.Ltr, popup))
        val bottom = PreferenceMenuPositionProvider(IntOffset(400, 80), 0, 8, 52)
        assertEquals(IntOffset(312, 632), bottom.calculatePosition(
            IntRect(100, 720, 500, 800), window, LayoutDirection.Rtl, popup))
    }
}
