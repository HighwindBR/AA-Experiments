package io.github.highwindbr.aaxp.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class ProfileMutationCoordinatorTest {
    @Test fun writeAndPublicationCannotBeOvertaken() = runBlocking {
        val coordinator = ProfileMutationCoordinator(ProfileRefreshGate())
        val committed = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val first = async {
            coordinator.execute(write = {
                events += "first saved"
                committed.complete(Unit)
                release.await()
            }, publish = {
                events += "first published"
                true
            })
        }
        committed.await()
        val second = async {
            coordinator.execute(write = { events += "second saved" },
                publish = { events += "second published"; true })
        }
        yield()
        assertEquals(listOf("first saved"), events)
        release.complete(Unit)
        assertEquals(ProfileMutationCoordinator.Outcome.PUBLISHED, first.await())
        assertEquals(ProfileMutationCoordinator.Outcome.PUBLISHED, second.await())
        assertEquals(listOf("first saved", "first published", "second saved", "second published"), events)
    }

    @Test fun countedRestoreResultSurvivesPublication() = runBlocking {
        var commitCount = 0
        val gate = ProfileMutationCoordinator(ProfileRefreshGate())
        val (restored, outcome) = gate.executeWithResult(
            write = { commitCount++; 4 },
            publish = { true }
        )
        assertEquals(4, restored)
        assertEquals(1, commitCount)
        assertEquals(ProfileMutationCoordinator.Outcome.PUBLISHED, outcome)
    }

    @Test fun countedRestoreResultSurvivesPublicationFailure() = runBlocking {
        val (restored, outcome) = ProfileMutationCoordinator(ProfileRefreshGate())
            .executeWithResult(write = { 7 }, publish = { false })
        assertEquals(7, restored)
        assertEquals(ProfileMutationCoordinator.Outcome.SAVED_NOT_PUBLISHED, outcome)
    }

    @Test fun savedValueWithFailedPublicationIsNotReportedAsPublished() = runBlocking {
        var persisted = false
        val outcome = ProfileMutationCoordinator(ProfileRefreshGate()).execute(
            write = { persisted = true }, publish = { false })
        assertTrue(persisted)
        assertEquals(ProfileMutationCoordinator.Outcome.SAVED_NOT_PUBLISHED, outcome)
    }

    @Test fun failedCommitNeverStartsPublicationAndReleasesGate() = runBlocking {
        val coordinator = ProfileMutationCoordinator(ProfileRefreshGate())
        var published = false
        try {
            coordinator.execute(write = { error("remote preferences rejected commit") },
                publish = { published = true; true })
            fail("Commit must fail")
        } catch (expected: IllegalStateException) {
            assertEquals("remote preferences rejected commit", expected.message)
        }
        assertFalse(published)
        assertEquals(ProfileMutationCoordinator.Outcome.PUBLISHED,
            coordinator.execute(write = {}, publish = { true }))
    }

    @Test fun failedPublicationAllowsNextOperation() = runBlocking {
        val coordinator = ProfileMutationCoordinator(ProfileRefreshGate())
        try {
            coordinator.execute(write = {}, publish = { error("service disconnected") })
            fail("Publication must fail")
        } catch (expected: IllegalStateException) {
            assertEquals("service disconnected", expected.message)
        }
        assertEquals(ProfileMutationCoordinator.Outcome.PUBLISHED,
            coordinator.execute(write = {}, publish = { true }))
    }
}
