package io.github.highwindbr.aaxp.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GetterProbePolicyTest {
    private fun getter(type: DiscoveredType, descriptor: String) =
        MethodFingerprint("classes.dex", "La;", "b", descriptor, type,
            emptyList(), 3, "fingerprint", false, emptyList(), emptyList())

    private fun candidate(key: String, type: DiscoveredType, desc: String) =
        DiscoveredIdentifier(key, IdentifierNamespace.FENOTYPE_FLAG, type, "true",
            emptyList(), listOf(getter(type, desc)), ResolutionConfidence.UNIQUE_GETTER, null, true)

    @Test fun readOnlyProbesAreRestrictedToKnownCompatibleGetters() {
        assertNull(GetterProbePolicy.issue(candidate(
            "PhoneThemeFeature__enable_dynamic_app_icon_shape_by_manufacturer_kill_switch",
            DiscoveredType.BOOLEAN, "()Z")))
        assertNull(GetterProbePolicy.issue(candidate(
            "WirelessLatencyMonitor__warning_threshold_ms", DiscoveredType.LONG, "()J")))
        assertNotNull(GetterProbePolicy.issue(candidate("AppValidation__should_bypass_validation",
            DiscoveredType.BOOLEAN, "()Z")))
    }

    @Test fun incorrectTypeAndMethodRemainBlocked() {
        val key = "WirelessLatencyMonitor__warning_threshold_ms"
        assertNotNull(GetterProbePolicy.issue(candidate(key, DiscoveredType.LONG, "()Z")))
        assertNotNull(GetterProbePolicy.issue(candidate(key, DiscoveredType.UNKNOWN, "()J")))
        assertNotNull(GetterProbePolicy.issue(candidate(key, DiscoveredType.BOOLEAN, "()Z")))
        val duplicated = candidate(key, DiscoveredType.LONG, "()J").let {
            it.copy(getterCandidates = it.getterCandidates + it.getterCandidates)
        }
        assertNotNull(GetterProbePolicy.issue(duplicated))
    }

    @Test fun sslTokensOnlyMatchIndependentWords() {
        assertNull(SafetyPolicy.blockedReason("WirelessLatencyMonitor__warning_threshold_ms"))
        assertNotNull(SafetyPolicy.blockedReason("SecureSSLFeature__skip_validation"))
        assertNotNull(SafetyPolicy.blockedReason("TLSFeature__enabled"))
        assertNotNull(SafetyPolicy.blockedReason("Security__tls_enabled"))
    }
}
