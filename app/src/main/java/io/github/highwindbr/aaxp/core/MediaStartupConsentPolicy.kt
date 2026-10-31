package io.github.highwindbr.aaxp.core

import io.github.highwindbr.aaxp.resolver.MediaStartupSemanticResolver

/**
 * Auto-open is opt-in per installed Android Auto build, never carried over
 * across a fingerprint change. Only a user-triggered compatibility resolution
 * can request enabling, and only when the unique projected-activity trigger was
 * proven alongside the launch routes on a previously validated build.
 * A newly updated/unreviewed AA build remains OFF until separate user consent.
 */
object MediaStartupConsentPolicy {
    fun allowAutoAfterExplicitResolution(
        status: MediaStartupSemanticResolver.Status,
        roles: Map<String, String>
    ): Boolean = status == MediaStartupSemanticResolver.Status.KNOWN_BUILD_VALIDATED &&
        roles["activity.firstProjected"]?.isNotBlank() == true &&
        (roles["launch.auxiliary"]?.isNotBlank() == true ||
            roles["launch.startCarActivity"]?.isNotBlank() == true) &&
        (roles["launch.navigationObserver"]?.isNotBlank() == true ||
            roles["launch.startCarActivity"]?.isNotBlank() == true)
}
