package io.github.highwindbr.aaxp.core

import androidx.compose.ui.text.input.KeyboardType
import io.github.highwindbr.aaxp.discovery.DiscoveredType

/**
 * Shared UI feedback, based on the same type codec enforced by the manager.
 * The UI never treats an invalid decimal or an unselected BOOLEAN as a value.
 */
object OverrideInputFeedback {
    fun issue(type: DiscoveredType, input: String): String? {
        if (type == DiscoveredType.STRING) return null // Empty STRING is a real value.
        if (input.isBlank()) return "Enter a value or choose Default."
        return runCatching { DynamicOverrideCodec.validate(type, input) }.exceptionOrNull()?.let {
            when (type) {
                DiscoveredType.INT -> "Enter a signed 32-bit integer."
                DiscoveredType.LONG -> "Enter a signed 64-bit integer."
                DiscoveredType.FLOAT, DiscoveredType.DOUBLE -> "Enter a finite decimal number."
                DiscoveredType.BOOLEAN -> "Choose True or False."
                else -> "Unsupported value type."
            }
        }
    }

    /** Advanced BOOLEAN creation requires an explicit choice; never mistake
     * the compiled default for a user selection or a valid null issue for
     * a missing selection. Non-BOOLEAN types retain normal codec validation.
     */
    fun advancedIssue(type: DiscoveredType, input: String): String? {
        if (type == DiscoveredType.BOOLEAN && input != "true" && input != "false") {
            return "Choose True or False before reviewing."
        }
        return issue(type, input)
    }

    fun keyboardType(type: DiscoveredType): KeyboardType = when (type) {
        DiscoveredType.INT, DiscoveredType.LONG -> KeyboardType.Number
        DiscoveredType.FLOAT, DiscoveredType.DOUBLE -> KeyboardType.Decimal
        else -> KeyboardType.Text
    }
}
