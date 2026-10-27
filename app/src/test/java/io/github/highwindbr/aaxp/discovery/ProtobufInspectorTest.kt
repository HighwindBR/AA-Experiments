package io.github.highwindbr.aaxp.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ProtobufInspectorTest {
    @Test fun phoneThemeSingleManufacturerIsRepeatedStringProto() {
        val inspection = requireNotNull(ProtobufInspector.inspectBase64("CgdzYW1zdW5n"))
        assertEquals(ProtobufResolutionLevel.PROTOBUF_WIRE_PARSED, inspection.level)
        assertEquals(listOf("samsung"), inspection.strings)
        assertEquals(1, inspection.fields.single().number)
    }

    @Test fun phoneThemeTwoManufacturersExposeRepeatedField() {
        val inspection = requireNotNull(ProtobufInspector.inspectBase64("CgdzYW1zdW5nCgZnb29nbGU="))
        assertEquals(listOf("samsung", "google"), inspection.strings)
        assertEquals(setOf(1), inspection.repeatedFieldNumbers)
    }

    @Test fun nestedWidgetMessageIsDecodedWithoutClaimingSchema() {
        val nested = byteArrayOf(0x0a, 0x07) + "pkg.one".toByteArray()
        val outer = byteArrayOf(0x0a, nested.size.toByte()) + nested
        val inspection = requireNotNull(ProtobufInspector.inspect(outer))
        assertEquals(ProtobufResolutionLevel.PROTOBUF_WIRE_PARSED, inspection.level)
        assertEquals(listOf("pkg.one"), inspection.strings)
    }

    @Test fun truncatedAndOversizedLengthsAreRejectedSafely() {
        val raw = requireNotNull(ProtobufInspector.inspect(byteArrayOf(0x0a, 0x7f)))
        assertEquals(ProtobufResolutionLevel.PROTOBUF_RAW, raw.level)
        assertTrue(raw.fields.isEmpty())
        assertNull(ProtobufInspector.inspectBase64("not base64 text"))
    }

    @Test fun schemaResolutionRequiresMatchingWireTypesAndCardinality() {
        val bytes = Base64.getDecoder().decode("CgdzYW1zdW5nCgZnb29nbGU=")
        val repeated = ProtobufSchema(mapOf(1 to ProtobufFieldSchema("manufacturers", setOf(ProtobufWireType.LENGTH_DELIMITED), repeated = true)))
        assertEquals(ProtobufResolutionLevel.PROTOBUF_SCHEMA_RESOLVED, ProtobufInspector.inspect(bytes, repeated)?.level)

        val singular = repeated.copy(fields = repeated.fields.mapValues { it.value.copy(repeated = false) })
        assertEquals(ProtobufResolutionLevel.PROTOBUF_WIRE_PARSED, ProtobufInspector.inspect(bytes, singular)?.level)
    }

    @Test fun booleanGettersAreNotReclassifiedAsProtobuf() {
        val identifiers = listOf(
            "CarAppLibrary__recreate_map_view_context_on_config_change_kill_switch",
            "ConnectivityLogging__disable_usb_trigger_configured_accessory_event",
            "ConnectivityLogging__disable_usb_trigger_configured_event",
            "FrameworkGalFeature__video_defer_early_ui_config_request_kill_switch",
            "GearheadCarService__ignore_density_config_change_kill_switch",
            "PhoneThemeFeature__enable_dynamic_app_icon_shape_by_manufacturer_kill_switch"
        )
        identifiers.forEach { key ->
            val getter = MethodFingerprint("classes.dex", "La;", "a", "()Z", DiscoveredType.BOOLEAN,
                emptyList(), 3, "hash", false, emptyList(), emptyList())
            val candidate = DiscoveredIdentifier(key, IdentifierNamespace.FENOTYPE_FLAG, DiscoveredType.BOOLEAN,
                "true", emptyList(), listOf(getter), ResolutionConfidence.UNIQUE_GETTER, null, true)
            val result = SemanticClassifier.normalize(candidate)
            assertEquals(key, IdentifierKind.FENOTYPE_FLAG, result.kind)
            assertEquals(key, SemanticValueType.BOOLEAN, result.semanticType)
            assertEquals(key, MappingResolution.DIRECT, result.resolution)
            assertTrue(key, result.editable)
            assertNull(key, result.metadata["protobufResolution"])
        }
    }

    @Test fun booleanFalseAndLongDefaultsNeverBecomeProtobufRegardlessOfNames() {
        for ((type, value) in listOf(DiscoveredType.BOOLEAN to "false", DiscoveredType.LONG to "300")) {
            val raw = DiscoveredIdentifier("Feature__manufacturer_config", IdentifierNamespace.FENOTYPE_FLAG,
                type, value, emptyList(), emptyList(), ResolutionConfidence.CATALOGUED, null, false)
            val decoded = ProtobufSemanticAnalyzer.enrich(raw)
            assertEquals(type, decoded.inferredType)
            assertEquals(IdentifierKind.FENOTYPE_FLAG, decoded.kind)
            assertNull(decoded.metadata["protobufResolution"])
        }
    }

    @Test fun wireParsedProtoRemainsNonEditableWithoutSchema() {
        val original = DiscoveredIdentifier(
            "PhoneThemeFeature__manufacturer_uses_dynamic_icon_shape", IdentifierNamespace.FENOTYPE_FLAG,
            DiscoveredType.STRING, "CgdzYW1zdW5nCgZnb29nbGU=", emptyList(), emptyList(),
            ResolutionConfidence.UNIQUE_GETTER, null, true
        )
        val enriched = ProtobufSemanticAnalyzer.enrich(original)
        assertEquals(IdentifierKind.PROTOBUF_CONFIG, enriched.kind)
        assertEquals(SemanticValueType.PROTO_LIST, enriched.semanticType)
        assertEquals("samsung|google", enriched.metadata["protobufStrings"])
        assertFalse(enriched.editable)
    }
}
