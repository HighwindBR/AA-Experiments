package io.github.highwindbr.aaxp.runtime;

import org.json.JSONObject;

/** Pure, fail-open decision for the configurable advanced primitive getter. */
public final class AdvancedRuntimeDecision {
    public enum Status {
        ABSENT, BAD_RECORD, DISABLED, BUILD_MISMATCH, KEY_MISMATCH,
        CLASS_MISMATCH, METHOD_MISMATCH, DESCRIPTOR_MISMATCH,
        TYPE_MISMATCH, INVALID_VALUE, VALID
    }
    public static final class Result {
        public final Status status;
        public final Object value;
        private Result(Status status, Object value) { this.status = status; this.value = value; }
        public boolean shouldOverride() { return status == Status.VALID; }
    }
    private AdvancedRuntimeDecision() {}
    private static Result reject(Status why) { return new Result(why, null); }

    public static String expectedDescriptor(String type) {
        return switch(type) {
            case "BOOLEAN" -> "()Z";
            case "LONG" -> "()J";
            case "DOUBLE" -> "()D";
            case "STRING" -> "()Ljava/lang/String;";
            default -> null;
        };
    }
    public static boolean sameClass(String saved, String binary) {
        return binary != null && (binary.equals(saved) ||
            ("L" + binary.replace('.', '/') + ";").equals(saved));
    }

    public static Result evaluate(String raw, String key, String build, String cls,
            String method, String descriptor, String type) {
        if (raw == null) return reject(Status.ABSENT);
        try {
            JSONObject j = new JSONObject(raw);
            if (j.optInt("schema", -1) != 1) return reject(Status.BAD_RECORD);
            if (!key.equals(j.optString("key", ""))) return reject(Status.KEY_MISMATCH);
            if (!build.equals(j.optString("build", ""))) return reject(Status.BUILD_MISMATCH);
            if (!sameClass(j.optString("targetClass", ""), cls)) return reject(Status.CLASS_MISMATCH);
            if (!method.equals(j.optString("targetMethod", ""))) return reject(Status.METHOD_MISMATCH);
            if (!descriptor.equals(j.optString("descriptor", ""))) return reject(Status.DESCRIPTOR_MISMATCH);
            if (!type.equals(j.optString("type", "")) ||
                !descriptor.equals(expectedDescriptor(type))) return reject(Status.TYPE_MISMATCH);
            if (Boolean.FALSE.equals(j.opt("active"))) return reject(Status.DISABLED);
            if (!Boolean.TRUE.equals(j.opt("active"))) return reject(Status.BAD_RECORD);
            if (!(j.opt("value") instanceof String value)) return reject(Status.INVALID_VALUE);
            try {
                Object result = switch(type) {
                    case "BOOLEAN" -> {
                        if (!"true".equals(value) && !"false".equals(value)) throw new IllegalArgumentException();
                        yield Boolean.valueOf(value);
                    }
                    case "LONG" -> Long.valueOf(value);
                    case "DOUBLE" -> {
                        double n = Double.parseDouble(value);
                        if (!Double.isFinite(n)) throw new IllegalArgumentException();
                        yield n;
                    }
                    case "STRING" -> value;
                    default -> throw new IllegalArgumentException();
                };
                return new Result(Status.VALID, result);
            } catch (RuntimeException invalid) { return reject(Status.INVALID_VALUE); }
        } catch (Exception malformed) { return reject(Status.BAD_RECORD); }
    }
}
