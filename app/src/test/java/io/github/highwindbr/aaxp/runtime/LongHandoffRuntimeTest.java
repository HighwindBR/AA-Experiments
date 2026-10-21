package io.github.highwindbr.aaxp.runtime;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class LongHandoffRuntimeTest {
    private String snapshot(boolean active) throws Exception {
        return new JSONObject().put("schema",1)
            .put("handoffMode",LongHandoffRuntime.MODE)
            .put("key",LongHandoffRuntime.KEY).put("type","LONG")
            .put("build","build").put("value","3").put("active",active)
            .put("targetClass","Laczh;").put("targetMethod","l")
            .put("descriptor","()J").put("opcodeFingerprint","sha")
            .put("instructionCount",17).put("previousOverride","LONG:3")
            .put("previousName",LongHandoffRuntime.KEY).toString();
    }
    private boolean active(String snapshot, String build) {
        return LongHandoffRuntime.activeSnapshot(snapshot, build, "aczh", "l", "()J", "LONG");
    }
    @Test public void worksOnlyOnExactReviewedTargetBuildAndBaseline() throws Exception {
        assertTrue(LongHandoffRuntime.isTarget(LongHandoffRuntime.KEY, "aczh", "l", "()J", "LONG"));
        assertFalse(LongHandoffRuntime.isTarget(LongHandoffRuntime.KEY, "aczh", "a", "()J", "LONG"));
        assertFalse(LongHandoffRuntime.isExpectedBase("other"));
        assertTrue(LongHandoffRuntime.isExpectedBase(LongHandoffRuntime.BASE));
        assertTrue(LongHandoffRuntime.baselineIntact("LONG:3",LongHandoffRuntime.KEY));
        assertFalse(LongHandoffRuntime.baselineIntact("LONG:0",LongHandoffRuntime.KEY));
        assertTrue(active(snapshot(true),"build"));
        assertFalse(active(snapshot(true),"other"));
        assertFalse(active(snapshot(false),"build"));
        assertFalse(active(null,"build"));
    }
    @Test public void tamperedSnapshotsCannotClaimExperimentalReturn() throws Exception {
        JSONObject x=new JSONObject(snapshot(true));
        assertFalse(active(x.put("value","4").toString(),"build"));
        assertFalse(active(x.put("value","3").put("previousOverride","LONG:0").toString(),"build"));
        assertFalse(active(x.put("previousOverride","LONG:3").put("handoffMode","arbitrary").toString(),"build"));
        assertFalse(active("broken-json","build"));
        assertTrue(LongHandoffRuntime.baselineIntact("LONG:3",LongHandoffRuntime.KEY));
        // Paused/removed snapshots are handled through the unchanged normal 3.
        assertFalse(active(snapshot(false),"build"));
    }
}
