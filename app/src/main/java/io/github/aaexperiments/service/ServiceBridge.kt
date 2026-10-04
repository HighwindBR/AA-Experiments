package io.github.aaexperiments.service

import android.content.SharedPreferences
import io.github.aaexperiments.core.FrameworkState
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import android.os.ParcelFileDescriptor
import io.github.aaexperiments.resolver.DynamicProfile

object ServiceBridge : XposedServiceHelper.OnServiceListener {
    private val mutableState = MutableStateFlow(FrameworkState(error = "Xposed service not connected"))
    val state: StateFlow<FrameworkState> = mutableState
    @Volatile private var service: XposedService? = null

    fun register() = XposedServiceHelper.registerListener(this)

    override fun onServiceBind(bound: XposedService) {
        service = bound
        mutableState.value = try {
            FrameworkState(true, bound.frameworkName, bound.frameworkVersion, bound.apiVersion, bound.scope)
        } catch (t: Throwable) {
            FrameworkState(error = "Service query failed: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    override fun onServiceDied(dead: XposedService) {
        if (service === dead) service = null
        mutableState.value = FrameworkState(error = "Xposed service disconnected")
    }

    fun dynamicPreferencesOrNull(): SharedPreferences? = try { service?.getRemotePreferences(DYNAMIC_PREF_GROUP) } catch (t: Throwable) {
        mutableState.value = mutableState.value.copy(error = "Dynamic preferences unavailable: ${t.javaClass.simpleName}: ${t.message}")
        null
    }

    fun preferenceSnapshot(): Map<String, String> = dynamicPreferencesOrNull()?.all.orEmpty().mapNotNull { (key, value) ->
        if (key.startsWith(io.github.aaexperiments.core.DynamicOverrideCodec.PREFIX) && value is String) key to value else null
    }.toMap()

    fun publishProfile(buildFingerprintSha256: String, baseSha256: String, packageVersionCode: Long, packageArtifactSha256: Map<String, String>, dexSha256: Map<String, String>, mappings: List<DynamicProfile.Mapping>): Result<Int> = runCatching {
        val bound = checkNotNull(service) { "Xposed service is not connected" }
        val bytes = DynamicProfile.create(buildFingerprintSha256, baseSha256, packageVersionCode, packageArtifactSha256, dexSha256, mappings).toByteArray(Charsets.UTF_8)
        bound.openRemoteFile(DynamicProfile.FILE_NAME).use { descriptor ->
            ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { output ->
                output.channel.truncate(0)
                output.write(bytes)
                output.flush()
            }
        }
        bytes.size
    }

    const val DYNAMIC_PREF_GROUP = "dynamic_overrides_v2"
}
