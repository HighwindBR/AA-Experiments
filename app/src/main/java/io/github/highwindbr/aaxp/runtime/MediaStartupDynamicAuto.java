package io.github.highwindbr.aaxp.runtime;

/**
 * RC1 selected-media auto consent has broader semantics than PF6.2 Spotify
 * consent. Never silently migrate an old per-build PF6.2 opt-in to all players.
 */
public final class MediaStartupDynamicAuto {
    public static final String KEY = "diagnostics.media_startup.selected_media_auto_enabled";
    public static final String LEGACY_PF6_KEY = "diagnostics.media_startup.pf6_auto_enabled";
    private MediaStartupDynamicAuto() {}
}
