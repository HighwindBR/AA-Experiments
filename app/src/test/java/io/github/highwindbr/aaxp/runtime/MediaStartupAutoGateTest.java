package io.github.highwindbr.aaxp.runtime;

import org.junit.Test;
import static org.junit.Assert.*;

public class MediaStartupAutoGateTest {
    @Test public void defaultOffAndOnlyExactOptIn() {
        assertFalse(MediaStartupAutoGate.enabled(null));
        assertFalse(MediaStartupAutoGate.enabled("TRUE"));
        assertFalse(MediaStartupAutoGate.enabled("1"));
        assertTrue(MediaStartupAutoGate.enabled("true"));
        MediaStartupAutoGate gate = new MediaStartupAutoGate();
        assertTrue(gate.onReady(false, 1000));
        assertEquals(-1, gate.onFirstActivity(1500));
        gate.onMediaChanged(true);
        assertEquals(MediaStartupAutoGate.Decision.DISABLED,
                gate.consume(1, 5000, true));
    }

    @Test public void readyPlusSpotifyPlusFirstActivityAndDelayDispatchesOnce() {
        MediaStartupAutoGate gate = new MediaStartupAutoGate();
        assertTrue(gate.onReady(true, 1000));
        assertFalse(gate.onReady(true, 1001));
        gate.onMediaChanged(true);
        int session = gate.onFirstActivity(2000);
        assertEquals(session, 1);
        assertEquals(-1, gate.onFirstActivity(2050));
        assertEquals(MediaStartupAutoGate.Decision.WAIT,
                gate.consume(session, 4799, true));
        assertEquals(MediaStartupAutoGate.Decision.DISPATCH,
                gate.consume(session, 4800, true));
        assertEquals(MediaStartupAutoGate.Decision.ALREADY_ATTEMPTED,
                gate.consume(session, 4900, true));
    }

    @Test public void userNavigationWinsOverAutomaticFocus() {
        MediaStartupAutoGate gate = new MediaStartupAutoGate();
        gate.onReady(true, 100);
        gate.onMediaChanged(true);
        int id = gate.onFirstActivity(1000);
        gate.onNavigation();
        assertEquals(MediaStartupAutoGate.Decision.NAVIGATION_OBSERVED,
                gate.consume(id, 3900, true));
        assertEquals(MediaStartupAutoGate.Decision.ALREADY_ATTEMPTED,
                gate.consume(id, 4000, true));
    }

    @Test public void routeBeforeFirstActivityDoesNotSuppressStartup() {
        MediaStartupAutoGate gate = new MediaStartupAutoGate();
        gate.onReady(true, 100);
        gate.onNavigation();
        gate.onMediaChanged(true);
        int id = gate.onFirstActivity(1000);
        assertEquals(MediaStartupAutoGate.Decision.DISPATCH,
                gate.consume(id, 3800, true));
    }

    @Test public void onlySpotifyAsMediaAndNoLateSwitches() {
        MediaStartupAutoGate gate = new MediaStartupAutoGate();
        gate.onReady(true, 100);
        int id = gate.onFirstActivity(1000);
        assertEquals(MediaStartupAutoGate.Decision.MEDIA_NOT_SPOTIFY,
                gate.consume(id, 3800, true));
        gate.onMediaChanged(true);
        assertEquals(MediaStartupAutoGate.Decision.ALREADY_ATTEMPTED,
                gate.consume(id, 4000, true));
    }

    @Test public void explicitManualRequestWins() {
        MediaStartupAutoGate gate = new MediaStartupAutoGate();
        gate.onReady(true, 100);
        gate.onMediaChanged(true);
        int id = gate.onFirstActivity(1000);
        gate.onManualAttempt();
        assertEquals(MediaStartupAutoGate.Decision.NAVIGATION_OBSERVED,
                gate.consume(id, 3800, true));
    }

    @Test public void turningOffCancelsAndCannotRearmMidSession() {
        MediaStartupAutoGate gate = new MediaStartupAutoGate();
        gate.onReady(true, 100);
        gate.onMediaChanged(true);
        int id = gate.onFirstActivity(1000);
        assertEquals(MediaStartupAutoGate.Decision.DISABLED,
                gate.consume(id, 3800, false));
        assertEquals(MediaStartupAutoGate.Decision.ALREADY_ATTEMPTED,
                gate.consume(id, 3801, true));
        // The production caller only schedules a single check; below verifies
        // that enabling a feature AFTER readiness cannot arm that session.
        gate.onEnded();
        gate.onReady(false, 5000);
        gate.onMediaChanged(true);
        int later = gate.onFirstActivity(6000);
        assertEquals(-1, later);
        assertEquals(MediaStartupAutoGate.Decision.DISABLED,
                gate.consume(3, 8800, true));
    }

    @Test public void sessionEndAndReconnectNeverReplayOldTimer() {
        MediaStartupAutoGate gate = new MediaStartupAutoGate();
        gate.onReady(true, 100);
        gate.onMediaChanged(true);
        int old = gate.onFirstActivity(1000);
        gate.onEnded();
        assertEquals(MediaStartupAutoGate.Decision.NO_SESSION,
                gate.consume(old, 4000, true));
        gate.onReady(true, 5000);
        gate.onMediaChanged(true);
        int next = gate.onFirstActivity(6000);
        assertNotEquals(old, next);
        assertEquals(MediaStartupAutoGate.Decision.SESSION_CHANGED,
                gate.consume(old, 9000, true));
        assertEquals(MediaStartupAutoGate.Decision.DISPATCH,
                gate.consume(next, 8800, true));
    }

    @Test public void staleStartupIsSkipped() {
        MediaStartupAutoGate gate = new MediaStartupAutoGate();
        gate.onReady(true, 100);
        gate.onMediaChanged(true);
        int id = gate.onFirstActivity(11500);
        assertEquals(MediaStartupAutoGate.Decision.STALE,
                gate.consume(id, 14300, true));
    }
}
