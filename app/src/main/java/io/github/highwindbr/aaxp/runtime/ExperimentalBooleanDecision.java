package io.github.highwindbr.aaxp.runtime;

import org.json.JSONObject;

/**
 * Pure decision logic for the single, preflight-only experimental BOOLEAN getter.
 * No exceptions or raw snapshot content escape into telemetry.
 */
public final class ExperimentalBooleanDecision {
    public enum Status {
        ABSENT, MALFORMED_JSON, UNSUPPORTED_SCHEMA, DISABLED,
        KEY_MISMATCH, BUILD_MISMATCH, CLASS_MISMATCH,
        METHOD_MISMATCH, DESCRIPTOR_MISMATCH, INVALID_VALUE, VALID
    }

    public static final class Result {
        public final Status status;
        public final boolean value;

        private Result(Status status, boolean value) {
            this.status = status;
            this.value = value;
        }

        public boolean shouldOverride() {
            return status == Status.VALID;
        }
    }

    private ExperimentalBooleanDecision() {}

    private static Result reject(Status status) {
        return new Result(status, false);
    }

    public static Result evaluate(String raw, String expectedKey, String expectedBuild,
            String expectedClass, String expectedMethod, String expectedDescriptor) {
        if (raw == null) return reject(Status.ABSENT);
        try {
            JSONObject record = new JSONObject(raw);
            if (record.optInt("schema", -1) != 1) return reject(Status.UNSUPPORTED_SCHEMA);
            if (!expectedKey.equals(record.optString("key", ""))) return reject(Status.KEY_MISMATCH);
            if (!expectedBuild.equals(record.optString("build", ""))) return reject(Status.BUILD_MISMATCH);
            // The analyzer persists DEX class names (e.g. "Ladic;" or
            // "Lcom/example/Flag;"). The runtime receives the equivalent
            // Java binary name ("adic" or "com.example.Flag"). Accept only
            // these two exact representations of the same resolved class.
            // Do not weaken the getter, method, build or ABI comparisons.
            String savedClass = record.optString("targetClass", "");
            String expectedDexClass = "L" + expectedClass.replace('.', '/') + ";";
            if (!expectedClass.equals(savedClass) && !expectedDexClass.equals(savedClass)) {
                return reject(Status.CLASS_MISMATCH);
            }
            if (!expectedMethod.equals(record.optString("targetMethod", ""))) return reject(Status.METHOD_MISMATCH);
            if (!expectedDescriptor.equals(record.optString("descriptor", ""))) return reject(Status.DESCRIPTOR_MISMATCH);
            Object active = record.opt("active");
            if (Boolean.FALSE.equals(active)) return reject(Status.DISABLED);
            if (!Boolean.TRUE.equals(active)) return reject(Status.MALFORMED_JSON);
            Object value = record.opt("value");
            if (!(value instanceof Boolean)) return reject(Status.INVALID_VALUE);
            return new Result(Status.VALID, (Boolean) value);
        } catch (Exception malformed) {
            return reject(Status.MALFORMED_JSON);
        }
    }
}
