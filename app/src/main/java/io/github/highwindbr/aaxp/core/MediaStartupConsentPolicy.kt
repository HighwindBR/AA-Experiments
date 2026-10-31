package io.github.highwindbr.aaxp.core

import io.github.highwindbr.aaxp.resolver.MediaStartupSemanticResolver

/**
 * Auto-open is opt-in per installed Android Auto build, never carried over
 * across a fingerprint change. Only a user-triggered compatibility resolution
 * can request enabling, and only when the unique projected-activity trigger was
 * proven alongside the launch routes.
 */
object MediaStartupConsentPolicy {
    fun allowAutoAfterExplicitResolution(
        status: MediaStartupSemanticResolver.Status,
        roles: Map<String, String>
    ): Boolean = status in setOf(
        MediaStartupSemanticResolver.Status.KNOWN_BUILD_VALIDATED,
        MediaStartupSemanticResolver.Status.REVIEW_ONLY
    ) && roles["activity.firstProjected"]?.isNotBlank() == true
}
