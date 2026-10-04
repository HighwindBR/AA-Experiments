package io.github.aaexperiments.discovery

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
