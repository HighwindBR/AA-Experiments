package io.github.highwindbr.aaxp.runtime;

import org.junit.Test;
import static org.junit.Assert.*;

public class DoubleGetterProbeRuntimeTest {
    @Test public void readOnlyDoubleTargetsRequireExactAbiAndInspectedBuild() {
        String base = DoubleGetterProbeRuntime.INSPECTED_BASE_SHA256;
        assertTrue(DoubleGetterProbeRuntime.permitted(DoubleGetterProbeRuntime.SYSTEMUI,
            "adjy", "a", "()D", base));
        assertTrue(DoubleGetterProbeRuntime.permitted(DoubleGetterProbeRuntime.PROJECTION,
            "adiu", "b", "()D", base));
        assertTrue(DoubleGetterProbeRuntime.permitted(DoubleGetterProbeRuntime.ENCODER,
            "adll", "c", "()D", base));
        assertFalse(DoubleGetterProbeRuntime.permitted(DoubleGetterProbeRuntime.SYSTEMUI,
            "adjy", "a", "()J", base));
        assertFalse(DoubleGetterProbeRuntime.permitted(DoubleGetterProbeRuntime.SYSTEMUI,
            "adjy", "b", "()D", base));
        assertFalse(DoubleGetterProbeRuntime.permitted(DoubleGetterProbeRuntime.SYSTEMUI,
            "adjy", "a", "()D", "other"));
        assertFalse(DoubleGetterProbeRuntime.permitted(
            "CarSensorParameters__max_parked_speed_gps_sensor","adbo","a","()D",base));
        assertFalse(DoubleGetterProbeRuntime.permitted("unknown","adjy","a","()D",base));
        assertFalse(DoubleGetterProbeRuntime.permitted(DoubleGetterProbeRuntime.SYSTEMUI,
            "adjy", "a", "()D", null));
    }
}
