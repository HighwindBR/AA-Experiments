package io.github.highwindbr.aaxp.runtime;

/** Pure, deterministic PF6.1 manual launch guard. No automatic launching. */
public final class MediaStartupLabGate {
    public static final String REQUEST_KEY = "diagnostics.media_startup.pf6_manual_request";
    public static final String ROUTE_KEY = "diagnostics.media_startup.pf6_route";
    public static final long TTL_MILLIS = 12000L;
    public enum Route { A, B }
    public enum Result { ACCEPTED, REPLAY, MALFORMED, EXPIRED, NOT_READY, ALREADY_USED, NO_MEDIA }
    private String previous;
    private boolean ready;
    private boolean used;
    private String componentPackage;
    private String componentClass;
    private long session;
    private long readyAt;
    public MediaStartupLabGate(String previous) { this.previous = previous; }
    public synchronized void ready(long now) {
        if (!ready) { ready = true; used = false; readyAt = now; session++; }
    }
    public synchronized void ended() {
        ready = false; used = false; componentPackage = null; componentClass = null; session++;
    }
    public synchronized void media(String pkg, String cls) {
        if (pkg != null && cls != null && !pkg.isEmpty() && !cls.isEmpty()) {
            componentPackage = pkg;
            componentClass = cls;
        } else {
            componentPackage = null; componentClass = null;
        }
    }
    public synchronized boolean isReady() { return ready; }
    public synchronized long session() { return session; }
    public synchronized boolean isCurrent(long expected) { return ready && session == expected; }
    public synchronized String componentClass() { return componentClass; }
    public synchronized String componentPackage() { return componentPackage; }
    public synchronized Result consider(String request, long now, Route expectedRoute) {
        if (request == null || request.equals(previous)) return Result.REPLAY;
        previous = request; // reject once, never replay after restart / reconnect
        String[] parts = request.split("\\|", -1);
        if (parts.length != 4 || !"v1".equals(parts[0]) ||
            !parts[1].matches("[0-9]{1,16}") ||
            !parts[2].matches("[0-9a-fA-F-]{36}") ||
            !expectedRoute.name().equals(parts[3])) return Result.MALFORMED;
        long time;
        try { time = Long.parseLong(parts[1]); }
        catch (NumberFormatException malformed) { return Result.MALFORMED; }
        if (time > now || now - time > TTL_MILLIS) return Result.EXPIRED;
        if (!ready) return Result.NOT_READY;
        // Do not dispatch commands issued before this projection began,
        // even if a remote-preference notification was missed.
        if (time < readyAt) return Result.EXPIRED;
        if (used) return Result.ALREADY_USED;
        if (!"com.spotify.music".equals(componentPackage) || componentClass == null)
            return Result.NO_MEDIA;
        used = true;
        return Result.ACCEPTED;
    }
}
