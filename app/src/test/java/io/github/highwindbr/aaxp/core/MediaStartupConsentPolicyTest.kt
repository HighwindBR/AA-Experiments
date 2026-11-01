package io.github.highwindbr.aaxp.core

import io.github.highwindbr.aaxp.resolver.MediaStartupSemanticResolver.Status
import org.junit.Assert.*
import org.junit.Test

class MediaStartupConsentPolicyTest {
    private val withTrigger = mapOf(
        "activity.firstProjected" to "Lverified;->run()V",
        "launch.startCarActivity" to "Lcontroller;->open(Landroid/content/Intent;Loptions;)V",
        "launch.navigationObserver" to "Lcontroller;->open(Landroid/content/Intent;Loptions;)V"
    )

    @Test fun knownBuildWithUniqueTriggerAllowsExplicitSetup() {
        assertTrue(MediaStartupConsentPolicy.allowAutoAfterExplicitResolution(
            Status.KNOWN_BUILD_VALIDATED, withTrigger))
    }

    @Test fun unreviewedBuildNeverAutoEnablesDuringCompatibilityScan() {
        assertFalse(MediaStartupConsentPolicy.allowAutoAfterExplicitResolution(
            Status.REVIEW_ONLY, withTrigger))
    }

    @Test fun manualOnlyRoutesDoNotEnableAutomaticOpening() {
        assertFalse(MediaStartupConsentPolicy.allowAutoAfterExplicitResolution(
            Status.KNOWN_BUILD_VALIDATED, mapOf("launch.startCarActivity" to "Ltest;->a()V")))
    }

    @Test fun auxiliaryOnlyWithProvenObserverCanAutoOpen() {
        val roles = withTrigger - "launch.startCarActivity" +
            ("launch.auxiliary" to "Lcontroller;->helper(Landroid/content/Intent;)V")
        assertTrue(MediaStartupConsentPolicy.allowAutoAfterExplicitResolution(
            Status.KNOWN_BUILD_VALIDATED, roles))
    }

    @Test fun auxiliaryWithoutNavigationObserverRemainsManualOnly() {
        val roles = mapOf(
            "activity.firstProjected" to "Ltracker;->run()V",
            "launch.auxiliary" to "Lcontroller;->helper(Landroid/content/Intent;)V"
        )
        assertFalse(MediaStartupConsentPolicy.allowAutoAfterExplicitResolution(
            Status.KNOWN_BUILD_VALIDATED, roles))
    }

    @Test fun ambiguousOrUnsupportedBuildAlwaysFailsClosed() {
        listOf(Status.AMBIGUOUS, Status.UNSUPPORTED, Status.ERROR).forEach {
            assertFalse(MediaStartupConsentPolicy.allowAutoAfterExplicitResolution(it, withTrigger))
        }
    }

    @Test fun blankAutomaticTriggerDoesNotCountAsProof() {
        assertFalse(MediaStartupConsentPolicy.allowAutoAfterExplicitResolution(
            Status.REVIEW_ONLY, mapOf("activity.firstProjected" to " ")))
    }
}
