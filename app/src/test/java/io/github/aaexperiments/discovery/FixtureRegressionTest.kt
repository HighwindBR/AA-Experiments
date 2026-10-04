package io.github.aaexperiments.discovery

import io.github.aaexperiments.scanner.ArchiveInputResolver
import io.github.aaexperiments.test.fixtureRootOrSkip
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FixtureRegressionTest {
    @Test fun knownApkmFixturesRemainAnalyzable() {
        val root = fixtureRootOrSkip("aa-16.9.666314.apkm", "aa-17.8.663814.apkm")
        val fixtures = File(root, "fixtures/apks")
        val oldFile = File(fixtures, "aa-16.9.666314.apkm")
        val stableFile = File(fixtures, "aa-17.8.663814.apkm")
        val temporary = File(root, "app/build/tmp/fixture-regression").apply { mkdirs() }
        val analyzer = DexCatalogAnalyzer()
        val old = analyzer.analyze(ArchiveInputResolver.baseApk(oldFile, temporary))
        val stable = analyzer.analyze(ArchiveInputResolver.baseApk(stableFile, temporary))
        val key = "CieloFeature__earth_enabled"
        val before = old.identifiers.single { it.key == key }
        val after = stable.identifiers.single { it.key == key }
        assertNotEquals(before.getterCandidates.single().className, after.getterCandidates.single().className)
        assertEquals(DiscoveredType.BOOLEAN, after.inferredType)
        val diff = CatalogDiff.compare(old, stable).single { it.key == key }
        assertEquals(ChangeKind.REMAPPED, diff.kind)
        val focusKey = "CieloFeature__cielo_focus_enabled"
        assertEquals("false", old.identifiers.single { it.key == focusKey }.compiledDefault)
        assertEquals("true", stable.identifiers.single { it.key == focusKey }.compiledDefault)

        mapOf(
            "CIELO_DASHBOARD" to IdentifierKind.JAVA_ENUM_LITERAL,
            "SETTINGS_SYSTEM_THEME_MODE" to IdentifierKind.TELEMETRY_EVENT,
            "BROWSE" to IdentifierKind.ROUTE_STATE
        ).forEach { (candidate, kind) -> stable.identifiers.single { it.key == candidate }.let { identifier ->
            assertEquals(candidate, kind, identifier.kind)
            assertFalse(candidate, identifier.editable)
        } }
        stable.identifiers.single { it.key == "NeoplanFeature__enabled" }.let { neoplan ->
            assertEquals(DiscoveredType.LONG, neoplan.inferredType)
            assertEquals(IdentifierKind.FENOTYPE_FLAG, neoplan.kind)
        }
        val byKey = stable.identifiers.associateBy { it.key }
        ResolverRegressionOracles.stableClassifications.forEach { oracle ->
            val actual = requireNotNull(byKey[oracle.key]) { "Missing oracle key ${oracle.key}" }
            assertEquals(oracle.key, oracle.kind, actual.kind)
            if (oracle.storageType != StorageValueType.UNKNOWN) assertEquals(oracle.key, oracle.storageType, actual.storageType)
            if (oracle.semanticType != SemanticValueType.UNKNOWN) assertEquals(oracle.key, oracle.semanticType, actual.semanticType)
            assertEquals(oracle.key, oracle.editable, actual.editable)
        }
        mapOf(
            "PhoneThemeFeature__manufacturer_prefers_device_font_family" to listOf("samsung"),
            "PhoneThemeFeature__manufacturer_uses_dynamic_icon_shape" to listOf("samsung", "google")
        ).forEach { (protoKey, expectedStrings) ->
            val actual = byKey.getValue(protoKey)
            assertEquals("$protoKey default=${actual.compiledDefault} occurrenceDefaults=${actual.occurrences.map { it.candidateDefault }} metadata=${actual.metadata}", IdentifierKind.PROTOBUF_CONFIG, actual.kind)
            assertEquals(protoKey, SemanticValueType.PROTO_LIST, actual.semanticType)
            assertEquals(protoKey, expectedStrings.joinToString("|"), actual.metadata["protobufStrings"])
            assertFalse(protoKey, actual.editable)
        }
        listOf(
            "CieloFeature__default_widgets_config",
            "CieloFeature__featured_widgets_config"
        ).forEach { protoKey ->
            val actual = byKey.getValue(protoKey)
            assertEquals("$protoKey default=${actual.compiledDefault} metadata=${actual.metadata}", IdentifierKind.PROTOBUF_CONFIG, actual.kind)
            assertEquals(protoKey, ProtobufResolutionLevel.PROTOBUF_WIRE_PARSED.name, actual.metadata["protobufResolution"])
            assertTrue("$protoKey should expose decoded package/provider strings: ${actual.metadata}", actual.metadata["protobufStrings"].orEmpty().contains("."))
            assertFalse(protoKey, actual.editable)
        }
        byKey.getValue("AceFeature__mode").let { ace ->
            assertEquals("RESOLVED", ace.metadata["enumMapping"])
            assertEquals("UNRESOLVED", ace.metadata["enumDomain"])
            assertEquals(EditabilityReason.ENUM_DOMAIN_UNRESOLVED, ace.editabilityReason)
            assertFalse(ace.editable)
        }
        listOf("Messaging__remove_sms_stream_item_path", "Messaging__remove_im_stream_item_path").forEach { mixedKey ->
            val actual = byKey.getValue(mixedKey)
            assertTrue("$mixedKey metadata=${actual.metadata}", actual.metadata["consumptionPaths"].orEmpty().contains(ConsumptionPath.LIVE.name))
            assertTrue("$mixedKey metadata=${actual.metadata}", actual.metadata["consumptionPaths"].orEmpty().contains(ConsumptionPath.INSTANCE_CACHE.name))
            assertTrue("$mixedKey metadata=${actual.metadata}", actual.metadata["consumptionPaths"].orEmpty().contains(ConsumptionPath.STATIC_STARTUP.name))
            assertTrue("$mixedKey metadata=${actual.metadata}", actual.metadata["consumptionPaths"].orEmpty().contains(ConsumptionPath.RUNTIME_LATCH.name))
            assertEquals(mixedKey, ConsumptionPath.MIXED.name, actual.metadata["consumptionPath"])
        }
        byKey.getValue("Media__autoplay_paused_after_buffering_retry_delay_ms").let { actual ->
            assertTrue("metadata=${actual.metadata}", actual.metadata["consumptionPaths"].orEmpty().contains(ConsumptionPath.STATIC_CACHE.name))
        }
        // The alpha16 instance-cache result was a generic Object-return collision. The stricter
        // lineage analyzer must retain the proven live consumer without inventing an unrelated cache.
        byKey.getValue("FrameRateRestrictions__thermal_headroom_throttling_enabled").let { actual ->
            assertTrue("metadata=${actual.metadata}", actual.metadata["consumptionPaths"].orEmpty().contains(ConsumptionPath.LIVE.name))
            assertFalse("metadata=${actual.metadata}", actual.metadata["consumptionPaths"].orEmpty().contains(ConsumptionPath.INSTANCE_CACHE.name))
        }
        byKey.getValue("CieloFeature__earth_status").let { actual ->
            assertEquals("8", actual.metadata["registryId"])
            assertEquals("", actual.compiledDefault)
            assertEquals(SemanticDependencyAnalyzer.ValueOrigin.FENOTYPE_REGISTRY.name, actual.metadata["valueOrigin"])
            assertEquals("NOT_PROVEN", actual.metadata["sourcePrecedence"])
            assertFalse("metadata=${actual.metadata}", actual.metadata.containsKey("semanticRelationships"))
            assertFalse("metadata=${actual.metadata}", actual.metadata["valueOrigin"].orEmpty().contains("SHARED_PREFERENCES"))
        }
        byKey.getValue("CieloFeature__earth_enabled").let { actual ->
            assertFalse("metadata=${actual.metadata}", actual.metadata.containsKey("semanticRelationships"))
            if (actual.metadata["consumerBudgetExhausted"].toBoolean()) {
                assertEquals("INCOMPLETE_CONSUMER_BUDGET", actual.metadata["analysisStatus"])
                assertFalse("metadata=${actual.metadata}", actual.metadata.containsKey("downstreamGates"))
            }
        }
        listOf("LauncherShortcuts__enabled", "LauncherShortcuts__assistant_shortcut_enabled").forEach { shortcut ->
            val actual = byKey.getValue(shortcut)
            assertFalse("$shortcut metadata=${actual.metadata}", actual.metadata.containsKey("semanticRelationships"))
            assertTrue("$shortcut metadata=${actual.metadata}", actual.metadata["relationshipStatus"].orEmpty().startsWith("DEFERRED") || actual.metadata["relationshipStatus"].orEmpty().startsWith("SUSPENDED"))
        }
        byKey.getValue("SystemUi__satellite_network_status").let { actual ->
            assertFalse("metadata=${actual.metadata}", actual.metadata.containsKey("semanticRelationships"))
            if (actual.metadata["analysisStatus"] != "INCOMPLETE_CONSUMER_BUDGET") {
                val gates = actual.metadata["downstreamGates"].orEmpty()
                assertTrue(gates.contains(SemanticDependencyAnalyzer.EligibilityGate.SDK_VERSION.name))
                assertTrue(gates.contains(SemanticDependencyAnalyzer.EligibilityGate.SYSTEM_FEATURE.name))
            }
        }
    }
}
