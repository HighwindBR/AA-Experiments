package io.github.highwindbr.aaxp.runtime;

import org.junit.Test;
import static org.junit.Assert.*;

public class MediaStartupTraceTest {
    @Test public void onlyTheInspectedBaseApkIsSupported() {
        assertTrue(MediaStartupTrace.isSupported(MediaStartupTrace.BASE_SHA256));
        assertTrue(MediaStartupTrace.isSupported(MediaStartupTrace.BASE_SHA256.toUpperCase()));
        assertFalse(MediaStartupTrace.isSupported(null));
        assertFalse(MediaStartupTrace.isSupported("18.1.164344"));
    }

    @Test public void optInRequiresExactTrue() {
        assertTrue(MediaStartupTrace.enabled("true"));
        for (String s : new String[]{"false", "TRUE", "1", " true ", ""})
            assertFalse(MediaStartupTrace.enabled(s));
        assertFalse(MediaStartupTrace.enabled(null));
    }

    @Test public void signaturesArePinnedToInspectedDex() {
        assertEquals("(Landroid/content/ComponentName;Lnzb;Z)V", MediaStartupTrace.NAV_DESCRIPTOR);
        assertEquals("(Landroid/content/Intent;)Lqz;", MediaStartupTrace.MEDIA_DESCRIPTOR);
    }

    @Test public void eventVolumeIsBoundedPerMethod() {
        MediaStartupTrace.Budget budget = new MediaStartupTrace.Budget();
        for (int i=0;i<MediaStartupTrace.MAX_EVENTS_PER_METHOD;i++) {
            assertTrue(budget.navigation());
            assertTrue(budget.media());
        }
        for (int i=0;i<100;i++) {
            assertFalse(budget.navigation());
            assertFalse(budget.media());
        }
        assertTrue(new MediaStartupTrace.Budget().navigation());
    }
}
