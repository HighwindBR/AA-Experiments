package io.github.highwindbr.aaxp.runtime;

/**
 * Select a proven route at configuration time; no retry after dispatch.
 * A is the traditional preferred route. The user's saved selection survives
 * when both routes are present, but an unavailable selection is never invoked.
 */
public final class MediaStartupRoutePolicy {
    private MediaStartupRoutePolicy() {}

    public static String select(String preferred, boolean hasA, boolean hasB) {
        if (!hasA && !hasB) return null;
        if ("B".equals(preferred) && hasB) return "B";
        if ("A".equals(preferred) && hasA) return "A";
        return hasA ? "A" : "B";
    }
}
