package io.github.aaexperiments.runtime;

import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import android.content.pm.ApplicationInfo;
import io.github.aaexperiments.core.BuildFingerprint;
import io.github.aaexperiments.core.PackageBuildIdentity;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class AndroidAutoModule extends XposedModule {
    private static final String AA_PACKAGE = "com.google.android.projection.gearhead";
    private static final String PREF_GROUP = "dynamic_overrides_v2";
    private static final String PROFILE_FILE = "active_profile_v2.json";
    private static final String TAG = "AAExperiments";
    private static final String EVENT_PREFIX = "AAX_EVENT ";
    private final OverrideSnapshot overrideSnapshot = new OverrideSnapshot();
    private final Map<String, String> lastReturnEvent = new ConcurrentHashMap<>();
    private SharedPreferences remotePreferences;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener =
            (preferences, key) -> refreshSnapshot(preferences);

    @Override public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        event(Log.INFO, "MODULE_LOADED", null, "process", param.getProcessName(), "api", String.valueOf(getApiVersion()));
        try {
            remotePreferences = getRemotePreferences(PREF_GROUP);
            refreshSnapshot(remotePreferences);
            remotePreferences.registerOnSharedPreferenceChangeListener(preferenceListener);
        } catch (Throwable t) {
            event(Log.WARN, "REMOTE_PREFERENCES_UNAVAILABLE", null, "error", t.getClass().getSimpleName());
            log(Log.WARN, TAG, "Remote preferences unavailable; all hooks fail open", t);
        }
    }

    @Override public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (!AA_PACKAGE.equals(param.getPackageName()) || !param.isFirstPackage()) return;
        try {
            JSONObject profile = readProfile();
            if (profile.optInt("schemaVersion") != 3) {
                event(Log.WARN, "PROFILE_REJECTED", null, "reason", "unsupported_schema"); return;
            }
            ApplicationInfo app = param.getApplicationInfo();
            // PackageReady exposes ApplicationInfo, not PackageInfo. The version code is
            // bound indirectly by the verified artifact hashes and is supplied by the profile
            // solely to reproduce the canonical fingerprint byte-for-byte.
            long versionCode = profile.getLong("packageVersionCode");
            PackageBuildIdentity identity = BuildFingerprint.computeFromPaths(versionCode, app.sourceDir, app.splitSourceDirs);
            if (!identity.getFingerprintSha256().equalsIgnoreCase(profile.getString("buildFingerprintSha256"))) {
                event(Log.WARN, "PROFILE_REJECTED", null, "reason", "build_fingerprint_mismatch"); return;
            }
            JSONArray mappings = profile.getJSONArray("mappings");
            for (int i = 0; i < mappings.length(); i++) install(param.getClassLoader(), mappings.getJSONObject(i));
            event(Log.INFO, "PROFILE_ACCEPTED", null, "mappings", String.valueOf(mappings.length()), "build", identity.getFingerprintSha256().substring(0, 16));
        } catch (Throwable t) {
            event(Log.ERROR, "PROFILE_FAILED", null, "error", t.getClass().getSimpleName());
            log(Log.ERROR, TAG, "Dynamic profile failed; no further hooks installed", t);
        }
    }

    private void refreshSnapshot(SharedPreferences preferences) {
        Map<String, String> next = new HashMap<>();
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            if (entry.getValue() instanceof String value) next.put(entry.getKey(), value);
        }
        overrideSnapshot.replace(next);
    }

    private void install(ClassLoader loader, JSONObject mapping) {
        String key = mapping.optString("key", "<unknown>");
        try {
            String className = mapping.getString("className");
            String methodName = mapping.getString("methodName");
            String descriptor = mapping.getString("descriptor");
            String preferenceKey = mapping.getString("preferenceKey");
            Class<?> cls = Class.forName(className, false, loader);
            Method method = cls.getDeclaredMethod(methodName);
            if (method.getParameterCount() != 0 || !descriptor.equals(descriptor(method))) {
                event(Log.WARN, "HOOK_SUSPENDED", key, "reason", "changed_signature"); return;
            }
            hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                String encoded = overrideSnapshot.get(preferenceKey);
                if (encoded == null) {
                    Object original = chain.proceed();
                    logReturnOnce(key, "ORIGINAL_RETURNED", "<default>", original);
                    return original;
                }
                try {
                    Object effective = RuntimeOverrideCodec.decode(encoded, method.getReturnType());
                    logReturnOnce(key, "OVERRIDE_RETURNED", encoded, effective);
                    return effective;
                }
                catch (Throwable invalid) {
                    event(Log.WARN, "OVERRIDE_REJECTED", key, "reason", invalid.getClass().getSimpleName());
                    log(Log.WARN, TAG, "Invalid override for " + key + "; original executed", invalid);
                    return chain.proceed();
                }
            });
            event(Log.INFO, "HOOK_INSTALLED", key, "target", className + "." + methodName + descriptor);
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError error) {
            event(Log.WARN, "HOOK_SUSPENDED", key, "reason", error.getClass().getSimpleName());
            log(Log.WARN, TAG, "Suspended incompatible mapping " + key, error);
        } catch (Throwable error) {
            event(Log.ERROR, "HOOK_FAILED", key, "reason", error.getClass().getSimpleName());
            log(Log.ERROR, TAG, "Failed to install mapping " + key, error);
        }
    }

    private void logReturnOnce(String key, String event, String generation, Object value) {
        String token = event + ':' + generation;
        if (token.equals(lastReturnEvent.put(key, token))) return;
        event(Log.INFO, event, key, "value", String.valueOf(value));
    }

    private void event(int priority, String event, String key, String... fields) {
        try {
            JSONObject payload = new JSONObject().put("event", event);
            if (key != null) payload.put("key", key);
            for (int i = 0; i + 1 < fields.length; i += 2) payload.put(fields[i], fields[i + 1]);
            log(priority, TAG, EVENT_PREFIX + payload);
        } catch (Throwable ignored) {
            log(priority, TAG, EVENT_PREFIX + event + (key == null ? "" : " key=" + key));
        }
    }

    private JSONObject readProfile() throws Exception {
        try (ParcelFileDescriptor descriptor = openRemoteFile(PROFILE_FILE);
             FileInputStream input = new FileInputStream(descriptor.getFileDescriptor());
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16384]; int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            return new JSONObject(output.toString("UTF-8"));
        }
    }

    private static String descriptor(Method method) {
        StringBuilder out = new StringBuilder("(");
        for (Class<?> type : method.getParameterTypes()) out.append(typeDescriptor(type));
        return out.append(')').append(typeDescriptor(method.getReturnType())).toString();
    }

    private static String typeDescriptor(Class<?> type) {
        if (type.isPrimitive()) {
            if (type == void.class) return "V"; if (type == boolean.class) return "Z"; if (type == byte.class) return "B";
            if (type == short.class) return "S"; if (type == char.class) return "C"; if (type == int.class) return "I";
            if (type == long.class) return "J"; if (type == float.class) return "F"; if (type == double.class) return "D";
        }
        if (type.isArray()) return type.getName().replace('.', '/');
        return "L" + type.getName().replace('.', '/') + ";";
    }

}
