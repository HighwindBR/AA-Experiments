package io.github.highwindbr.aaxp.core

import org.junit.Assert.*
import org.junit.Test

class AnalysisWakeLockLeaseTest {
    private class FakeLock : AnalysisWakeLockLease.PowerLock {
        override var isHeld = false
        var acquisitions = 0
        var releases = 0
        var lastTimeout = 0L
        override fun acquire(timeoutMillis: Long) {
            assertFalse(isHeld)
            isHeld = true
            acquisitions++
            lastTimeout = timeoutMillis
        }
        override fun release() {
            assertTrue(isHeld)
            isHeld = false
            releases++
        }
    }

    @Test fun wakeLockIsCappedAtFiveMinutes() {
        assertEquals(300_000L, AnalysisWakeLockLease.MAX_HOLD_MILLIS)
    }

    @Test fun acquiresOnceAndReleasesOnCompletion() {
        val power = FakeLock()
        val lease = AnalysisWakeLockLease(power)
        lease.start()
        lease.start()
        assertTrue(power.isHeld)
        assertEquals(1, power.acquisitions)
        assertEquals(300_000L, power.lastTimeout)
        lease.stop()
        lease.stop()
        assertFalse(power.isHeld)
        assertEquals(1, power.releases)
    }

    @Test fun canAcquireAgainForNextScanWithoutLeakingPriorLease() {
        val power = FakeLock()
        val lease = AnalysisWakeLockLease(power)
        lease.start()
        lease.stop()
        lease.start()
        lease.stop()
        assertEquals(2, power.acquisitions)
        assertEquals(2, power.releases)
    }

    @Test fun stoppedServiceWithoutAnalysisNeverAcquiresOrReleases() {
        val power = FakeLock()
        AnalysisWakeLockLease(power).stop()
        assertEquals(0, power.acquisitions)
        assertEquals(0, power.releases)
    }
}
