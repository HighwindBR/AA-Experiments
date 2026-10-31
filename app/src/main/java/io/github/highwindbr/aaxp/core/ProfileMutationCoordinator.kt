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
    ): Outcome = executeWithResult(write, publish).second

    /**
     * Preserve mutation results (such as restored-record counts) while holding
     * the same gate through verified publication. The write runs exactly once.
     */
    suspend fun <T> executeWithResult(
        write: suspend () -> T,
        publish: suspend () -> Boolean
    ): Pair<T, Outcome> = gate.withLock {
        val result = write()
        result to if (publish()) Outcome.PUBLISHED else Outcome.SAVED_NOT_PUBLISHED
    }
}
