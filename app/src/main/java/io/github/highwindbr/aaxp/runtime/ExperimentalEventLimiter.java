package io.github.highwindbr.aaxp.runtime;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** One event per unique state per loaded Android Auto process. No raw values/snapshots stored. */
public final class ExperimentalEventLimiter {
    private final Set<String> reported = ConcurrentHashMap.newKeySet();

    public boolean first(String event, String key, String state) {
        return reported.add(event + "|" + key + "|" + state);
    }
}
