package io.github.highwindbr.aaxp.runtime;

/**
 * One-shot, explicitly requested Spotify projection proof.
 *
 * Independent of Android/LSPosed for deterministic tests. A request carries
 * a random token and a monotonic timestamp, expires quickly, and can only be
 * attempted after projection readiness AND an initial projected activity.
 * This does not infer or bypass head-unit driving restrictions.
 */
public final class MediaStartupLaunchGate {
    public static final String REQUEST_KEY = "diagnostics.media_startup.spotify_manual_request";
    public static final long TTL_MILLIS = 12_000L;
    public static final String SPOTIFY_PACKAGE = "com.spotify.music";
    public static final String SPOTIFY_SERVICE =
            "com.spotify.carapplibrary.androidauto.AndroidAutoService";
    public static final String MEDIA_ACTION =
            "androidx.car.app.media.action.SHOW_MEDIA_PLAYBACK";

    public enum Decision {
        NO_CHANGE, INVALID, EXPIRED, NOT_READY, NO_ACTIVITY, ALREADY_ATTEMPTED, ACCEPTED
    }

    private boolean ready;
    private boolean activitySeen;
    private boolean attempted;
    private String lastRequest;
    private int session;

    private MediaStartupLaunchGate() {}

    public static MediaStartupLaunchGate create(String currentRequest) {
        MediaStartupLaunchGate gate = new MediaStartupLaunchGate();
        // Commands surviving process restarts are never replayed.
        gate.lastRequest = currentRequest;
        return gate;
    }

    public static String makeRequest(long elapsedRealtime, String randomUuid) {
        if (elapsedRealtime < 0 || randomUuid == null ||
                !randomUuid.matches("[0-9a-fA-F-]{36}"))
            throw new IllegalArgumentException("Invalid proof request");
        return "v1|" + elapsedRealtime + "|" + randomUuid;
    }

    public synchronized void onReady() {
        // Duplicate readiness callbacks must NOT reset the one-shot budget.
        if (!ready) {
            ready = true;
            activitySeen = false;
            attempted = false;
            session++;
        }
    }

    public synchronized void onActivityStart() {
        if (ready) activitySeen = true;
    }

    public synchronized void onEnded() {
        ready = false;
        activitySeen = false;
        attempted = false;
        session++;
    }

    public synchronized boolean isReady() { return ready; }

    public synchronized int session() { return session; }

    public synchronized boolean stillReadyInSession(int expected) {
        return ready && session == expected;
    }

    public synchronized Decision consider(String request, long elapsedRealtime) {
        if (request == null || request.equals(lastRequest)) return Decision.NO_CHANGE;
        // Mark as seen BEFORE parsing or checking session: never retry a
        // rejected request, including after another preference notification.
        lastRequest = request;
        String[] values = request.split("\\|", -1);
        if (values.length != 3 || !"v1".equals(values[0]) ||
                !values[1].matches("[0-9]{1,16}") ||
                !values[2].matches("[0-9a-fA-F-]{36}"))
            return Decision.INVALID;
        final long timestamp;
        try { timestamp = Long.parseLong(values[1]); }
        catch (NumberFormatException malformed) { return Decision.INVALID; }
        if (elapsedRealtime < timestamp ||
                elapsedRealtime - timestamp > TTL_MILLIS) return Decision.EXPIRED;
        if (!ready) return Decision.NOT_READY;
        if (!activitySeen) return Decision.NO_ACTIVITY;
        if (attempted) return Decision.ALREADY_ATTEMPTED;
        attempted = true; // one attempt even if reflection later fails
        return Decision.ACCEPTED;
    }
}
