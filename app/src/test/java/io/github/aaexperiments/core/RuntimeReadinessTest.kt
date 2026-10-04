package io.github.aaexperiments.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeReadinessTest {
    private val target = AaInstallation(installed = true, runtimeTarget = true)
    private val connected = FrameworkState(
        connected = true,
        api = 101,
        scope = listOf("com.google.android.projection.gearhead")
    )

    @Test fun `offline archive has no runtime target`() {
        assertEquals(RuntimeReadinessKind.NO_TARGET, runtimeReadiness(AaInstallation(true), connected, 1, true).kind)
    }

    @Test fun `disconnected service is reported before profile state`() {
        assertEquals(RuntimeReadinessKind.DISCONNECTED, runtimeReadiness(target, FrameworkState(), 1, false).kind)
    }

    @Test fun `old framework API is rejected`() {
        assertEquals(RuntimeReadinessKind.API_UNSUPPORTED, runtimeReadiness(target, connected.copy(api = 100), 1, true).kind)
    }

    @Test fun `missing Android Auto scope is explicit`() {
        assertEquals(RuntimeReadinessKind.OUT_OF_SCOPE, runtimeReadiness(target, connected.copy(scope = listOf("android")), 1, true).kind)
    }

    @Test fun `zero overrides keeps runtime unchanged`() {
        assertEquals(RuntimeReadinessKind.NO_OVERRIDES, runtimeReadiness(target, connected, 0, true).kind)
    }

    @Test fun `stored overrides without profile are not ready`() {
        assertEquals(RuntimeReadinessKind.PROFILE_UNPUBLISHED, runtimeReadiness(target, connected, 1, false).kind)
    }

    @Test fun `published matching setup awaits target process evidence`() {
        assertEquals(RuntimeReadinessKind.READY_AWAITING_PROCESS, runtimeReadiness(target, connected, 1, true).kind)
    }
}
