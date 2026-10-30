package io.github.highwindbr.aaxp.core

import org.junit.Assert.*
import org.junit.Test

class LatestRequestGateTest {
    @Test fun onlyNewestRequestMayPublish() {
        val gate = LatestRequestGate()
        val old = gate.begin()
        val recent = gate.begin()
        assertFalse(gate.accepts(old))
        assertTrue(gate.accepts(recent))
    }

    @Test fun leavingScreenInvalidatesPendingRequest() {
        val gate = LatestRequestGate()
        val pending = gate.begin()
        gate.invalidate()
        assertFalse(gate.accepts(pending))
        assertTrue(gate.accepts(gate.begin()))
    }

    @Test fun gatesForDifferentScreensAreIndependent() {
        val catalog = LatestRequestGate()
        val details = LatestRequestGate()
        val search = catalog.begin()
        val load = details.begin()
        catalog.invalidate()
        assertFalse(catalog.accepts(search))
        assertTrue(details.accepts(load))
    }
}
