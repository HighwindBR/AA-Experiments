package io.github.highwindbr.aaxp.runtime;

import org.json.JSONObject;

/** Guard for the sole reviewed normal-LONG to Advanced hook ownership test. */
public final class LongHandoffRuntime {
    public static final String KEY = "Assistant__icon_experiment_variant";
    public static final String MODE = "NORMAL_LONG_VALIDATION";
    public static final String BASE =
            "cebd7c3c50885fb7dbb8fce202b5f33f0412c8f3801fac91a509da9482b9c60d";
    public static final String ORIGINAL = "LONG:3";
    public static final String NAME = KEY;
    public static final long BASELINE_VALUE = 3L;

    private LongHandoffRuntime() {}

    public static boolean isTarget(String key, String cls, String method, String descriptor, String type) {
        return KEY.equals(key) && "aczh".equals(cls) &&
                "l".equals(method) && "()J".equals(descriptor) && "LONG".equals(type);
    }

    public static boolean isExpectedBase(String hash) {
        return hash != null && BASE.equalsIgnoreCase(hash);
    }

    public static boolean baselineIntact(String rawNormal, String rawName) {
        return ORIGINAL.equals(rawNormal) && NAME.equals(rawName);
    }

    public static boolean activeSnapshot(String raw, String build, String cls,
            String method, String descriptor, String type) {
        if (raw == null || !isTarget(KEY, cls, method, descriptor, type)) return false;
        try {
            JSONObject j = new JSONObject(raw);
            return j.getInt("schema") == 1 &&
                    MODE.equals(j.getString("handoffMode")) &&
                    KEY.equals(j.getString("key")) &&
                    build.equals(j.getString("build")) &&
                    AdvancedRuntimeDecision.sameClass(j.getString("targetClass"), cls) &&
                    method.equals(j.getString("targetMethod")) &&
                    descriptor.equals(j.getString("descriptor")) &&
                    type.equals(j.getString("type")) &&
                    "3".equals(j.getString("value")) &&
                    ORIGINAL.equals(j.getString("previousOverride")) &&
                    NAME.equals(j.getString("previousName")) &&
                    j.getBoolean("active") &&
                    j.getInt("instructionCount") > 0 &&
                    !j.getString("opcodeFingerprint").isBlank();
        } catch (Exception malformed) {
            return false;
        }
    }
}
