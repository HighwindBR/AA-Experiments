package io.github.highwindbr.aaxp.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Read-only preflight2: inspected Android Auto 18.1 projection lifecycle,
 * projected launch and media-routing callbacks. No startCarActivity call,
 * Intent mutation, audio command or automatic app selection is implemented.
 */
public final class MediaStartupTrace2 {
    public static final int MAX_EVENTS_PER_STAGE = 32;
    public static final String PROJECTION_PROCESS =
            "com.google.android.projection.gearhead:projection";

    private MediaStartupTrace2() {}

    public static boolean isProjectionProcess(String processName) {
        return PROJECTION_PROCESS.equals(processName);
    }

    public static final class Target {
        public final String stage;
        public final String owner;
        public final String method;
        public final String descriptor;

        private Target(String stage, String owner, String method, String descriptor) {
            this.stage = stage;
            this.owner = owner;
            this.method = method;
            this.descriptor = descriptor;
        }
    }

    // Exact prototypes independently checked against classes.dex in AA
    // 18.1.164344-release.daily, with base hash pinned by MediaStartupTrace.
    // These stages describe invocation of code, not proof of a visible HU UI.
    public static final Target[] TARGETS = {
        new Target("PROJECTION_READY", 
                "com.google.android.projection.gearhead.system.GhLifecycleService",
                "f", "()V"),
        new Target("INTENT_PROCESSED", "new", "g",
                "(Landroid/content/Intent;Lrgi;)V"),
        new Target("INTENT_TARGET_STORED", "new", "h",
                "(Landroid/content/Intent;)V"),
        new Target("START_CAR_ACTIVITY", "nfh", "k",
                "(Landroid/content/Intent;Lrgi;)V"),
        new Target("MEDIA_APP_CHANGED", "mcl", "l",
                "(Landroid/content/ComponentName;)V"),
        new Target("ACTIVITY_START_REQUESTED", "ruc", "run", "()V")
    };

    public static class Budget {
        private final Map<String, AtomicInteger> counts = new HashMap<>();

        /** 1-based ordinal, or 0 once the stage has reached its cap. */
        public synchronized int take(String stage) {
            AtomicInteger counter = counts.get(stage);
            if (counter == null) {
                counter = new AtomicInteger();
                counts.put(stage, counter);
            }
            int before = counter.getAndUpdate(value ->
                    value < MAX_EVENTS_PER_STAGE ? value + 1 : value);
            return before < MAX_EVENTS_PER_STAGE ? before + 1 : 0;
        }
    }
}
