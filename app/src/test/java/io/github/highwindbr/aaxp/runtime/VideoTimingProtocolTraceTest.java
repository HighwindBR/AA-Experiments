package io.github.highwindbr.aaxp.runtime;

import org.junit.Test;
import static org.junit.Assert.*;

public class VideoTimingProtocolTraceTest {
    @Test public void diagnosticsArePinnedToTheExactInspectedBaseApk() {
        assertTrue(VideoTimingProtocolTrace.isSupported(VideoTimingProtocolTrace.BASE_SHA256));
        assertTrue(VideoTimingProtocolTrace.isSupported(VideoTimingProtocolTrace.BASE_SHA256.toUpperCase()));
        assertFalse(VideoTimingProtocolTrace.isSupported(null));
        assertFalse(VideoTimingProtocolTrace.isSupported("cebd7c3c50885fb7"));
        assertFalse(VideoTimingProtocolTrace.isSupported("different"));
    }

    @Test public void protocolMajorAndSelectorBranchesMatchDex() {
        assertEquals("NORMAL", VideoTimingProtocolTrace.protocolBranch(5));
        assertEquals("PARKED_EXP", VideoTimingProtocolTrace.protocolBranch(6));
        assertEquals("PARKED_EXP", VideoTimingProtocolTrace.protocolBranch(7));
        assertEquals("NORMAL", VideoTimingProtocolTrace.selectorBranch(2, true));
        assertEquals("PARKED_EXP", VideoTimingProtocolTrace.selectorBranch(3, false));
        assertEquals("PARKED_EXP", VideoTimingProtocolTrace.selectorBranch(2, false));
        assertEquals("PARKED_EXP", VideoTimingProtocolTrace.selectorBranch(3, true));
    }

    @Test public void onlyAnExplicitTruePreferenceEnablesObservation() {
        assertTrue(VideoTimingProtocolTrace.enabled("true"));
        for (String other : new String[] { "", "false", "1", "TRUE" })
            assertFalse(VideoTimingProtocolTrace.enabled(other));
        assertFalse(VideoTimingProtocolTrace.enabled(null));
    }
}
