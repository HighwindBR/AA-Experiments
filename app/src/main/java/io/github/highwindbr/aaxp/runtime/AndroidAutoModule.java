package io.github.highwindbr.aaxp.runtime;

import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.content.Intent;
import android.content.ComponentName;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import io.github.highwindbr.aaxp.core.ExperimentalBooleanPolicy;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import android.content.pm.ApplicationInfo;
import io.github.highwindbr.aaxp.core.BuildFingerprint;
import io.github.highwindbr.aaxp.core.PackageBuildIdentity;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class AndroidAutoModule extends XposedModule {
    private static final String AA_PACKAGE = "com.google.android.projection.gearhead";
    private static final String PREF_GROUP = "dynamic_overrides_v2";
    private static final String TAG = "AAExperiments";
    private static final String EVENT_PREFIX = "AAX_EVENT ";
    private final OverrideSnapshot overrideSnapshot = new OverrideSnapshot();
    private final Map<String, String> lastReturnEvent = new ConcurrentHashMap<>();
    private final Map<String, Boolean> probedCalls = new ConcurrentHashMap<>();
    private final ExperimentalEventLimiter experimentalEvents = new ExperimentalEventLimiter();
    private SharedPreferences remotePreferences;
    private volatile String moduleProcessName;
    private volatile MediaStartupLaunchGate mediaLaunchGate;
    private volatile MediaStartupAutoGate mediaAutoGate;
    private volatile Handler mediaLaunchHandler;
    private volatile Method mediaControllerFactory;
    private volatile Method mediaControllerStart;
    private volatile Constructor<?> mediaActivityOptionsConstructor;
    private volatile boolean mediaLaunchAvailable;
    private boolean mediaPollScheduled;
    private final Runnable mediaCommandPoll = new Runnable() {
        @Override public void run() {
            mediaPollScheduled = false;
            if (mediaLaunchGate == null || !mediaLaunchGate.isReady() ||
                    !MediaStartupTrace.enabled(overrideSnapshot.get(MediaStartupTrace.KEY)))
                return;
            checkMediaLaunchRequest();
            startMediaPolling();
        }
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener =
            (preferences, key) -> {
                refreshSnapshot(preferences);
                if (MediaStartupLaunchGate.REQUEST_KEY.equals(key)) requestMediaLaunchCheck();
            };

    @Override public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        moduleProcessName = param.getProcessName();
        event(Log.INFO, "MODULE_LOADED", null, "process", moduleProcessName, "api", String.valueOf(getApiVersion()));
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
            JSONArray probes = profile.optJSONArray("probes");
            if (probes != null) for (int i = 0; i < probes.length(); i++)
                installProbe(param.getClassLoader(), probes.getJSONObject(i), identity.getBaseSha256());
            JSONArray experimental = profile.optJSONArray("experimental");
            if (experimental != null) for (int i = 0; i < experimental.length(); i++)
                installExperimental(param.getClassLoader(), experimental.getJSONObject(i), identity.getFingerprintSha256());
            JSONArray advanced = profile.optJSONArray("advanced");
            if (advanced != null) for (int i = 0; i < advanced.length(); i++) {
                JSONObject candidate = advanced.getJSONObject(i);
                boolean conflict = false;
                for (int j = 0; j < mappings.length(); j++) {
                    JSONObject normal = mappings.getJSONObject(j);
                    if (normal.optString("className").equals(candidate.optString("className")) &&
                            normal.optString("methodName").equals(candidate.optString("methodName")) &&
                            normal.optString("descriptor").equals(candidate.optString("descriptor"))) {
                        conflict = true; break;
                    }
                }
                if (conflict) {
                    event(Log.WARN, "ADVANCED_REJECTED", candidate.optString("key"),
                            "reason", "duplicate_normal_hook");
                } else {
                    installAdvanced(param.getClassLoader(), candidate,
                            identity.getFingerprintSha256(), identity.getBaseSha256());
                }
            }
            if (profile.optBoolean("videoTimingTrace", false)) {
                installVideoTimingTrace(param.getClassLoader(), identity.getBaseSha256());
            }
            if (profile.optBoolean("mediaStartupTrace", false)) {
                // Use the process identity already provided by LibXposed rather
                // than an Android API-level-dependent process lookup.
                if (MediaStartupTrace2.isProjectionProcess(moduleProcessName)) {
                    installMediaStartupTrace2(param.getClassLoader(), identity.getBaseSha256());
                }
            }
            event(Log.INFO, "PROFILE_ACCEPTED", null, "mappings", String.valueOf(mappings.length()),
                    "probes", String.valueOf(probes == null ? 0 : probes.length()),
                    "experimental", String.valueOf(experimental == null ? 0 : experimental.length()),
                    "advanced", String.valueOf(advanced == null ? 0 : advanced.length()),
                    "build", identity.getFingerprintSha256().substring(0, 16));
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

    /**
     * Explicit one-key experimental BOOLEAN override. The remote snapshot is checked on
     * each invocation so restoring/pausing fails open even in a running Android Auto process.
     * It never accepts a raw protobuf or unverified getter type.
     */
    private void installExperimental(ClassLoader loader, JSONObject mapping, String buildFingerprint) {
        String key = mapping.optString("key", "<unknown>");
        if (!ExperimentalBooleanPolicy.KEY.equals(key)) {
            event(Log.WARN, "EXPERIMENT_REJECTED", key, "reason", "not_allowlisted");
            return;
        }
        try {
            String className = mapping.getString("className");
            String methodName = mapping.getString("methodName");
            String descriptor = mapping.getString("descriptor");
            if (!"()Z".equals(descriptor)) {
                event(Log.WARN, "EXPERIMENT_REJECTED", key, "reason", "invalid_abi"); return;
            }
            Class<?> cls = Class.forName(className, false, loader);
            Method method = cls.getDeclaredMethod(methodName);
            if (method.getParameterCount() != 0 || !"()Z".equals(descriptor(method))) {
                event(Log.WARN, "EXPERIMENT_REJECTED", key, "reason", "changed_signature"); return;
            }
            // Installation proves method resolution, not invocation. Report snapshot
            // availability independently before the first getter call.
            hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                eventOnce(Log.INFO, "EXPERIMENT_GETTER_INVOKED", key, "first");
                ExperimentalBooleanDecision.Result decision = inspectExperimentalSnapshot(
                        key, buildFingerprint, className, methodName, descriptor);
                if (!decision.shouldOverride()) {
                    eventOnce(Log.WARN, "EXPERIMENT_FALLBACK", key,
                            decision.status.name(), "reason", decision.status.name());
                    return chain.proceed();
                }
                String value = String.valueOf(decision.value);
                eventOnce(Log.INFO, "EXPERIMENT_OVERRIDE_RETURNED", key, value,
                        "source", "EXPERIMENTAL", "value", value);
                return decision.value;
            });
            inspectExperimentalSnapshot(key, buildFingerprint, className, methodName, descriptor);
            event(Log.INFO, "EXPERIMENT_HOOK_INSTALLED", key, "target", className + "." + methodName + descriptor);
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError failure) {
            event(Log.WARN, "EXPERIMENT_REJECTED", key, "reason", failure.getClass().getSimpleName());
        } catch (Throwable failure) {
            event(Log.ERROR, "EXPERIMENT_HOOK_FAILED", key, "reason", failure.getClass().getSimpleName());
            log(Log.ERROR, TAG, "Experimental getter hook failed", failure);
        }
    }

    private ExperimentalBooleanDecision.Result inspectExperimentalSnapshot(
            String key, String build, String cls, String method, String descriptor) {
        ExperimentalBooleanDecision.Result decision = ExperimentalBooleanDecision.evaluate(
                overrideSnapshot.get(ExperimentalBooleanPolicy.RECORD_KEY), key, build,
                cls, method, descriptor);
        // No snapshot JSON, user data, or app state is ever written to the logs.
        eventOnce(Log.INFO, "EXPERIMENT_SNAPSHOT_LOADED", key,
                decision.status.name(), "status", decision.status.name());
        return decision;
    }

    private void eventOnce(int priority, String eventName, String key, String state, String... fields) {
        if (experimentalEvents.first(eventName, key, state)) {
            event(priority, eventName, key, fields);
        }
    }

    /** No arbitrary method or type: every candidate must be present in the validated
     * build-scoped profile, use a supported ABI, and carry an exact matching remote
     * record. Every failed decision calls through to the original implementation. */
    private void installAdvanced(ClassLoader loader, JSONObject mapping,
            String buildFingerprint, String baseSha256) {
        String key = mapping.optString("key", "<unknown>");
        try {
            String prefKey = mapping.getString("preferenceKey");
            String expectedPrefKey = io.github.highwindbr.aaxp.core.AdvancedOverridePolicy.INSTANCE.preferenceKey(key);
            if (!expectedPrefKey.equals(prefKey) || !key.contains("__") ||
                    io.github.highwindbr.aaxp.discovery.SafetyPolicy.INSTANCE.blockedReason(key) != null) {
                event(Log.WARN, "ADVANCED_REJECTED", key, "reason", "policy_denied");
                return;
            }
            String cls = mapping.getString("className");
            String methodName = mapping.getString("methodName");
            String abi = mapping.getString("descriptor");
            String type = mapping.getString("type");
            if (LongHandoffRuntime.KEY.equals(key) &&
                    (!LongHandoffRuntime.isTarget(key, cls, methodName, abi, type) ||
                     !LongHandoffRuntime.isExpectedBase(baseSha256))) {
                event(Log.WARN, "ADVANCED_REJECTED", key, "reason", "handoff_target_or_build_mismatch");
                return;
            }
            if (!abi.equals(AdvancedRuntimeDecision.expectedDescriptor(type))) {
                event(Log.WARN, "ADVANCED_REJECTED", key, "reason", "unsupported_abi"); return;
            }
            Class<?> owner = Class.forName(cls, false, loader);
            Method getter = owner.getDeclaredMethod(methodName);
            if (getter.getParameterCount() != 0 || !abi.equals(descriptor(getter))) {
                event(Log.WARN, "ADVANCED_REJECTED", key, "reason", "changed_signature"); return;
            }
            hook(getter).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                eventOnce(Log.INFO, "ADVANCED_GETTER_INVOKED", key, "first");
                String rawRecord = overrideSnapshot.get(prefKey);
                if (LongHandoffRuntime.KEY.equals(key)) {
                    // The normal LONG:3 preference remains stored throughout
                    // the handoff. A stale Advanced hook must still return 3
                    // after Pause/Restore, even before a process restart.
                    String normal = overrideSnapshot.get(
                            io.github.highwindbr.aaxp.core.DynamicOverrideCodec.INSTANCE.preferenceKey(key));
                    String name = overrideSnapshot.get(
                            io.github.highwindbr.aaxp.core.DynamicOverrideCodec.INSTANCE.namePreferenceKey(key));
                    if (!LongHandoffRuntime.baselineIntact(normal, name) ||
                            !LongHandoffRuntime.activeSnapshot(rawRecord,
                                    buildFingerprint, cls, methodName, abi, type)) {
                        if (LongHandoffRuntime.baselineIntact(normal, name)) {
                            eventOnce(Log.INFO, "LONG_HANDOFF_BASELINE_RETURNED", key, "normal:3",
                                    "type", "LONG", "value", "3");
                            return LongHandoffRuntime.BASELINE_VALUE;
                        }
                        eventOnce(Log.WARN, "LONG_HANDOFF_BASELINE_MISSING", key, "unsafe",
                                "reason", "original_normal_value_missing");
                        return chain.proceed();
                    }
                }
                AdvancedRuntimeDecision.Result value = AdvancedRuntimeDecision.evaluate(
                        rawRecord, key, buildFingerprint, cls, methodName, abi, type);
                if (!value.shouldOverride()) {
                    eventOnce(Log.WARN, "ADVANCED_FALLBACK", key, value.status.name(),
                            "reason", value.status.name());
                    return chain.proceed();
                }
                // One return event per key/value/process. We never log STRING contents.
                String reportedValue = "STRING".equals(type) ? "<redacted>" : String.valueOf(value.value);
                eventOnce(Log.INFO, "ADVANCED_OVERRIDE_RETURNED", key, type + ":" + reportedValue,
                        "type", type, "value", reportedValue);
                return value.value;
            });
            event(Log.INFO, "ADVANCED_HOOK_INSTALLED", key, "target", cls + "." + methodName + abi);
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError missing) {
            event(Log.WARN, "ADVANCED_REJECTED", key, "reason", missing.getClass().getSimpleName());
        } catch (Throwable failure) {
            event(Log.ERROR, "ADVANCED_HOOK_FAILED", key, "reason", failure.getClass().getSimpleName());
            log(Log.ERROR, TAG, "Advanced hook installation failed for " + key, failure);
        }
    }

    /**
     * Optional diagnostic for AA 18.0, proven by the exact base APK hash.
     * Hooks are observation-only: the original constructor and method always
     * execute via chain.proceed() without modifying arguments or return values.
     * Only protocol versions and the selection state are logged (no vehicle IDs).
     */
    private void installVideoTimingTrace(ClassLoader loader, String actualBaseSha256) {
        if (!VideoTimingProtocolTrace.isSupported(actualBaseSha256)) {
            event(Log.WARN, "VTC_TRACE_NOT_INSTALLED", null, "reason", "base_sha_mismatch");
            return;
        }
        try {
            Class<?> owner = Class.forName(VideoTimingProtocolTrace.CLASS, false, loader);
            Class<?> carInfo = Class.forName(VideoTimingProtocolTrace.CAR_INFO_CLASS, false, loader);
            Constructor<?> ctor = owner.getDeclaredConstructor(carInfo);
            Method selector = owner.getDeclaredMethod(VideoTimingProtocolTrace.SELECTOR_METHOD);
            if (!VideoTimingProtocolTrace.SELECTOR_DESCRIPTOR.equals(descriptor(selector))) {
                event(Log.WARN, "VTC_TRACE_NOT_INSTALLED", null, "reason", "selector_signature_mismatch");
                return;
            }
            Field majorField = carInfo.getDeclaredField("e");
            Field minorField = carInfo.getDeclaredField("f");
            Field modeField = owner.getDeclaredField("a");
            Field normalField = owner.getDeclaredField("b");
            majorField.setAccessible(true);
            minorField.setAccessible(true);
            modeField.setAccessible(true);
            normalField.setAccessible(true);

            hook(ctor).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                Object result = chain.proceed();
                if (!VideoTimingProtocolTrace.enabled(overrideSnapshot.get(VideoTimingProtocolTrace.KEY))) return result;
                try {
                    Object info = chain.getArg(0);
                    int major = majorField.getInt(info);
                    int minor = minorField.getInt(info);
                    eventOnce(Log.INFO, "VTC_HU_PROTOCOL_OBSERVED", "VideoTimingController",
                            major + "." + minor, "major", String.valueOf(major),
                            "minor", String.valueOf(minor),
                            "expectedBranch", VideoTimingProtocolTrace.protocolBranch(major));
                } catch (Throwable observationError) {
                    eventOnce(Log.WARN, "VTC_TRACE_READ_FAILED", "CarInfo",
                            observationError.getClass().getSimpleName(),
                            "reason", observationError.getClass().getSimpleName());
                }
                return result;
            });

            hook(selector).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                if (VideoTimingProtocolTrace.enabled(overrideSnapshot.get(VideoTimingProtocolTrace.KEY))) {
                    eventOnce(Log.INFO, "VTC_SELECTOR_INVOKED", "VideoTimingController", "first");
                    try {
                        Object instance = chain.getThisObject();
                        int mode = modeField.getInt(instance);
                        boolean normal = normalField.getBoolean(instance);
                        String branch = VideoTimingProtocolTrace.selectorBranch(mode, normal);
                        eventOnce(Log.INFO, "VTC_BRANCH_SELECTED", "VideoTimingController",
                                branch, "branch", branch, "mode", String.valueOf(mode),
                                "normalFlag", String.valueOf(normal));
                    } catch (Throwable observationError) {
                        eventOnce(Log.WARN, "VTC_TRACE_READ_FAILED", "Liyo.d",
                                observationError.getClass().getSimpleName(),
                                "reason", observationError.getClass().getSimpleName());
                    }
                }
                return chain.proceed();
            });
            event(Log.INFO, "VTC_TRACE_INSTALLED", null,
                    "target", "iyo.<init>(CarInfo) + iyo.d()Lxxe;",
                    "mode", "READ_ONLY", "build", actualBaseSha256.substring(0, 16));
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException | LinkageError incompatible) {
            event(Log.WARN, "VTC_TRACE_NOT_INSTALLED", null, "reason", incompatible.getClass().getSimpleName());
        } catch (Throwable failure) {
            event(Log.ERROR, "VTC_TRACE_HOOK_FAILED", null, "reason", failure.getClass().getSimpleName());
            log(Log.ERROR, TAG, "VideoTiming protocol observation hook failed", failure);
        }
    }


    /**
     * Beta5 preflight2: inspect precisely identified callbacks observed in
     * real projection logs. No arguments or return values are accessed or
     * changed. The original method is always called once via chain.proceed().
     */
    private void installMediaStartupTrace2(ClassLoader loader, String baseSha256) {
        if (!MediaStartupTrace.isSupported(baseSha256)) {
            event(Log.WARN, "MEDIA_PF2_NOT_INSTALLED", null, "reason", "base_sha_mismatch");
            return;
        }
        final MediaStartupTrace2.Budget budget = new MediaStartupTrace2.Budget();
        final MediaStartupLaunchGate gate = MediaStartupLaunchGate.create(
                overrideSnapshot.get(MediaStartupLaunchGate.REQUEST_KEY));
        mediaLaunchGate = gate;
        mediaAutoGate = new MediaStartupAutoGate();
        mediaLaunchHandler = new Handler(Looper.getMainLooper());
        int installed = 0;
        boolean readinessInstalled = false;
        boolean endInstalled = false;
        boolean teardownInstalled = false;
        boolean activityInstalled = false;
        boolean launchRouteInstalled = false;
        for (MediaStartupTrace2.Target target : MediaStartupTrace2.TARGETS) {
            try {
                Class<?> owner = Class.forName(target.owner, false, loader);
                Class<?>[] parameters;
                switch (target.stage) {
                    case "INTENT_PROCESSED":
                    case "START_CAR_ACTIVITY":
                        parameters = new Class<?>[]{android.content.Intent.class,
                                Class.forName("rgi", false, loader)};
                        break;
                    case "INTENT_TARGET_STORED":
                        parameters = new Class<?>[]{android.content.Intent.class};
                        break;
                    case "MEDIA_APP_CHANGED":
                        parameters = new Class<?>[]{android.content.ComponentName.class};
                        break;
                    default:
                        parameters = new Class<?>[0];
                        break;
                }
                Method method = owner.getDeclaredMethod(target.method, parameters);
                if (!target.descriptor.equals(descriptor(method))) {
                    event(Log.WARN, "MEDIA_PF2_TARGET_REJECTED", null,
                            "stage", target.stage, "reason", "signature_mismatch");
                    continue;
                }
                hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            boolean armed = MediaStartupTrace.enabled(
                                    overrideSnapshot.get(MediaStartupTrace.KEY));
                            if (armed) {
                                int number = budget.take(target.stage);
                                if (number > 0) {
                                    event(Log.INFO, "MEDIA_PF2_STAGE_ENTER", null,
                                            "stage", target.stage, "ordinal", String.valueOf(number),
                                            "mode", "READ_ONLY");
                                }
                            }
                            // Teardown invalidates all outstanding requests before
                            // Android Auto starts shutting down its own session.
                            if ("PROJECTION_END".equals(target.stage) ||
                                    "PROJECTION_TEARDOWN".equals(target.stage)) {
                                gate.onEnded();
                                mediaAutoGate.onEnded();
                            }
                            Object original = chain.proceed();
                            if ("PROJECTION_READY".equals(target.stage) &&
                                    MediaStartupTrace.enabled(
                                            overrideSnapshot.get(MediaStartupTrace.KEY))) {
                                gate.onReady();
                                if (mediaAutoGate.onReady(
                                        MediaStartupAutoGate.enabled(overrideSnapshot.get(
                                                MediaStartupAutoGate.KEY)), SystemClock.elapsedRealtime())) {
                                    event(Log.INFO, "MEDIA_PF4_SESSION_ARMED", null,
                                            "auto", MediaStartupAutoGate.enabled(overrideSnapshot.get(
                                                    MediaStartupAutoGate.KEY)) ? "ON" : "OFF");
                                }
                                startMediaPolling();
                            } else if ("ACTIVITY_START_REQUESTED".equals(target.stage) && armed) {
                                gate.onActivityStart();
                                int sessionId = mediaAutoGate.onFirstActivity(SystemClock.elapsedRealtime());
                                if (sessionId >= 0) scheduleAutoSpotify(sessionId);
                            } else if ("MEDIA_APP_CHANGED".equals(target.stage) && armed) {
                                try {
                                    Object value = chain.getArg(0);
                                    mediaAutoGate.onMediaChanged(value instanceof ComponentName &&
                                            MediaStartupLaunchGate.SPOTIFY_PACKAGE.equals(
                                                    ((ComponentName) value).getPackageName()));
                                } catch (Throwable ignored) {
                                    mediaAutoGate.onMediaChanged(false);
                                }
                            } else if ("START_CAR_ACTIVITY".equals(target.stage) && armed) {
                                mediaAutoGate.onNavigation();
                            }
                            // Callback return is not proof of physical HU visibility.
                            if ("PROJECTION_READY".equals(target.stage) &&
                                    MediaStartupTrace.enabled(
                                            overrideSnapshot.get(MediaStartupTrace.KEY))) {
                                int number = budget.take("PROJECTION_READY_RETURN");
                                if (number > 0) {
                                    event(Log.INFO, "MEDIA_PF2_STAGE_RETURN", null,
                                            "stage", target.stage, "ordinal", String.valueOf(number));
                                }
                            }
                            return original;
                        });
                installed++;
                if ("PROJECTION_READY".equals(target.stage)) readinessInstalled = true;
                if ("PROJECTION_END".equals(target.stage)) endInstalled = true;
                if ("PROJECTION_TEARDOWN".equals(target.stage)) teardownInstalled = true;
                if ("ACTIVITY_START_REQUESTED".equals(target.stage)) activityInstalled = true;
                if ("START_CAR_ACTIVITY".equals(target.stage)) launchRouteInstalled = true;
                event(Log.INFO, "MEDIA_PF2_HOOK_INSTALLED", null,
                        "stage", target.stage, "mode", "READ_ONLY");
            } catch (ClassNotFoundException | NoSuchMethodException | LinkageError incompatible) {
                event(Log.WARN, "MEDIA_PF2_TARGET_REJECTED", null,
                        "stage", target.stage, "reason", incompatible.getClass().getSimpleName());
            } catch (Throwable failure) {
                event(Log.ERROR, "MEDIA_PF2_TARGET_FAILED", null,
                        "stage", target.stage, "reason", failure.getClass().getSimpleName());
            }
        }
        event(Log.INFO, "MEDIA_PF2_INSTALLATION_COMPLETE", null,
                "installed", String.valueOf(installed),
                "expected", String.valueOf(MediaStartupTrace2.TARGETS.length),
                "base", baseSha256.substring(0, 16));
        if (readinessInstalled && endInstalled && teardownInstalled &&
                activityInstalled && launchRouteInstalled) {
            installMediaLaunchProof(loader);
        } else {
            event(Log.WARN, "MEDIA_PF3_DISABLED", null, "reason", "required_hook_missing");
        }
    }

    /**
     * Resolve all required ABI before allowing any request. The DI singleton
     * nfh.a() and no-options rgi() constructor were inspected in AA 18.1 DEX.
     * No invocation occurs during installation; no automatic launch path exists.
     */
    private void installMediaLaunchProof(ClassLoader loader) {
        try {
            Class<?> owner = Class.forName("nfh", false, loader);
            Class<?> options = Class.forName("rgi", false, loader);
            Method factory = owner.getDeclaredMethod("a");
            Method launch = owner.getDeclaredMethod("k", Intent.class, options);
            Constructor<?> ctor = options.getDeclaredConstructor();
            if (!Modifier.isStatic(factory.getModifiers()) ||
                    !owner.equals(factory.getReturnType()) ||
                    !"()Lnfh;".equals(descriptor(factory)) ||
                    !"(Landroid/content/Intent;Lrgi;)V".equals(descriptor(launch)) ||
                    ctor.getParameterCount() != 0) {
                event(Log.WARN, "MEDIA_PF3_DISABLED", null, "reason", "abi_mismatch");
                return;
            }
            mediaControllerFactory = factory;
            mediaControllerStart = launch;
            mediaActivityOptionsConstructor = ctor;
            mediaLaunchAvailable = true;
            event(Log.INFO, "MEDIA_PF3_PROOF_ARMED", null, "mode", "MANUAL_PLUS_OPT_IN_AUTO",
                    "target", "SPOTIFY", "ttlMs",
                    String.valueOf(MediaStartupLaunchGate.TTL_MILLIS));
        } catch (ReflectiveOperationException | LinkageError missing) {
            event(Log.WARN, "MEDIA_PF3_DISABLED", null,
                    "reason", missing.getClass().getSimpleName());
        } catch (Throwable unexpected) {
            event(Log.ERROR, "MEDIA_PF3_DISABLED", null,
                    "reason", unexpected.getClass().getSimpleName());
        }
    }

    /**
     * PF4: schedule exactly one attempt for each first projected activity.
     * The session token invalidates callbacks after disconnect/reconnect.
     */
    private void scheduleAutoSpotify(int sessionId) {
        Handler handler = mediaLaunchHandler;
        if (handler == null || !mediaLaunchAvailable) return;
        handler.postDelayed(() -> attemptAutoSpotify(sessionId),
                MediaStartupAutoGate.SETTLE_MILLIS);
        event(Log.INFO, "MEDIA_PF4_AUTO_SCHEDULED", null,
                "delayMs", String.valueOf(MediaStartupAutoGate.SETTLE_MILLIS));
    }

    private void attemptAutoSpotify(int scheduledSession) {
        if (Looper.myLooper() != Looper.getMainLooper() || mediaAutoGate == null ||
                !mediaLaunchAvailable || !MediaStartupTrace.enabled(
                        overrideSnapshot.get(MediaStartupTrace.KEY))) return;
        MediaStartupAutoGate.Decision decision = mediaAutoGate.consume(scheduledSession,
                SystemClock.elapsedRealtime(), MediaStartupAutoGate.enabled(
                        overrideSnapshot.get(MediaStartupAutoGate.KEY)));
        if (decision != MediaStartupAutoGate.Decision.DISPATCH) {
            event(Log.INFO, "MEDIA_PF4_AUTO_SKIPPED", null, "reason", decision.name());
            return;
        }
        if (mediaLaunchGate == null || !mediaLaunchGate.reserveForAutomatic()) {
            event(Log.INFO, "MEDIA_PF4_AUTO_SKIPPED", null, "reason", "manual_budget_used");
            return;
        }
        event(Log.INFO, "MEDIA_PF4_AUTO_ATTEMPT", null, "target", "SPOTIFY");
        try {
            dispatchSpotify(scheduledSession, false);
            event(Log.INFO, "MEDIA_PF4_AUTO_DISPATCHED", null,
                    "result", "RETURNED", "visible", "UNVERIFIED");
        } catch (Throwable failed) {
            Throwable cause = failed instanceof InvocationTargetException &&
                    failed.getCause() != null ? failed.getCause() : failed;
            event(Log.ERROR, "MEDIA_PF4_AUTO_FAILED", null,
                    "reason", cause.getClass().getSimpleName());
        }
    }

    /** Called by remote-preference listener; never execute car activity on binder threads. */
    private void requestMediaLaunchCheck() {
        Handler handler = mediaLaunchHandler;
        if (handler != null) handler.post(this::checkMediaLaunchRequest);
    }

    private void startMediaPolling() {
        Handler handler = mediaLaunchHandler;
        if (handler == null) return;
        // The lifecycle hook can arrive on a binder thread. Serialize the
        // poll scheduler on the AA main thread to avoid duplicate timers.
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(this::startMediaPolling);
            return;
        }
        if (mediaPollScheduled) return;
        mediaPollScheduled = true;
        handler.postDelayed(mediaCommandPoll, 1000);
    }

    /** Fallback polling covers LSPosed implementations without remote change broadcasts. */
    private void checkMediaLaunchRequest() {
        if (!mediaLaunchAvailable || mediaLaunchGate == null ||
                !MediaStartupTrace.enabled(overrideSnapshot.get(MediaStartupTrace.KEY)))
            return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            requestMediaLaunchCheck();
            return;
        }
        try {
            SharedPreferences prefs = remotePreferences;
            if (prefs == null) return;
            String request = prefs.getString(MediaStartupLaunchGate.REQUEST_KEY, null);
            MediaStartupLaunchGate.Decision decision =
                    mediaLaunchGate.consider(request, SystemClock.elapsedRealtime());
            if (decision == MediaStartupLaunchGate.Decision.NO_CHANGE) return;
            if (decision != MediaStartupLaunchGate.Decision.ACCEPTED) {
                event(Log.WARN, "MEDIA_PF3_REQUEST_REJECTED", null, "reason", decision.name());
                return;
            }
            final int activeSession = mediaLaunchGate.session();
            if (!mediaLaunchGate.stillReadyInSession(activeSession)) {
                event(Log.WARN, "MEDIA_PF3_REQUEST_REJECTED", null, "reason", "session_ended");
                return;
            }
            mediaAutoGate.onManualAttempt();
            event(Log.INFO, "MEDIA_PF3_REQUEST_ACCEPTED", null,
                    "mode", "MANUAL_ONESHOT", "target", "SPOTIFY");
            try {
                dispatchSpotify(activeSession, true);
                // Successful return is NOT proof the HU displayed Spotify.
                event(Log.INFO, "MEDIA_PF3_REQUEST_DISPATCHED", null,
                        "result", "RETURNED", "visible", "UNVERIFIED");
            } catch (Throwable error) {
                Throwable cause = error instanceof InvocationTargetException &&
                        error.getCause() != null ? error.getCause() : error;
                event(Log.ERROR, "MEDIA_PF3_LAUNCH_FAILED", null,
                        "reason", cause.getClass().getSimpleName());
            }
        } catch (Throwable error) {
            event(Log.ERROR, "MEDIA_PF3_REQUEST_FAILED", null,
                    "reason", error.getClass().getSimpleName());
        }
    }

    private void dispatchSpotify(int sessionId, boolean manual) throws Exception {
        if (mediaLaunchGate == null || !mediaLaunchGate.stillReadyInSession(sessionId))
            throw new IllegalStateException("Projection ended");
        Object controller = mediaControllerFactory.invoke(null);
        if (controller == null || !mediaLaunchGate.stillReadyInSession(sessionId))
            throw new IllegalStateException("Controller unavailable or session ended");
        Object options = mediaActivityOptionsConstructor.newInstance();
        Intent intent = new Intent(MediaStartupLaunchGate.MEDIA_ACTION);
        intent.setComponent(new ComponentName(
                MediaStartupLaunchGate.SPOTIFY_PACKAGE,
                MediaStartupLaunchGate.SPOTIFY_SERVICE));
        // The inspected AA controller remains responsible for routing and policy.
        mediaControllerStart.invoke(controller, intent, options);
    }

    private void installProbe(ClassLoader loader, JSONObject mapping, String baseSha256) {
        String key = mapping.optString("key", "<unknown>");
        // The read-only diagnostic feature is intentionally narrower than a future
        // Advanced Overrides mode. The runtime independently enforces the cohort.
        if (!("PhoneThemeFeature__enable_dynamic_app_icon_shape_by_manufacturer_kill_switch".equals(key)
                || "CarAppLibrary__recreate_map_view_context_on_config_change_kill_switch".equals(key)
                || "WirelessLatencyMonitor__warning_threshold_ms".equals(key)
                || DoubleGetterProbeRuntime.isDoubleTarget(key))) {
            event(Log.WARN, "PROBE_REJECTED", key, "reason", "not_allowlisted");
            return;
        }
        String expectedDescriptor = DoubleGetterProbeRuntime.isDoubleTarget(key) ? "()D" :
                key.equals("WirelessLatencyMonitor__warning_threshold_ms") ? "()J" : "()Z";
        try {
            String className = mapping.getString("className");
            String methodName = mapping.getString("methodName");
            String descriptor = mapping.getString("descriptor");
            if (!expectedDescriptor.equals(descriptor)) {
                event(Log.WARN, "PROBE_REJECTED", key, "reason", "type_mismatch");
                return;
            }
            if (DoubleGetterProbeRuntime.isDoubleTarget(key) &&
                    !DoubleGetterProbeRuntime.permitted(key, className, methodName,
                            descriptor, baseSha256)) {
                event(Log.WARN, "PROBE_REJECTED", key, "reason", "double_target_or_build_mismatch");
                return;
            }
            Class<?> cls = Class.forName(className, false, loader);
            Method method = cls.getDeclaredMethod(methodName);
            if (method.getParameterCount() != 0 || !descriptor.equals(descriptor(method))) {
                event(Log.WARN, "PROBE_REJECTED", key, "reason", "changed_signature");
                return;
            }
            hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                if (probedCalls.putIfAbsent(key, Boolean.TRUE) == null) {
                    // Log before proceeding: this proves the original getter was reached,
                    // without logging its value or ever changing its return.
                    event(Log.INFO, "PROBE_INVOKED", key);
                }
                return chain.proceed();
            });
            event(Log.INFO, "PROBE_HOOK_INSTALLED", key, "target", className + "." + methodName + descriptor);
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError failure) {
            event(Log.WARN, "PROBE_REJECTED", key, "reason", failure.getClass().getSimpleName());
        } catch (Throwable failure) {
            event(Log.ERROR, "PROBE_FAILED", key, "reason", failure.getClass().getSimpleName());
            log(Log.ERROR, TAG, "Read-only probe failed for " + key, failure);
        }
    }

    private void logReturnOnce(String key, String event, String generation, Object value) {
        String token = event + ':' + generation;
        if (token.equals(lastReturnEvent.put(key, token))) return;
        // Normal STRING overrides may contain personal or provider data. Never log contents.
        event(Log.INFO, event, key, "value", value instanceof String ? "<redacted>" : String.valueOf(value));
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
        SharedPreferences prefs = remotePreferences != null
                ? remotePreferences : getRemotePreferences(PREF_GROUP);
        String active = prefs.getString(ProfileSlotPolicy.POINTER_KEY, null);
        Throwable lastFailure = null;
        for (String candidate : ProfileSlotPolicy.readOrder(active)) {
            try (ParcelFileDescriptor descriptor = openRemoteFile(candidate);
                 FileInputStream input = new FileInputStream(descriptor.getFileDescriptor());
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[16384];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    if (output.size() > ProfileSlotPolicy.MAX_PROFILE_BYTES)
                        throw new IllegalArgumentException("Profile too large");
                }
                JSONObject profile = new JSONObject(output.toString("UTF-8"));
                // A wrong-type mappings field is rejected by getJSONArray.
                profile.getJSONArray("mappings");
                if (profile.getInt("schemaVersion") != 3 ||
                        profile.getString("buildFingerprintSha256").isEmpty())
                    throw new IllegalArgumentException("Invalid profile header");
                if (active != null && !candidate.equals(active))
                    event(Log.WARN, "PROFILE_FALLBACK", null, "source", candidate);
                event(Log.INFO, "PROFILE_SOURCE", null, "file", candidate);
                return profile;
            } catch (Exception error) {
                lastFailure = error;
            }
        }
        throw new IllegalStateException("No valid profile slot or legacy profile", lastFailure);
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
