package io.github.highwindbr.aaxp.runtime;

/**
 * Beta5 PF4 experimental auto-open decision gate. Pure Java, no Android APIs.
 * Default disabled. A single automatic attempt is possible only within a
 * freshly opened projection session, with Spotify already selected as media,
 * following the first projected activity and a deliberate settling interval.
 *
 * ANY explicit launch route after the first activity suppresses auto-open.
 * A conservative false negative is safer than overriding a user choice.
 */
public final class MediaStartupAutoGate {
    public static final String KEY = "diagnostics.media_startup.spotify_auto_enabled";
    public static final long SETTLE_MILLIS = 2800L;
    public static final long MAX_FROM_READY_MILLIS = 12000L;

    public enum Decision {
        DISPATCH, DISABLED, NO_SESSION, NO_ACTIVITY, WAIT, STALE,
        MEDIA_NOT_SPOTIFY, NAVIGATION_OBSERVED, ALREADY_ATTEMPTED, SESSION_CHANGED
    }

    private int session;
    private boolean ready;
    private boolean optedInAtReady;
    private boolean firstActivity;
    private boolean spotifySelected;
    private boolean explicitNavigation;
    private boolean attempted;
    private long readyAt;
    private long firstAt;

    public static boolean enabled(String raw) { return "true".equals(raw); }

    /** Return true only when a NEW projection session was begun. */
    public synchronized boolean onReady(boolean enabledAtStart, long now) {
        if (ready) return false;
        session++;
        ready = true;
        optedInAtReady = enabledAtStart;
        firstActivity = false;
        spotifySelected = false;
        explicitNavigation = false;
        attempted = false;
        readyAt = now;
        firstAt = 0L;
        return true;
    }

    /** Session ID to schedule once; -1 means disabled/duplicate/not ready. */
    public synchronized int onFirstActivity(long now) {
        if (!ready || firstActivity) return -1;
        firstActivity = true;
        firstAt = now;
        return optedInAtReady ? session : -1;
    }

    public synchronized void onMediaChanged(boolean isSpotify) {
        if (ready) spotifySelected = isSpotify;
    }

    /** A launch explicitly requested after the initial activity wins over auto. */
    public synchronized void onNavigation() {
        if (ready && firstActivity && !attempted) explicitNavigation = true;
    }

    public synchronized void onManualAttempt() {
        if (ready) explicitNavigation = true;
    }

    public synchronized void onEnded() {
        ready = false;
        optedInAtReady = false;
        firstActivity = false;
        spotifySelected = false;
        explicitNavigation = false;
        attempted = false;
        session++;
    }

    /**
     * Called once at settle time, on AA's main thread. Every terminal path
     * consumes the auto opportunity: we never fire late if media changes.
     */
    public synchronized Decision consume(int scheduledSession, long now, boolean liveEnabled) {
        if (!ready) return Decision.NO_SESSION;
        if (scheduledSession != session) return Decision.SESSION_CHANGED;
        if (!firstActivity) return Decision.NO_ACTIVITY;
        if (attempted) return Decision.ALREADY_ATTEMPTED;
        if (!optedInAtReady || !liveEnabled) { attempted = true; return Decision.DISABLED; }
        if (now < firstAt || now - firstAt < SETTLE_MILLIS) return Decision.WAIT;
        attempted = true;
        if (now < readyAt || now - readyAt > MAX_FROM_READY_MILLIS) return Decision.STALE;
        if (explicitNavigation) return Decision.NAVIGATION_OBSERVED;
        if (!spotifySelected) return Decision.MEDIA_NOT_SPOTIFY;
        return Decision.DISPATCH;
    }
}
