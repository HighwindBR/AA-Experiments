package io.github.highwindbr.aaxp.runtime;

import org.junit.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public class MediaStartupTrace2Test {
    @Test public void onlyProjectionProcessIsObserved() {
        assertTrue(MediaStartupTrace2.isProjectionProcess(MediaStartupTrace2.PROJECTION_PROCESS));
        for (String value : new String[] {
                null, "", "com.google.android.projection.gearhead",
                "com.google.android.projection.gearhead:car",
                "com.google.android.projection.gearhead:watchdog",
                "com.google.android.projection.gearhead:shared"
        }) assertFalse(MediaStartupTrace2.isProjectionProcess(value));
    }

    @Test public void exactObservedPrototypesAreReviewableAndUnique() {
        Set<String> stages = new HashSet<>();
        assertEquals(8, MediaStartupTrace2.TARGETS.length);
        for (MediaStartupTrace2.Target t : MediaStartupTrace2.TARGETS) {
            assertTrue(stages.add(t.stage));
            assertFalse(t.owner.isEmpty());
            assertFalse(t.method.isEmpty());
            assertTrue(t.descriptor.startsWith("("));
            assertTrue(t.descriptor.endsWith("V"));
        }
        assertEquals("PROJECTION_READY", MediaStartupTrace2.TARGETS[0].stage);
        assertEquals("(Landroid/content/Intent;Lrgi;)V",
                MediaStartupTrace2.TARGETS[3].descriptor);
    }

    @Test public void stageBudgetsAreIndependentAndBounded() {
        MediaStartupTrace2.Budget budget = new MediaStartupTrace2.Budget();
        for (int n=1;n<=MediaStartupTrace2.MAX_EVENTS_PER_STAGE;n++) {
            assertEquals(n, budget.take("START_CAR_ACTIVITY"));
            assertEquals(n, budget.take("PROJECTION_READY"));
        }
        assertEquals(0, budget.take("START_CAR_ACTIVITY"));
        assertEquals(0, budget.take("PROJECTION_READY"));
        assertEquals(1, budget.take("MEDIA_APP_CHANGED"));
    }
}
