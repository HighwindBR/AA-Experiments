package io.github.highwindbr.aaxp.resolver

import io.github.highwindbr.aaxp.core.DynamicOverrideCodec
import io.github.highwindbr.aaxp.discovery.*
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class DynamicProfileTest {
    @Test fun emitsOnlyMappingsWithActiveOverrides() {
        val method = MethodFingerprint("classes.dex", "La;", "b", "()Z", DiscoveredType.BOOLEAN,
            emptyList(), 2, "hash", false, emptyList(), emptyList())
        fun flag(key: String) = DiscoveredIdentifier(key, IdentifierNamespace.FENOTYPE_FLAG,
            DiscoveredType.BOOLEAN, "false", emptyList(), listOf(method), ResolutionConfidence.UNIQUE_GETTER, null, true)
        val inventory = DexInventory(baseSha256 = "base", dexSha256 = emptyMap(), generatedAtEpochMs = 1,
            identifiers = listOf(flag("one"), flag("two")))

        val none = JSONObject(DynamicProfile.create(inventory, emptySet())).getJSONArray("mappings")
        val one = JSONObject(DynamicProfile.create(inventory, setOf(DynamicOverrideCodec.preferenceKey("two")))).getJSONArray("mappings")
        assertEquals(0, none.length())
        assertEquals(1, one.length())
        assertEquals("two", one.getJSONObject(0).getString("key"))
    }

    @Test fun readOnlyProbesAreSeparateFromOverrideMappings() {
        val probe = DynamicProfile.Probe("PhoneThemeFeature__enable_dynamic_app_icon_shape_by_manufacturer_kill_switch",
            "a.b", "c", "()Z")
        val root = JSONObject(DynamicProfile.create("build", "base", 42, emptyMap(), emptyMap(),
            emptyList(), listOf(probe)))
        assertEquals(3, root.getInt("schemaVersion"))
        assertEquals(0, root.getJSONArray("mappings").length())
        val saved = root.getJSONArray("probes").getJSONObject(0)
        assertEquals(probe.key, saved.getString("key"))
        assertEquals("a.b", saved.getString("className"))
        assertEquals("()Z", saved.getString("descriptor"))
        assertEquals(false, saved.has("preferenceKey"))
    }

    @Test fun emitsCompactDatabaseBackedMappings() {
        val mapping = DynamicProfile.Mapping("flag", "override.v2.hash", "a.b", "c", "()Z", "BOOLEAN")
        val root = JSONObject(DynamicProfile.create("build", "base", 42, mapOf("base" to "base"), mapOf("classes.dex" to "dex"), listOf(mapping)))
        assertEquals(3, root.getInt("schemaVersion"))
        assertEquals("build", root.getString("buildFingerprintSha256"))
        assertEquals("base", root.getString("baseSha256"))
        assertEquals("override.v2.hash", root.getJSONArray("mappings").getJSONObject(0).getString("preferenceKey"))
    }
}
