package io.github.highwindbr.aaxp.runtime;

import org.junit.Test;
import static org.junit.Assert.*;

public class MediaStartupLaunchGateTest {
    private static final String ID = "1a3b5c7d-1111-2222-3333-9a8b7c6d5e4f";

    @Test public void noReplayingOfPersistedCommands() {
        String saved = MediaStartupLaunchGate.makeRequest(1000, ID);
        MediaStartupLaunchGate gate = MediaStartupLaunchGate.create(saved);
        gate.onReady();
        gate.onActivityStart();
        assertEquals(MediaStartupLaunchGate.Decision.NO_CHANGE, gate.consider(saved, 1001));
    }

    @Test public void requiresReadyAndFirstProjectedActivity() {
        MediaStartupLaunchGate gate = MediaStartupLaunchGate.create(null);
        assertEquals(MediaStartupLaunchGate.Decision.NOT_READY,
                gate.consider(MediaStartupLaunchGate.makeRequest(300, ID), 301));
        gate.onReady();
        assertEquals(MediaStartupLaunchGate.Decision.NO_ACTIVITY,
                gate.consider(MediaStartupLaunchGate.makeRequest(302, ID), 303));
        gate.onActivityStart();
        assertEquals(MediaStartupLaunchGate.Decision.ACCEPTED,
                gate.consider(MediaStartupLaunchGate.makeRequest(304, ID), 305));
        assertEquals(MediaStartupLaunchGate.Decision.ALREADY_ATTEMPTED,
                gate.consider(MediaStartupLaunchGate.makeRequest(306, ID), 307));
    }

    @Test public void duplicateReadyDoesNotResetBudgetButEndDoes() {
        MediaStartupLaunchGate gate = MediaStartupLaunchGate.create(null);
        gate.onReady(); gate.onActivityStart();
        assertEquals(MediaStartupLaunchGate.Decision.ACCEPTED,
                gate.consider(MediaStartupLaunchGate.makeRequest(100, ID), 102));
        int initial = gate.session();
        gate.onReady();
        assertEquals(initial, gate.session());
        assertEquals(MediaStartupLaunchGate.Decision.ALREADY_ATTEMPTED,
                gate.consider(MediaStartupLaunchGate.makeRequest(103, ID), 104));
        gate.onEnded();
        assertFalse(gate.isReady());
        gate.onReady(); gate.onActivityStart();
        assertTrue(gate.session() > initial);
        assertEquals(MediaStartupLaunchGate.Decision.ACCEPTED,
                gate.consider(MediaStartupLaunchGate.makeRequest(105, ID), 106));
    }

    @Test public void expiredFutureOrMalformedAreNeverLaunched() {
        MediaStartupLaunchGate gate = MediaStartupLaunchGate.create(null);
        gate.onReady(); gate.onActivityStart();
        assertEquals(MediaStartupLaunchGate.Decision.EXPIRED,
                gate.consider(MediaStartupLaunchGate.makeRequest(0, ID),
                        MediaStartupLaunchGate.TTL_MILLIS + 1));
        assertEquals(MediaStartupLaunchGate.Decision.EXPIRED,
                gate.consider(MediaStartupLaunchGate.makeRequest(50_000, ID), 400));
        assertEquals(MediaStartupLaunchGate.Decision.INVALID, gate.consider("v1|0|wrong", 1000));
        assertEquals(MediaStartupLaunchGate.Decision.INVALID,
                gate.consider("v2|1000|" + ID, 1001));
        assertEquals(MediaStartupLaunchGate.Decision.EXPIRED,
                gate.consider("v1|9999999999999999|" + ID, 1000));
        assertEquals(MediaStartupLaunchGate.Decision.ACCEPTED,
                gate.consider(MediaStartupLaunchGate.makeRequest(1100, ID), 1101));
    }

    @Test public void sessionEndRevokesPreviouslyValidatedSession() {
        MediaStartupLaunchGate gate = MediaStartupLaunchGate.create(null);
        gate.onReady(); gate.onActivityStart();
        int id = gate.session();
        gate.onEnded();
        assertFalse(gate.stillReadyInSession(id));
    }
}
