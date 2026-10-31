package io.github.highwindbr.aaxp.core

/**
 * Bounded CPU-awake lease for foreground DEX analysis. The host service owns
 * the actual Android PARTIAL_WAKE_LOCK and always releases it on destruction.
 *
 * A five-minute timeout caps battery risk if the scan hangs. It is not renewed;
 * scans longer than five minutes may no longer be protected from CPU sleep.
 * This does not resurrect work if Android kills the entire app process.
 */
class AnalysisWakeLockLease(private val power: PowerLock) {
    interface PowerLock {
        val isHeld: Boolean
        fun acquire(timeoutMillis: Long)
        fun release()
    }

    companion object {
        const val MAX_HOLD_MILLIS: Long = 5L * 60L * 1000L
    }

    fun start() {
        if (!power.isHeld) power.acquire(MAX_HOLD_MILLIS)
    }

    fun stop() {
        if (power.isHeld) power.release()
    }
}
