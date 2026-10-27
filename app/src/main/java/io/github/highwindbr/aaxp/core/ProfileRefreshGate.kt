package io.github.highwindbr.aaxp.core

import kotlinx.coroutines.sync.Mutex

/** Serializes asynchronous profile computations and build-state handoffs. */
class ProfileRefreshGate {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T {
        mutex.lock()
        try {
            return block()
        } finally {
            mutex.unlock()
        }
    }
}
