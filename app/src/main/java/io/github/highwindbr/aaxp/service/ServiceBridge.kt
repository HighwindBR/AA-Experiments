package io.github.highwindbr.aaxp.service

import android.content.SharedPreferences
import io.github.highwindbr.aaxp.core.FrameworkState
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import android.os.ParcelFileDescriptor
import io.github.highwindbr.aaxp.runtime.ProfileSlotPolicy
import java.io.ByteArrayOutputStream
import org.json.JSONObject
import io.github.highwindbr.aaxp.resolver.DynamicProfile

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
        if (key.startsWith(io.github.highwindbr.aaxp.core.DynamicOverrideCodec.PREFIX) && value is String) key to value else null
    }.toMap()

    /**
     * Never truncate the live profile. The LSPosed remote-file API has no rename:
     * commit a verified inactive slot before switching a durable preference pointer.
     * On a write failure the previously selected slot (or legacy file) remains intact.
     */
    @Synchronized
    fun publishProfile(buildFingerprintSha256: String, baseSha256: String, packageVersionCode: Long, packageArtifactSha256: Map<String, String>, dexSha256: Map<String, String>, mappings: List<DynamicProfile.Mapping>, probes: List<DynamicProfile.Probe> = emptyList(), experimental: List<DynamicProfile.ExperimentalMapping> = emptyList(), advanced: List<DynamicProfile.AdvancedMapping> = emptyList(), videoTimingTrace: Boolean = false): Result<Int> = runCatching {
        val bound = checkNotNull(service) { "Xposed service is not connected" }
        val prefs = bound.getRemotePreferences(DYNAMIC_PREF_GROUP)
        val bytes = DynamicProfile.create(buildFingerprintSha256, baseSha256, packageVersionCode, packageArtifactSha256, dexSha256, mappings, probes, experimental, advanced, videoTimingTrace).toByteArray(Charsets.UTF_8)
        val current = prefs.getString(ProfileSlotPolicy.POINTER_KEY, null)
        val target = ProfileSlotPolicy.inactiveSlot(current)
        bound.openRemoteFile(target).use { descriptor ->
            ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { output ->
                output.channel.truncate(0)
                output.channel.position(0)
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
        }
        // Read back through the same remote-file service before activating the slot.
        val readBack = bound.openRemoteFile(target).use { descriptor ->
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                ByteArrayOutputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        require(output.size() <= ProfileSlotPolicy.MAX_PROFILE_BYTES) {
                            "Profile read-back exceeds size limit"
                        }
                    }
                    output.toByteArray()
                }
            }
        }
        require(bytes.contentEquals(readBack)) { "Remote profile read-back mismatch" }
        val verified = JSONObject(String(readBack, Charsets.UTF_8))
        require(verified.getInt("schemaVersion") == 3 &&
            verified.getString("buildFingerprintSha256") == buildFingerprintSha256) {
            "Remote profile verification failed"
        }
        require(prefs.edit().putString(ProfileSlotPolicy.POINTER_KEY, target).commit()) {
            "Could not activate verified profile slot"
        }
        bytes.size
    }

    const val DYNAMIC_PREF_GROUP = "dynamic_overrides_v2"
}
