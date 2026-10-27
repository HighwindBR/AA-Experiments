package io.github.highwindbr.aaxp.core

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single exclusive owner for expensive installed/archive/targeted DEX operations.
 * This also guarantees startForegroundService/stopService are properly paired.
 */
class AnalysisOperationGate {
    private val active = AtomicBoolean(false)

    fun tryBegin(): Boolean = active.compareAndSet(false, true)

    fun finish() {
        check(active.compareAndSet(true, false)) {
            "Analysis operation finished without owning the foreground service"
        }
    }

    fun isActive(): Boolean = active.get()
}
