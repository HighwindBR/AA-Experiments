package io.github.highwindbr.aaxp.runtime;

/**
 * AA 18.0-only, read-only verification. Called independently at hook install
 * time so a forged profile cannot expand the allowlist from manager-side code.
 */
public final class DoubleGetterProbeRuntime {
    public static final String INSPECTED_BASE_SHA256 =
        "cebd7c3c50885fb7dbb8fce202b5f33f0412c8f3801fac91a509da9482b9c60d";
    public static final String SYSTEMUI =
        "SystemUi__widescreen_aspect_ratio_breakpoint";
    public static final String PROJECTION =
        "ProjectionWindowManager__expected_frame_time_deviation";
    public static final String ENCODER =
        "VideoEncoderParams__bitrate_adjustment_exponent";

    private DoubleGetterProbeRuntime() {}

    public static boolean isDoubleTarget(String key) {
        return SYSTEMUI.equals(key) || PROJECTION.equals(key) || ENCODER.equals(key);
    }

    public static boolean permitted(String key, String cls, String method,
                                    String descriptor, String baseSha256) {
        if (baseSha256 == null || !INSPECTED_BASE_SHA256.equalsIgnoreCase(baseSha256)
                || !"()D".equals(descriptor)) return false;
        if (SYSTEMUI.equals(key)) return "adjy".equals(cls) && "a".equals(method);
        if (PROJECTION.equals(key)) return "adiu".equals(cls) && "b".equals(method);
        if (ENCODER.equals(key)) return "adll".equals(cls) && "c".equals(method);
        return false;
    }
}
