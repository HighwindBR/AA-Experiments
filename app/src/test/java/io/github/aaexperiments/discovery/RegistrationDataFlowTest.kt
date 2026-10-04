package io.github.aaexperiments.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RegistrationDataFlowTest {
    @Test fun provesLiteralThroughFactoryResultToStaticField() {
        val result = RegistrationDataFlow.traceOps(listOf(
            RegistrationDataFlow.Op.StringValue(1, "Feature__flag"),
            RegistrationDataFlow.Op.NumericValue(5, "10000"),
            RegistrationDataFlow.Op.Invoke(listOf(1, 5), returnsValue = true),
            RegistrationDataFlow.Op.MoveResult(2),
            RegistrationDataFlow.Op.StaticFieldWrite(2, "Lfeature;->a:Lconfig;")
        ))
        assertEquals(setOf("Lfeature;->a:Lconfig;"), result["Feature__flag"])
        assertEquals(setOf("10000"), RegistrationDataFlow.traceDetailedOps(listOf(
            RegistrationDataFlow.Op.StringValue(1, "Feature__flag"), RegistrationDataFlow.Op.NumericValue(5, "10000"),
            RegistrationDataFlow.Op.Invoke(listOf(1, 5), true), RegistrationDataFlow.Op.MoveResult(2),
            RegistrationDataFlow.Op.StaticFieldWrite(2, "Lfeature;->a:Lconfig;")
        ))["Feature__flag"]?.candidateDefaults)
    }

    @Test fun tracksMovesAndDoesNotAssociateUnrelatedNearbyFields() {
        val result = RegistrationDataFlow.traceOps(listOf(
            RegistrationDataFlow.Op.StringValue(1, "CIELO_DASHBOARD"),
            RegistrationDataFlow.Op.Move(3, 1),
            RegistrationDataFlow.Op.StaticFieldWrite(9, "Lnoise;->a:Z"),
            RegistrationDataFlow.Op.Invoke(listOf(3), returnsValue = true),
            RegistrationDataFlow.Op.MoveResult(4),
            RegistrationDataFlow.Op.StaticFieldWrite(4, "Lenum;->dashboard:Lenum;")
        ))
        assertEquals(setOf("Lenum;->dashboard:Lenum;"), result["CIELO_DASHBOARD"])
        assertFalse(result["CIELO_DASHBOARD"].orEmpty().contains("Lnoise;->a:Z"))
    }

    @Test fun clobberBreaksStaleLiteralProvenance() {
        val result = RegistrationDataFlow.traceOps(listOf(
            RegistrationDataFlow.Op.StringValue(1, "Feature__flag"),
            RegistrationDataFlow.Op.Clobber(1),
            RegistrationDataFlow.Op.StaticFieldWrite(1, "Lfeature;->wrong:Ljava/lang/String;")
        ))
        assertFalse("Feature__flag" in result)
    }

    @Test fun separatesStringDefaultFromRegistrationKey() {
        val evidence = RegistrationDataFlow.traceDetailedOps(listOf(
            RegistrationDataFlow.Op.StringValue(1, "PhoneThemeFeature__manufacturer_prefers_device_font_family"),
            RegistrationDataFlow.Op.StringValue(2, "CgdzYW1zdW5n"),
            RegistrationDataFlow.Op.Invoke(listOf(1, 2), returnsValue = true),
            RegistrationDataFlow.Op.MoveResult(3),
            RegistrationDataFlow.Op.StaticFieldWrite(3, "Lfeature;->a:Lconfig;")
        )).getValue("PhoneThemeFeature__manufacturer_prefers_device_font_family")
        assertEquals(setOf("CgdzYW1zdW5n"), evidence.candidateDefaults)
        assertFalse(evidence.ambiguous)
    }
}
