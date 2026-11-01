package io.github.highwindbr.aaxp.runtime;

import org.junit.Test;
import static org.junit.Assert.*;

public class MediaStartupRoutePolicyTest {
    @Test public void selectBWhenAIsNotVerified() {
        assertEquals("B", MediaStartupRoutePolicy.select("A", false, true));
        assertEquals("B", MediaStartupRoutePolicy.select("B", false, true));
    }

    @Test public void selectAWhenBIsNotVerified() {
        assertEquals("A", MediaStartupRoutePolicy.select("B", true, false));
        assertEquals("A", MediaStartupRoutePolicy.select("A", true, false));
    }

    @Test public void preservePreferenceWhenBothAreVerified() {
        assertEquals("A", MediaStartupRoutePolicy.select("A", true, true));
        assertEquals("B", MediaStartupRoutePolicy.select("B", true, true));
        assertEquals("A", MediaStartupRoutePolicy.select(null, true, true));
    }

    @Test public void noVerifiedRouteMeansNoOpening() {
        assertNull(MediaStartupRoutePolicy.select("A", false, false));
        assertNull(MediaStartupRoutePolicy.select("B", false, false));
    }
}
