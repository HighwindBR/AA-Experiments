package io.github.aaexperiments.core

data class AaInstallation(
    val installed: Boolean,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val basePath: String? = null,
    val splitPaths: List<String> = emptyList(),
    val baseSha256: String? = null,
    val dexSha256: Map<String, String> = emptyMap(),
    val identifierCount: Int = 0,
    val editableCount: Int = 0,
    val discoveryErrorCount: Int = 0,
    val runtimeTarget: Boolean = false,
    val scanError: String? = null,
    val buildFingerprintSha256: String? = null,
    val packageArtifactSha256: Map<String, String> = emptyMap(),
)

enum class SuspendedOverrideReason {
    SUSPENDED_TYPE_CHANGED,
    SUSPENDED_MAPPING_MISSING,
    SUSPENDED_MAPPING_AMBIGUOUS,
    SUSPENDED_INVALID_VALUE,
}

data class SuspendedOverride(
    val preferenceKey: String,
    val identifierKey: String? = null,
    val storedType: String? = null,
    val currentType: String? = null,
    val reason: SuspendedOverrideReason,
)

enum class SaveState { IDLE, SAVING, SAVED, FAILED }

data class FrameworkState(
    val connected: Boolean = false,
    val name: String? = null,
    val version: String? = null,
    val api: Int? = null,
    val scope: List<String> = emptyList(),
    val error: String? = null
)

enum class RuntimeReadinessKind {
    NO_TARGET,
    DISCONNECTED,
    API_UNSUPPORTED,
    OUT_OF_SCOPE,
    NO_OVERRIDES,
    PROFILE_UNPUBLISHED,
    READY_AWAITING_PROCESS
}

data class RuntimeReadiness(
    val kind: RuntimeReadinessKind,
    val label: String,
    val detail: String,
    val warning: Boolean = false
)

fun runtimeReadiness(
    aa: AaInstallation,
    framework: FrameworkState,
    overrideCount: Int,
    profilePublished: Boolean
): RuntimeReadiness = when {
    !aa.runtimeTarget -> RuntimeReadiness(
        RuntimeReadinessKind.NO_TARGET,
        "No runtime target",
        "Imported archives are analyzed offline and never hooked.",
        true
    )
    !framework.connected -> RuntimeReadiness(
        RuntimeReadinessKind.DISCONNECTED,
        "LSPosed disconnected",
        framework.error ?: "Connect the LSPosed service before publishing overrides.",
        true
    )
    (framework.api ?: 0) < 101 -> RuntimeReadiness(
        RuntimeReadinessKind.API_UNSUPPORTED,
        "API 101 required",
        "The connected framework does not expose the modern module API required by this build.",
        true
    )
    framework.scope.none { it == "com.google.android.projection.gearhead" } -> RuntimeReadiness(
        RuntimeReadinessKind.OUT_OF_SCOPE,
        "Android Auto out of scope",
        "Enable this module only for com.google.android.projection.gearhead in LSPosed.",
        true
    )
    overrideCount == 0 -> RuntimeReadiness(
        RuntimeReadinessKind.NO_OVERRIDES,
        "No active overrides",
        "The runtime remains unchanged until an experiment is explicitly modified."
    )
    !profilePublished -> RuntimeReadiness(
        RuntimeReadinessKind.PROFILE_UNPUBLISHED,
        "Profile not published",
        "Overrides are stored, but no compatible runtime profile is available.",
        true
    )
    else -> RuntimeReadiness(
        RuntimeReadinessKind.READY_AWAITING_PROCESS,
        "Ready; awaiting target process",
        "API 101 has no reverse telemetry channel. Confirm HOOK_INSTALLED and OVERRIDE_RETURNED in the LSPosed log; newly mapped experiments require the Android Auto process to start again."
    )
}
