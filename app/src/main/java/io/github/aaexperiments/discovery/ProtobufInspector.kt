package io.github.aaexperiments.discovery

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

enum class ProtobufResolutionLevel { PROTOBUF_RAW, PROTOBUF_WIRE_PARSED, PROTOBUF_SCHEMA_RESOLVED }
enum class ProtobufWireType(val id: Int) { VARINT(0), FIXED64(1), LENGTH_DELIMITED(2), START_GROUP(3), END_GROUP(4), FIXED32(5) }

data class ProtobufFieldSchema(
    val name: String,
    val wireTypes: Set<ProtobufWireType>,
    val repeated: Boolean = false
)

/** A deliberately small descriptor used only when a schema was independently recovered. */
data class ProtobufSchema(
    val fields: Map<Int, ProtobufFieldSchema>,
    val allowUnknownFields: Boolean = false
)

data class ProtobufField(
    val number: Int,
    val wireType: ProtobufWireType,
    val varint: Long? = null,
    val fixedValue: Long? = null,
    val text: String? = null,
    val nested: List<ProtobufField> = emptyList(),
    val byteLength: Int = 0
)

data class ProtobufInspection(
    val level: ProtobufResolutionLevel,
    val fields: List<ProtobufField>,
    val warnings: List<String> = emptyList()
) {
    val repeatedFieldNumbers: Set<Int> = fields.groupingBy { it.number }.eachCount().filterValues { it > 1 }.keys
    val strings: List<String> get() = fields.flatMap { field -> listOfNotNull(field.text) + ProtobufInspection(level, field.nested).strings }
    fun summary(): String = buildString {
        append("fields=").append(fields.size)
        if (repeatedFieldNumbers.isNotEmpty()) append(", repeated=").append(repeatedFieldNumbers.sorted().joinToString("|"))
        if (strings.isNotEmpty()) append(", strings=").append(strings.joinToString("|").take(512))
    }
}

/** Defensive schema-free protobuf wire decoder. It never assigns field semantics. */
object ProtobufInspector {
    private const val MAX_BYTES = 1 shl 20
    private const val MAX_FIELDS = 4096
    private const val MAX_DEPTH = 8

    fun inspectBase64(value: String): ProtobufInspection? {
        val compact = value.trim()
        if (compact.length < 4 || compact.length % 4 == 1 || !compact.matches(Regex("[A-Za-z0-9+/=_-]+"))) return null
        val bytes = runCatching { Base64.getDecoder().decode(compact) }
            .recoverCatching { Base64.getUrlDecoder().decode(compact) }.getOrNull() ?: return null
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) return null
        return inspect(bytes)
    }

    fun inspect(bytes: ByteArray): ProtobufInspection? {
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) return null
        val parsed = parse(bytes, 0) ?: return ProtobufInspection(ProtobufResolutionLevel.PROTOBUF_RAW, emptyList(), listOf("Invalid or truncated wire stream"))
        return ProtobufInspection(ProtobufResolutionLevel.PROTOBUF_WIRE_PARSED, parsed)
    }

    fun inspect(bytes: ByteArray, schema: ProtobufSchema): ProtobufInspection? {
        val wire = inspect(bytes) ?: return null
        if (wire.level != ProtobufResolutionLevel.PROTOBUF_WIRE_PARSED) return wire
        val grouped = wire.fields.groupBy { it.number }
        val valid = grouped.all { (number, values) ->
            val descriptor = schema.fields[number]
            if (descriptor == null) schema.allowUnknownFields
            else values.all { it.wireType in descriptor.wireTypes } && (descriptor.repeated || values.size == 1)
        }
        return if (valid) wire.copy(level = ProtobufResolutionLevel.PROTOBUF_SCHEMA_RESOLVED)
        else wire.copy(warnings = wire.warnings + "Wire data does not fully match the supplied schema")
    }

    private fun parse(bytes: ByteArray, depth: Int): List<ProtobufField>? {
        if (depth > MAX_DEPTH) return null
        var offset = 0
        val fields = mutableListOf<ProtobufField>()
        while (offset < bytes.size) {
            if (fields.size >= MAX_FIELDS) return null
            val tag = readVarint(bytes, offset) ?: return null
            offset = tag.second
            val number = (tag.first ushr 3).toInt()
            val type = ProtobufWireType.entries.firstOrNull { it.id == (tag.first and 7L).toInt() } ?: return null
            if (number <= 0 || type in setOf(ProtobufWireType.START_GROUP, ProtobufWireType.END_GROUP)) return null
            when (type) {
                ProtobufWireType.VARINT -> {
                    val value = readVarint(bytes, offset) ?: return null; offset = value.second
                    fields += ProtobufField(number, type, varint = value.first)
                }
                ProtobufWireType.FIXED64 -> {
                    if (offset + 8 > bytes.size) return null
                    val value = ByteBuffer.wrap(bytes, offset, 8).order(ByteOrder.LITTLE_ENDIAN).long; offset += 8
                    fields += ProtobufField(number, type, fixedValue = value)
                }
                ProtobufWireType.LENGTH_DELIMITED -> {
                    val lengthValue = readVarint(bytes, offset) ?: return null; offset = lengthValue.second
                    val length = lengthValue.first
                    if (length < 0 || length > Int.MAX_VALUE || offset + length.toInt() > bytes.size) return null
                    val payload = bytes.copyOfRange(offset, offset + length.toInt()); offset += length.toInt()
                    val text = utf8(payload)
                    val nested = if (text == null && payload.isNotEmpty()) parse(payload, depth + 1).orEmpty() else emptyList()
                    fields += ProtobufField(number, type, text = text, nested = nested, byteLength = payload.size)
                }
                ProtobufWireType.FIXED32 -> {
                    if (offset + 4 > bytes.size) return null
                    val value = ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL; offset += 4
                    fields += ProtobufField(number, type, fixedValue = value)
                }
                else -> return null
            }
        }
        return fields
    }

    private fun readVarint(bytes: ByteArray, start: Int): Pair<Long, Int>? {
        var result = 0L; var shift = 0; var offset = start
        while (offset < bytes.size && shift < 64) {
            val value = bytes[offset++].toInt() and 0xff
            result = result or ((value and 0x7f).toLong() shl shift)
            if (value and 0x80 == 0) return result to offset
            shift += 7
        }
        return null
    }

    private fun utf8(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return ""
        val value = runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull() ?: return null
        if (!value.toByteArray(Charsets.UTF_8).contentEquals(bytes)) return null
        return value.takeIf { text -> text.all { it == '\n' || it == '\r' || it == '\t' || it.code in 0x20..0x7e || it.code >= 0xa0 } }
    }
}

object ProtobufSemanticAnalyzer {
    fun enrich(identifier: DiscoveredIdentifier): DiscoveredIdentifier {
        val raw = identifier.compiledDefault ?: return identifier
        if (!looksLikeProtoSetting(identifier.key)) return identifier
        val inspection = ProtobufInspector.inspectBase64(raw) ?: return identifier
        val parsed = inspection.level == ProtobufResolutionLevel.PROTOBUF_WIRE_PARSED
        val repeatedStrings = parsed && inspection.fields.isNotEmpty() && inspection.fields.all { it.wireType == ProtobufWireType.LENGTH_DELIMITED && it.text != null }
        val metadata = identifier.metadata + buildMap {
            put("protobufResolution", inspection.level.name)
            put("protobufSummary", inspection.summary())
            if (inspection.strings.isNotEmpty()) put("protobufStrings", inspection.strings.joinToString("|"))
            if (inspection.repeatedFieldNumbers.isNotEmpty()) put("protobufRepeatedFields", inspection.repeatedFieldNumbers.sorted().joinToString("|"))
        }
        return identifier.copy(
            kind = IdentifierKind.PROTOBUF_CONFIG,
            semanticType = if (repeatedStrings) SemanticValueType.PROTO_LIST else SemanticValueType.PROTOBUF,
            editable = false,
            editabilityReason = EditabilityReason.TYPE_UNSUPPORTED,
            reason = if (parsed) "Protobuf wire format decoded; schema semantics are not proven, so structured editing is disabled."
                else "Serialized protobuf default recognized, but the wire stream could not be decoded safely.",
            metadata = metadata,
            evidenceConfidence = identifier.evidenceConfidence.copy(
                semanticType = if (parsed) EvidenceStrength.MEDIUM else EvidenceStrength.LOW,
                defaultValue = EvidenceStrength.HIGH,
                evidence = (identifier.evidenceConfidence.evidence + "protobuf ${inspection.level.name.lowercase()}").distinct()
            )
        )
    }

    private fun looksLikeProtoSetting(key: String) =
        key.contains("config", true) || key.contains("widgets", true) || key.contains("manufacturer_", true) || key.contains("protobuf", true)
}
