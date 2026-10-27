package io.github.highwindbr.aaxp.runtime;

/**
 * Read-only, opt-in diagnostic, pinned to the exact Android Auto 18.0 DEX
 * which was independently inspected. Never changes protocol fields or returns.
 */
public final class VideoTimingProtocolTrace {
    public static final String KEY = "diagnostics.vtc_protocol_trace.enabled";
    public static final String BASE_SHA256 =
            "cebd7c3c50885fb7dbb8fce202b5f33f0412c8f3801fac91a509da9482b9c60d";
    public static final String CLASS = "iyo";
    public static final String CAR_INFO_CLASS = "com.google.android.gms.car.CarInfo";
    public static final String SELECTOR_METHOD = "d";
    public static final String SELECTOR_DESCRIPTOR = "()Lxxe;";

    private VideoTimingProtocolTrace() {}

    public static boolean isSupported(String actualBaseSha256) {
        return BASE_SHA256.equalsIgnoreCase(actualBaseSha256);
    }

    public static boolean enabled(String remoteValue) {
        return "true".equals(remoteValue);
    }

    public static String protocolBranch(int major) {
        return major < 6 ? "NORMAL" : "PARKED_EXP";
    }

    public static String selectorBranch(int layout, boolean normalFlag) {
        return normalFlag && layout == 2 ? "NORMAL" : "PARKED_EXP";
    }
}
