package io.github.highwindbr.aaxp.runtime;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class ExperimentalBooleanDecisionTest {
    private static final String KEY = "PhoneThemeFeature__enable_dynamic_app_icon_shape_by_manufacturer_kill_switch";
    private static final String BUILD = "aa-build";
    private static final String CLASS = "adic";
    private static final String METHOD = "d";
    private static final String DESC = "()Z";

    private JSONObject valid(boolean value) throws Exception {
        return new JSONObject()
                .put("schema", 1).put("key", KEY).put("build", BUILD)
                .put("targetClass", CLASS).put("targetMethod", METHOD)
                .put("descriptor", DESC).put("active", true).put("value", value);
    }

    private ExperimentalBooleanDecision.Result read(String raw) {
        return ExperimentalBooleanDecision.evaluate(raw, KEY, BUILD, CLASS, METHOD, DESC);
    }

    private void rejects(JSONObject data, ExperimentalBooleanDecision.Status status) {
        ExperimentalBooleanDecision.Result result = read(data.toString());
        assertEquals(status, result.status);
        assertFalse(result.shouldOverride());
    }

    @Test public void validTrueAndFalseAreDistinguishableAndNeverFallBack() throws Exception {
        ExperimentalBooleanDecision.Result t = read(valid(true).toString());
        ExperimentalBooleanDecision.Result f = read(valid(false).toString());
        assertEquals(ExperimentalBooleanDecision.Status.VALID, t.status);
        assertEquals(ExperimentalBooleanDecision.Status.VALID, f.status);
        assertTrue(t.shouldOverride());
        assertTrue(t.value);
        assertTrue(f.shouldOverride());
        assertFalse(f.value);
    }

    @Test public void dexAndJavaClassRepresentationsMatchExactly() throws Exception {
        for (boolean value : new boolean[] { true, false }) {
            // Actual AA 18.0 snapshot stores "Ladic;" while runtime uses "adic".
            ExperimentalBooleanDecision.Result oldSnapshot =
                    read(valid(value).put("targetClass", "Ladic;").toString());
            assertEquals(ExperimentalBooleanDecision.Status.VALID, oldSnapshot.status);
            assertTrue(oldSnapshot.shouldOverride());
            assertEquals(value, oldSnapshot.value);

            // Existing Java-form snapshots must remain valid.
            assertTrue(read(valid(value).toString()).shouldOverride());
        }
        JSONObject qualified = valid(false).put("targetClass", "Lcom/example/Flag;");
        ExperimentalBooleanDecision.Result qualifiedResult =
                ExperimentalBooleanDecision.evaluate(qualified.toString(), KEY, BUILD,
                        "com.example.Flag", METHOD, DESC);
        assertEquals(ExperimentalBooleanDecision.Status.VALID, qualifiedResult.status);
        assertFalse(qualifiedResult.value);
    }

    @Test public void malformedOrDifferentClassNamesStillFailClosed() throws Exception {
        for (String different : new String[] {
                "Lother;", "Ladic", "Ladic;;", "ladic;", "Lother/adic;",
                "com.example.adic", "Lcom/example/adic;"
        }) {
            rejects(valid(false).put("targetClass", different),
                    ExperimentalBooleanDecision.Status.CLASS_MISMATCH);
        }
        assertEquals(ExperimentalBooleanDecision.Status.BUILD_MISMATCH,
                ExperimentalBooleanDecision.evaluate(valid(false).put("targetClass", "Ladic;")
                        .put("build", "other").toString(), KEY, BUILD,
                        CLASS, METHOD, DESC).status);
        assertEquals(ExperimentalBooleanDecision.Status.METHOD_MISMATCH,
                ExperimentalBooleanDecision.evaluate(valid(false).put("targetClass", "Ladic;")
                        .put("targetMethod", "another").toString(), KEY, BUILD,
                        CLASS, METHOD, DESC).status);
    }

    @Test public void unavailableAndMalformedSnapshotsFallBack() throws Exception {
        assertEquals(ExperimentalBooleanDecision.Status.ABSENT, read(null).status);
        assertEquals(ExperimentalBooleanDecision.Status.MALFORMED_JSON, read("{oops").status);
        rejects(valid(true).put("schema", 2), ExperimentalBooleanDecision.Status.UNSUPPORTED_SCHEMA);
        rejects(valid(true).put("active", JSONObject.NULL), ExperimentalBooleanDecision.Status.MALFORMED_JSON);
        rejects(valid(true).put("active", "true"), ExperimentalBooleanDecision.Status.MALFORMED_JSON);
        rejects(valid(true).put("value", "false"), ExperimentalBooleanDecision.Status.INVALID_VALUE);
        rejects(valid(true).put("value", JSONObject.NULL), ExperimentalBooleanDecision.Status.INVALID_VALUE);
        rejects(valid(true).put("active", false), ExperimentalBooleanDecision.Status.DISABLED);
    }

    @Test public void mismatchedIdentitiesAlwaysFallBack() throws Exception {
        rejects(valid(false).put("key", "different"), ExperimentalBooleanDecision.Status.KEY_MISMATCH);
        rejects(valid(false).put("build", "new-build"), ExperimentalBooleanDecision.Status.BUILD_MISMATCH);
        rejects(valid(false).put("targetClass", "other"), ExperimentalBooleanDecision.Status.CLASS_MISMATCH);
        rejects(valid(false).put("targetMethod", "other"), ExperimentalBooleanDecision.Status.METHOD_MISMATCH);
        rejects(valid(false).put("descriptor", "()J"), ExperimentalBooleanDecision.Status.DESCRIPTOR_MISMATCH);
    }

    @Test public void eventLimiterEmitsOncePerStateWithoutSuppressingChanges() {
        ExperimentalEventLimiter limiter = new ExperimentalEventLimiter();
        assertTrue(limiter.first("EXPERIMENT_GETTER_INVOKED", KEY, "first"));
        assertFalse(limiter.first("EXPERIMENT_GETTER_INVOKED", KEY, "first"));
        assertTrue(limiter.first("EXPERIMENT_SNAPSHOT_LOADED", KEY, "ABSENT"));
        assertFalse(limiter.first("EXPERIMENT_SNAPSHOT_LOADED", KEY, "ABSENT"));
        assertTrue(limiter.first("EXPERIMENT_SNAPSHOT_LOADED", KEY, "VALID"));
        assertTrue(limiter.first("EXPERIMENT_OVERRIDE_RETURNED", KEY, "false"));
        assertTrue(limiter.first("EXPERIMENT_OVERRIDE_RETURNED", KEY, "true"));
        assertFalse(limiter.first("EXPERIMENT_OVERRIDE_RETURNED", KEY, "false"));
    }
}
