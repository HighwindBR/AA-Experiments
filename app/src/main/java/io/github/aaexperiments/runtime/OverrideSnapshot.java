package io.github.aaexperiments.runtime;

import java.util.HashMap;
import java.util.Map;

/** Lock-free immutable snapshot: readers observe either the complete old or complete new map. */
final class OverrideSnapshot {
    private volatile Map<String, String> values = Map.of();

    String get(String key) {
        return values.get(key);
    }

    Map<String, String> view() {
        return values;
    }

    void replace(Map<String, String> next) {
        values = Map.copyOf(new HashMap<>(next));
    }
}
