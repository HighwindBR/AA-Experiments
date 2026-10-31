package io.github.highwindbr.aaxp.core

/**
 * Serializes the preference commit and the immediately following profile
 * publication as one operation. Other participants must use the same gate.
 *
 * A successful preference write is not equivalent to a published profile.
 * Publication failure must never be reported to the UI as an applied override.
 */
class ProfileMutationCoordinator(private val gate: ProfileRefreshGate) {
    enum class Outcome { PUBLISHED, SAVED_NOT_PUBLISHED }

    suspend fun execute(
        write: suspend () -> Unit,
        publish: suspend () -> Boolean
    ): Outcome = gate.withLock {
        write()
        if (publish()) Outcome.PUBLISHED else Outcome.SAVED_NOT_PUBLISHED
    }
}
