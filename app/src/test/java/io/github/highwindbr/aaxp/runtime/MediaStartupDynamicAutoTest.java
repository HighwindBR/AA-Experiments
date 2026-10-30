package io.github.highwindbr.aaxp.runtime;

import org.junit.Test;
import static org.junit.Assert.*;

public final class MediaStartupDynamicAutoTest {
    @Test public void selectedMediaOptInNeverReusesSpotifyOnlyConsent() {
        assertNotEquals(MediaStartupDynamicAuto.KEY, MediaStartupDynamicAuto.LEGACY_PF6_KEY);
        assertNotEquals(MediaStartupDynamicAuto.KEY, MediaStartupAutoGate.KEY);
        assertEquals("diagnostics.media_startup.pf6_auto_enabled",
                MediaStartupDynamicAuto.LEGACY_PF6_KEY);
        assertEquals("diagnostics.media_startup.selected_media_auto_enabled",
                MediaStartupDynamicAuto.KEY);
    }
}
