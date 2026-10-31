package io.github.highwindbr.aaxp.runtime;
import org.junit.Test;
import static org.junit.Assert.*;
public class MediaStartupLabGateTest {
    private final String requestA = "v1|1000|aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee|A";
    @Test public void oneManualAttemptOnlyAfterReadinessAndKnownMedia() {
        MediaStartupLabGate gate = new MediaStartupLabGate(null);
        assertEquals(MediaStartupLabGate.Result.NOT_READY, gate.consider(requestA,1001,MediaStartupLabGate.Route.A));
        gate.ready(1000);
        assertEquals(MediaStartupLabGate.Result.NO_MEDIA, gate.consider(
            "v1|1002|aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee|A", 1003, MediaStartupLabGate.Route.A));
        gate.media("com.spotify.music","com.spotify.music.PlayerService");
        assertEquals(MediaStartupLabGate.Result.ACCEPTED, gate.consider(
            "v1|1004|aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee|B", 1005, MediaStartupLabGate.Route.B));
        assertEquals(MediaStartupLabGate.Result.ALREADY_USED, gate.consider(
            "v1|1006|aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee|A", 1007, MediaStartupLabGate.Route.A));
    }
    @Test public void acceptsSelectedNonSpotifyAfterExpiredAndReplay() {
        MediaStartupLabGate gate = new MediaStartupLabGate(requestA);
        gate.ready(1000); gate.media("com.google.android.apps.youtube.music","x");
        assertEquals(MediaStartupLabGate.Result.REPLAY,gate.consider(requestA,1001,MediaStartupLabGate.Route.A));
        assertEquals(MediaStartupLabGate.Result.EXPIRED,gate.consider(
            "v1|1000|aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee|B",14000,MediaStartupLabGate.Route.B));
        assertEquals(MediaStartupLabGate.Result.ACCEPTED,gate.consider(
            "v1|14001|aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee|A",14002,MediaStartupLabGate.Route.A));
    }
    @Test public void sessionEndsInvalidateAndNextSessionCanRun() {
        MediaStartupLabGate gate = new MediaStartupLabGate(null);
        gate.ready(1000); gate.media("com.spotify.music","test.Service");
        long id = gate.session(); gate.ended();
        assertFalse(gate.isCurrent(id));
        gate.ready(1000); gate.media("com.spotify.music","test.Service");
        assertTrue(gate.isCurrent(gate.session()));
        assertEquals(MediaStartupLabGate.Result.ACCEPTED,
            gate.consider(requestA,1001,MediaStartupLabGate.Route.A));
    }
    @Test public void oldSessionRequestNeverLaunchesAfterReconnect() {
        MediaStartupLabGate gate = new MediaStartupLabGate(null);
        gate.ready(1000);
        gate.ended();
        gate.ready(4000);
        gate.media("com.spotify.music", "com.spotify.music.Service");
        assertEquals(MediaStartupLabGate.Result.EXPIRED,
            gate.consider(requestA, 4002, MediaStartupLabGate.Route.A));
        assertEquals(MediaStartupLabGate.Result.ACCEPTED,
            gate.consider("v1|4003|aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee|A",
                4004, MediaStartupLabGate.Route.A));
    }
    @Test public void autoReservationSharesManualOncePerSession() {
        MediaStartupLabGate gate = new MediaStartupLabGate(null);
        gate.ready(1000); gate.media("com.spotify.music","com.spotify.music.Service");
        assertTrue(gate.reserveForAutomatic());
        assertFalse(gate.reserveForAutomatic());
        assertEquals(MediaStartupLabGate.Result.ALREADY_USED,
            gate.consider(requestA,1001,MediaStartupLabGate.Route.A));
        gate.ended(); gate.ready(2000);
        assertFalse(gate.reserveForAutomatic());
        gate.media("com.spotify.music","com.spotify.music.Service");
        assertTrue(gate.reserveForAutomatic());
    }
    @Test public void selectedPlayerCanBeNonSpotifyAndMustMatchBothParts() {
        MediaStartupLabGate gate = new MediaStartupLabGate(null);
        gate.ready(1000);
        gate.media("com.google.android.apps.youtube.music", "yt.music.Service");
        assertTrue(gate.selectedMediaMatches("com.google.android.apps.youtube.music", "yt.music.Service"));
        assertFalse(gate.selectedMediaMatches("com.spotify.music", "yt.music.Service"));
        assertFalse(gate.selectedMediaMatches("com.google.android.apps.youtube.music", "old.Service"));
        assertTrue(gate.reserveForAutomatic());
        assertFalse(gate.reserveForAutomatic());
        // A different class in the same package is a different selected target.
        gate.media("com.google.android.apps.youtube.music", "another.Service");
        assertFalse(gate.selectedMediaMatches("com.google.android.apps.youtube.music", "yt.music.Service"));
        gate.ended();
        assertFalse(gate.selectedMediaMatches("com.google.android.apps.youtube.music", "yt.music.Service"));
    }
    @Test public void nullOrIncompleteSelectionIsNeverLaunched() {
        MediaStartupLabGate gate = new MediaStartupLabGate(null);
        gate.ready(1000);
        gate.media("com.example.player", "");
        assertFalse(gate.reserveForAutomatic());
        assertEquals(MediaStartupLabGate.Result.NO_MEDIA, gate.consider(
            requestA, 1001, MediaStartupLabGate.Route.A));
        gate.media("com.example.player", "player.Service");
        assertFalse(gate.selectedMediaMatches("com.example.player", "other.Service"));
        gate.media(null, null);
        assertFalse(gate.reserveForAutomatic());
    }
    @Test public void mismatchedPreferenceOrMalformedNeverInvokes() {
        MediaStartupLabGate gate = new MediaStartupLabGate(null);
        gate.ready(1000); gate.media("com.spotify.music","service");
        assertEquals(MediaStartupLabGate.Result.MALFORMED,
            gate.consider(requestA,1001,MediaStartupLabGate.Route.B));
        assertEquals(MediaStartupLabGate.Result.MALFORMED,
            gate.consider("v1|123|bad|A",124,MediaStartupLabGate.Route.A));
    }
}
