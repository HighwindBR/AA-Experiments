package io.github.highwindbr.aaxp.runtime;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class AdvancedRuntimeDecisionTest {
    private String record(String type, String value) throws Exception {
        return new JSONObject().put("schema", 1).put("key", "Feature__setting")
            .put("build", "sha").put("targetClass", "Lfoo/Bar;")
            .put("targetMethod", "a").put("descriptor", AdvancedRuntimeDecision.expectedDescriptor(type))
            .put("type", type).put("value", value).put("active", true).toString();
    }
    private AdvancedRuntimeDecision.Result read(String raw, String type) {
        return AdvancedRuntimeDecision.evaluate(raw, "Feature__setting", "sha",
            "foo.Bar", "a", AdvancedRuntimeDecision.expectedDescriptor(type), type);
    }
    @Test public void appliesFourTypedPrimitiveValues() throws Exception {
        assertEquals(Boolean.FALSE, read(record("BOOLEAN", "false"),"BOOLEAN").value);
        assertEquals(Long.MAX_VALUE, read(record("LONG", Long.toString(Long.MAX_VALUE)), "LONG").value);
        assertEquals(2.5d, (Double) read(record("DOUBLE", "2.5"), "DOUBLE").value, 0.0d);
        assertEquals("hello:世界", read(record("STRING","hello:世界"), "STRING").value);
        assertEquals("", read(record("STRING",""), "STRING").value);
    }
    @Test public void doesNotWriteWhenInvalidOrFromOldBuild() throws Exception {
        assertEquals(AdvancedRuntimeDecision.Status.BUILD_MISMATCH,
            AdvancedRuntimeDecision.evaluate(record("LONG","1"),"Feature__setting", "other",
                "foo.Bar","a","()J","LONG").status);
        assertEquals(AdvancedRuntimeDecision.Status.INVALID_VALUE, read(record("DOUBLE","NaN"),"DOUBLE").status);
        assertEquals(AdvancedRuntimeDecision.Status.INVALID_VALUE, read(record("BOOLEAN","invalid"),"BOOLEAN").status);
        assertEquals(AdvancedRuntimeDecision.Status.INVALID_VALUE, read(record("LONG","9223372036854775808"),"LONG").status);
        assertEquals(AdvancedRuntimeDecision.Status.ABSENT, read(null,"LONG").status);
        assertEquals(AdvancedRuntimeDecision.Status.BAD_RECORD, read("not-json","LONG").status);
        JSONObject paused = new JSONObject(record("BOOLEAN","true")).put("active",false);
        assertEquals(AdvancedRuntimeDecision.Status.DISABLED, read(paused.toString(),"BOOLEAN").status);
    }
    @Test public void identityAndTypeAreExactExceptDexNameNormalization() throws Exception {
        String raw = record("LONG","1");
        assertTrue(read(raw,"LONG").shouldOverride());
        assertEquals(AdvancedRuntimeDecision.Status.CLASS_MISMATCH,
            AdvancedRuntimeDecision.evaluate(raw, "Feature__setting","sha","foo.NotBar","a","()J","LONG").status);
        assertEquals(AdvancedRuntimeDecision.Status.DESCRIPTOR_MISMATCH,
            AdvancedRuntimeDecision.evaluate(raw,"Feature__setting","sha","foo.Bar","a","()D","DOUBLE").status);
        assertEquals(AdvancedRuntimeDecision.Status.TYPE_MISMATCH,
            AdvancedRuntimeDecision.evaluate(raw,"Feature__setting","sha","foo.Bar","a","()J","DOUBLE").status);
        assertEquals(AdvancedRuntimeDecision.Status.METHOD_MISMATCH,
            AdvancedRuntimeDecision.evaluate(raw,"Feature__setting","sha","foo.Bar","b","()J","LONG").status);
    }
}
