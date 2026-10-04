package io.github.aaexperiments.discovery

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SafetyPolicyTest {
    @Test fun sensitiveControlsAreBlockedButOrdinaryUiFlagsAreNot() {
        assertNotNull(SafetyPolicy.blockedReason("AppValidation__signature_check_enabled"))
        assertNotNull(SafetyPolicy.blockedReason("video_while_driving"))
        assertNotNull(SafetyPolicy.blockedReason("SenderlibCertFeature__backup_key_raw"))
        assertNull(SafetyPolicy.blockedReason("CieloFeature__earth_enabled"))
        assertNull(SafetyPolicy.blockedReason("settings_show_widgets_default"))
    }
}
