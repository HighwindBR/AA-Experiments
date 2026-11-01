package io.github.highwindbr.aaxp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Keep dialog actions outside the scrolling body, including with a large font/IME. */
@Composable
internal fun ScrollableDialogBody(content: @Composable () -> Unit) {
    Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
        content()
    }
}
