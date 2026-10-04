package io.github.aaexperiments.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticClassifierTest {
    @Test fun preventsKnownNonConfigurationLiteralsFromBecomingEditable() {
        val cases = mapOf(
            "CIELO_DASHBOARD" to IdentifierKind.JAVA_ENUM_LITERAL,
            "CIELO" to IdentifierKind.ROUTE_STATE,
            "SETTINGS_SYSTEM_THEME_MODE" to IdentifierKind.TELEMETRY_EVENT,
            "SETTINGS_NAVIGATION_THEME_MODE" to IdentifierKind.TELEMETRY_EVENT,
            "PLAYBACK" to IdentifierKind.ROUTE_STATE,
            "BROWSE" to IdentifierKind.ROUTE_STATE,
            "GEMINI_ENABLED_KEY" to IdentifierKind.DERIVED_RUNTIME_STATE,
            "GEARHEAD_PROJECTION_ENABLED" to IdentifierKind.DERIVED_RUNTIME_STATE,
            "androidx.car.app.media.action.SHOW_MEDIA_PLAYBACK" to IdentifierKind.INTENT_ACTION
        )
        cases.forEach { (key, expected) ->
            val raw = DiscoveredIdentifier(key, IdentifierNamespace.STRING_LITERAL, DiscoveredType.BOOLEAN, null,
                emptyList(), listOf(method()), ResolutionConfidence.UNIQUE_GETTER, null, true)
            val normalized = SemanticClassifier.normalize(raw)
            assertEquals(key, expected, normalized.kind)
            assertFalse(key, normalized.editable)
            assertEquals(key, EditabilityReason.ENTITY_NOT_CONFIGURABLE, normalized.editabilityReason)
        }
    }

    @Test fun keepsConfigurationSourcesIndependentFromResolutionAndType() {
        val phenotype = DiscoveredIdentifier("NeoplanFeature__enabled", IdentifierNamespace.FENOTYPE_FLAG, DiscoveredType.LONG, "0",
            emptyList(), listOf(method(DiscoveredType.LONG, "()J")), ResolutionConfidence.UNIQUE_GETTER, null, true)
        val normalized = SemanticClassifier.normalize(phenotype)
        assertEquals(IdentifierKind.FENOTYPE_FLAG, normalized.kind)
        assertEquals(DiscoveredType.LONG, normalized.inferredType)
        assertEquals(MappingResolution.DIRECT, normalized.resolution)
        assertNull(normalized.editabilityReason)
    }

    @Test fun recognizesLocalPreferenceWithoutTreatingItAsPhenotype() {
        assertEquals(IdentifierKind.PREFERENCE_KEY,
            SemanticClassifier.kindOf("key_settings_magic_cue_enabled", IdentifierNamespace.PREFERENCE_KEY))
    }

    @Test fun numericModeRemainsResolvedButNotEditableUntilDomainIsKnown() {
        val mode = SemanticClassifier.normalize(DiscoveredIdentifier("AceFeature__mode", IdentifierNamespace.FENOTYPE_FLAG,
            DiscoveredType.LONG, "0", emptyList(), listOf(method(DiscoveredType.LONG, "()J")),
            ResolutionConfidence.STRUCTURALLY_VALIDATED, null, true))
        assertEquals(DiscoveredType.LONG, mode.inferredType)
        assertEquals(StorageValueType.LONG, mode.storageType)
        assertEquals(SemanticValueType.ENUM_BACKED_LONG, mode.semanticType)
        assertEquals(MappingResolution.STRUCTURAL, mode.resolution)
        assertFalse(mode.editable)
        assertEquals(EditabilityReason.ENUM_DOMAIN_UNRESOLVED, mode.editabilityReason)
    }

    @Test fun textualProtoArtifactIsCatalogOnlyAndNeverEditable() {
        val occurrence = IdentifierOccurrence(method(), 0, null, emptyList(), role = LiteralRole.UNKNOWN,
            kind = IdentifierKind.PROTOBUF_CONFIG, storageType = StorageValueType.STRING,
            semanticType = SemanticValueType.STRING, classificationConfidence = EvidenceStrength.LOW)
        val raw = DiscoveredIdentifier(" protocol=", IdentifierNamespace.PROTOBUF_CONFIG, DiscoveredType.STRING, null,
            listOf(occurrence), listOf(method(DiscoveredType.STRING, "()Ljava/lang/String;")),
            ResolutionConfidence.UNIQUE_GETTER, null, true)
        val normalized = SemanticClassifier.normalize(raw)
        assertFalse(normalized.editable)
        assertEquals(MappingResolution.CATALOG_ONLY, normalized.resolution)
        assertEquals(EditabilityReason.SUSPICIOUS_DISCOVERY, normalized.editabilityReason)
        assertTrue(normalized.metadata["suspiciousReason"].orEmpty().isNotBlank())
    }

    @Test fun protocolLibraryPropertyIsNotMisclassifiedAsProtobuf() {
        val analyzer = DexCatalogAnalyzer()
        assertEquals(IdentifierNamespace.STRING_LITERAL, analyzer.classify("http.protocol.allow-circular-redirects"))
        assertEquals(IdentifierNamespace.PROTOBUF_CONFIG, analyzer.classify("feature_proto_config"))
    }

    private fun method(type: DiscoveredType = DiscoveredType.BOOLEAN, descriptor: String = "()Z") =
        MethodFingerprint("classes.dex", "La;", "a", descriptor, type, emptyList(), 1, "hash", false, emptyList(), emptyList())
}
