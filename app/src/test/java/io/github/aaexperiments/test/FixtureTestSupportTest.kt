package io.github.aaexperiments.test

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.AssumptionViolatedException
import org.junit.Test

class FixtureTestSupportTest {
    @Test fun missingPrivateArchiveProducesSkipAssumption() {
        val missingFile = "aa-fixture-helper-test-missing.apkm"
        val assumption = assertThrows(AssumptionViolatedException::class.java) {
            fixtureRootOrSkip(missingFile)
        }
        assertTrue(assumption.message.orEmpty().contains(missingFile))
    }
}
