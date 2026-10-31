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
                "launch.controllerFactory", "media.selectedApp"
        };
        Map<String, Target> map = new LinkedHashMap<>();
        for (String key : required) map.put(key, new Target(json.getString(key)));
        // Each launch route has its own proof. Never require both: Android Auto
        // updates may retire or rename one method independently of the other.
        if (json.has("launch.startCarActivity"))
            map.put("launch.startCarActivity", new Target(json.getString("launch.startCarActivity")));
        if (json.has("launch.auxiliary"))
            map.put("launch.auxiliary", new Target(json.getString("launch.auxiliary")));
        if (map.get("launch.startCarActivity") == null && map.get("launch.auxiliary") == null)
            throw new IllegalArgumentException("No verified media-opening route");

        if (json.has("launch.optionsNoArgConstructor"))
            map.put("launch.optionsNoArgConstructor",
                    new Target(json.getString("launch.optionsNoArgConstructor")));
        if (json.has("launch.navigationObserver"))
            map.put("launch.navigationObserver",
                    new Target(json.getString("launch.navigationObserver")));
        if (json.has("activity.firstProjected")) {
            Target tracker = new Target(json.getString("activity.firstProjected"));
            if (!"run".equals(tracker.name) || !"()V".equals(tracker.descriptor))
                throw new IllegalArgumentException("Invalid first projected activity ABI");
            map.put("activity.firstProjected", tracker);
        }
        Target direct = map.get("launch.startCarActivity");
        Target auxiliary = map.get("launch.auxiliary");
        Target observer = map.get("launch.navigationObserver");
        Target factory = map.get("launch.controllerFactory");
        Target options = map.get("launch.optionsNoArgConstructor");
        String intent = "Landroid/content/Intent;";
        String controller = factory.owner;
        if (!factory.descriptor.equals("()" + controller) ||
                !map.get("projection.ready").descriptor.equals("()V") ||
                !map.get("projection.end").descriptor.equals("()V") ||
                !map.get("projection.teardown").descriptor.equals("()V") ||
                !map.get("projection.ready").owner.equals(map.get("projection.end").owner) ||
                !map.get("projection.ready").owner.equals(map.get("projection.teardown").owner) ||
                !map.get("media.selectedApp").descriptor.equals("(Landroid/content/ComponentName;)V"))
            throw new IllegalArgumentException("Cross-role ABI mismatch");
        if (auxiliary != null && (!controller.equals(auxiliary.owner) ||
                !auxiliary.descriptor.equals("(" + intent + ")V")))
            throw new IllegalArgumentException("Auxiliary route ABI mismatch");
        if (direct != null && !controller.equals(direct.owner))
            throw new IllegalArgumentException("Direct route owner mismatch");
        // Direct dispatch requires options. Auxiliary dispatch never resolves
        // unavailable direct/option members, but the independent navigation
        // observer (if proven) still needs its own valid options signature.
        Target inspectedDirect = direct != null ? direct : observer;
        if (inspectedDirect != null) {
            if (options == null || !controller.equals(inspectedDirect.owner) ||
                    !options.name.equals("<init>") || !options.descriptor.equals("()V") ||
                    !inspectedDirect.descriptor.equals("(" + intent + options.owner + ")V"))
                throw new IllegalArgumentException("Direct/observer ABI mismatch");
        }
        if (observer != null && (direct == null || !observer.reference().equals(direct.reference())) &&
                direct != null)
            throw new IllegalArgumentException("Mismatched navigation observer");
        return new MediaStartupLabProfile(expectedFingerprint, expectedBase,
                data.getString("mode"), map);
    }
}
