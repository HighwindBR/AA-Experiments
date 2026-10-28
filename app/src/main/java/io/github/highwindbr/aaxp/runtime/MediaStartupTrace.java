package io.github.highwindbr.aaxp.runtime;

import java.util.concurrent.atomic.AtomicInteger;

/** Beta5 preflight1: read-only observation of two inspected Android Auto 18.1 routes.
 * Invocation is not proof of projected UI readiness or a completed app launch.
 */
public final class MediaStartupTrace {
    public static final String KEY = "diagnostics.media_startup_trace.enabled";
    public static final String BASE_SHA256 =
            "f3e90ea898469991785dbf91500e1d50db4c637e0e67483065ee0813e3b59c73";
    public static final String NAV_CLASS = "nzf";
    public static final String NAV_ARG_CLASS = "nzb";
    public static final String NAV_METHOD = "m";
    public static final String NAV_DESCRIPTOR =
            "(Landroid/content/ComponentName;Lnzb;Z)V";
    public static final String MEDIA_CLASS = "mad";
    public static final String MEDIA_METHOD = "b";
    public static final String MEDIA_DESCRIPTOR =
            "(Landroid/content/Intent;)Lqz;";

    public static final int MAX_EVENTS_PER_METHOD = 48;

    private MediaStartupTrace() {}

    public static boolean isSupported(String actualBaseSha256) {
        return BASE_SHA256.equalsIgnoreCase(actualBaseSha256);
    }

    public static boolean enabled(String preference) {
        return "true".equals(preference);
    }

    private static boolean take(AtomicInteger count) {
        return count.getAndUpdate(value ->
                value < MAX_EVENTS_PER_METHOD ? value + 1 : value)
                < MAX_EVENTS_PER_METHOD;
    }

    public static final class Budget {
        private final AtomicInteger navigation = new AtomicInteger();
        private final AtomicInteger media = new AtomicInteger();
        public boolean navigation() { return take(navigation); }
        public boolean media() { return take(media); }
    }
}
