package io.github.highwindbr.aaxp.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class ProfileRefreshGateTest {
    @Test fun laterRefreshCannotOvertakeEarlierPublication() = runBlocking {
        val gate = ProfileRefreshGate()
        val enteredFirst = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val enteredSecond = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val first = async {
            gate.withLock {
                events += "first start"
                enteredFirst.complete(Unit)
                releaseFirst.await()
                events += "first finish"
            }
        }
        enteredFirst.await()
        val second = async {
            gate.withLock {
                events += "second"
                enteredSecond.complete(Unit)
            }
        }
        yield()
        assertFalse(enteredSecond.isCompleted)
        releaseFirst.complete(Unit)
        first.await()
        second.await()
        assertEquals(listOf("first start", "first finish", "second"), events)
    }

    @Test fun exceptionCannotPermanentlyBlockSubsequentRefreshes() = runBlocking {
        val gate = ProfileRefreshGate()
        assertThrows(IllegalStateException::class.java) {
            // runBlocking is used here solely to adapt an asserting JVM lambda.
            runBlocking { gate.withLock { error("transient failure") } }
        }
        assertEquals(17, gate.withLock { 17 })
    }
}
