package io.github.highwindbr.aaxp.core

/**
 * Discards results of older asynchronous UI queries when another request starts
 * or the user leaves the screen. Called only from the ViewModel's main thread.
 */
class LatestRequestGate {
    private var generation = 0L

    fun begin(): Long = ++generation

    fun invalidate() { generation++ }

    fun accepts(request: Long): Boolean = request == generation
}
