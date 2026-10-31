package io.github.highwindbr.aaxp.runtime;

import org.json.JSONObject;
import org.json.JSONException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable per-build role map. Hooks still require runtime ABI and explicit auto opt-in. */
public final class MediaStartupLabProfile {
    public static final String MODE = "MANUAL_PLUS_OPT_IN_AUTO";
    public static final String LEGACY_MODE = "MANUAL_ONLY";
    public final String mode;
    public final Map<String, Target> targets;
    public final String fingerprint;
    public final String baseSha;

    public static final class Target {
        public final String owner;
        public final String name;
        public final String descriptor;
        Target(String raw) {
            int arrow = raw.indexOf("->");
            int paren = raw.indexOf('(', arrow + 2);
            if (arrow < 3 || paren <= arrow + 2 || !raw.startsWith("L") ||
                    raw.charAt(arrow - 1) != ';' || !raw.endsWith("V") &&
                    !raw.substring(paren).matches("\\(\\).*")) {
                // Descriptor validation below makes this only a cheap bound.
                throw new IllegalArgumentException("Malformed role reference");
            }
            owner = raw.substring(0, arrow);
            name = raw.substring(arrow + 2, paren);
            descriptor = raw.substring(paren);
            if (owner.contains(" ") || name.contains(" ") ||
                    !descriptor.startsWith("(") || !descriptor.contains(")")) {
                throw new IllegalArgumentException("Malformed role descriptor");
            }
        }
        public String javaOwner() { return owner.substring(1, owner.length() - 1).replace('/', '.'); }
        public String reference() { return owner + "->" + name + descriptor; }
    }

    private MediaStartupLabProfile(String fingerprint, String baseSha, String mode, Map<String, Target> values) {
        this.fingerprint = fingerprint; this.baseSha = baseSha; this.mode = mode;
        this.targets = java.util.Collections.unmodifiableMap(values);
    }

    public Target role(String name) { return targets.get(name); }

    public static MediaStartupLabProfile parse(JSONObject data, String expectedBase,
                                                String expectedFingerprint) throws JSONException {
        if (data == null ||
                !(MODE.equals(data.optString("mode")) || LEGACY_MODE.equals(data.optString("mode"))) ||
                !expectedBase.equalsIgnoreCase(data.optString("baseSha256")) ||
                !expectedFingerprint.equalsIgnoreCase(data.optString("buildFingerprintSha256")))
            throw new IllegalArgumentException("Build binding/mode mismatch");
        JSONObject json = data.getJSONObject("roles");
        String[] required = {
                "projection.ready", "projection.end", "projection.teardown",
                "launch.controllerFactory", "launch.startCarActivity",
                "launch.optionsNoArgConstructor", "launch.auxiliary",
                "media.selectedApp"
        };
        Map<String, Target> map = new LinkedHashMap<>();
        for (String key : required) map.put(key, new Target(json.getString(key)));
        // Optional for manual-only profiles produced by PF6.1. Automatic
        // startup is available ONLY if this independent activity role exists.
        if (json.has("activity.firstProjected")) {
            Target tracker = new Target(json.getString("activity.firstProjected"));
            if (!"run".equals(tracker.name) || !"()V".equals(tracker.descriptor))
                throw new IllegalArgumentException("Invalid first projected activity ABI");
            map.put("activity.firstProjected", tracker);
        }
        Target direct = map.get("launch.startCarActivity");
        Target aux = map.get("launch.auxiliary");
        Target factory = map.get("launch.controllerFactory");
        Target options = map.get("launch.optionsNoArgConstructor");
        String intent = "Landroid/content/Intent;";
        if (!direct.owner.equals(aux.owner) || !direct.owner.equals(factory.owner) ||
                !aux.descriptor.equals("(" + intent + ")V") ||
                !factory.descriptor.equals("()" + direct.owner) ||
                !options.name.equals("<init>") || !options.descriptor.equals("()V") ||
                !direct.descriptor.equals("(" + intent + options.owner + ")V") ||
                !map.get("projection.ready").descriptor.equals("()V") ||
                !map.get("projection.end").descriptor.equals("()V") ||
                !map.get("projection.teardown").descriptor.equals("()V") ||
                !map.get("projection.ready").owner.equals(map.get("projection.end").owner) ||
                !map.get("projection.ready").owner.equals(map.get("projection.teardown").owner) ||
                !map.get("media.selectedApp").descriptor.equals("(Landroid/content/ComponentName;)V"))
            throw new IllegalArgumentException("Cross-role ABI mismatch");
        return new MediaStartupLabProfile(expectedFingerprint, expectedBase,
                data.getString("mode"), map);
    }
}
