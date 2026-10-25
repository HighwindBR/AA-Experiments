package io.github.highwindbr.aaxp.core

import io.github.highwindbr.aaxp.discovery.DiscoveredType
import org.junit.Assert.*
import org.junit.Test

class OverrideInputFeedbackTest {
    @Test fun finiteScalarNumbersAndSignedBounds() {
        assertNull(OverrideInputFeedback.issue(DiscoveredType.LONG, "-9223372036854775808"))
        assertNull(OverrideInputFeedback.issue(DiscoveredType.DOUBLE, "-1.67"))
        assertNull(OverrideInputFeedback.issue(DiscoveredType.INT, "42"))
        assertNotNull(OverrideInputFeedback.issue(DiscoveredType.INT, "2147483648"))
        assertNotNull(OverrideInputFeedback.issue(DiscoveredType.LONG, ""))
        assertNotNull(OverrideInputFeedback.issue(DiscoveredType.LONG, "9223372036854775808"))
    }

    @Test fun nonFiniteAndMalformedDecimalsAreRejected() {
        assertNotNull(OverrideInputFeedback.issue(DiscoveredType.DOUBLE, "NaN"))
        assertNotNull(OverrideInputFeedback.issue(DiscoveredType.FLOAT, "Infinity"))
        assertNotNull(OverrideInputFeedback.issue(DiscoveredType.DOUBLE, "oops"))
        assertNull(OverrideInputFeedback.issue(DiscoveredType.DOUBLE, "0.2"))
    }

    @Test fun booleanRequiresExplicitSelection() {
        assertNotNull(OverrideInputFeedback.issue(DiscoveredType.BOOLEAN, ""))
        assertNotNull(OverrideInputFeedback.issue(DiscoveredType.BOOLEAN, "False"))
        assertNull(OverrideInputFeedback.issue(DiscoveredType.BOOLEAN, "false"))
        assertNull(OverrideInputFeedback.issue(DiscoveredType.BOOLEAN, "true"))
    }

    @Test fun advancedBooleanRequiresSelectionButAcceptsBothActualChoices() {
        assertEquals("Choose True or False before reviewing.",
            OverrideInputFeedback.advancedIssue(DiscoveredType.BOOLEAN, ""))
        assertNotNull(OverrideInputFeedback.advancedIssue(DiscoveredType.BOOLEAN, "TRUE"))
        assertNull(OverrideInputFeedback.advancedIssue(DiscoveredType.BOOLEAN, "false"))
        assertNull(OverrideInputFeedback.advancedIssue(DiscoveredType.BOOLEAN, "true"))
        assertNull(OverrideInputFeedback.advancedIssue(DiscoveredType.LONG, "0"))
        assertNotNull(OverrideInputFeedback.advancedIssue(DiscoveredType.LONG, "overflow"))
        assertNull(OverrideInputFeedback.advancedIssue(DiscoveredType.STRING, ""))
    }

    @Test fun emptyStringIsAValidExplicitOverride() {
        assertNull(OverrideInputFeedback.issue(DiscoveredType.STRING, ""))
    }
}
