package io.github.aaexperiments.discovery

import java.nio.ByteBuffer
import java.nio.ByteOrder

class ArscCatalogParser(private val data: ByteArray) {
    private val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
    private data class Chunk(val offset: Int, val type: Int, val headerSize: Int, val size: Int)

    fun parse(): List<DiscoveredIdentifier> {
        val root = chunk(0); require(root.type == 0x0002) { "Not an Android resource table" }
        val globalPoolChunk = children(root).firstOrNull { it.type == 0x0001 }
        val globalStrings = globalPoolChunk?.let(::stringPool).orEmpty()
        val entries = linkedMapOf<String, MutableList<ResourceValue>>()
        children(root).filter { it.type == 0x0200 }.forEach { pkg -> parsePackage(pkg, globalStrings, entries) }
        return entries.map { (key, values) ->
            val default = values.firstOrNull { it.defaultConfig } ?: values.firstOrNull()
            DiscoveredIdentifier(key, IdentifierNamespace.RESOURCE_NAME, default?.type ?: DiscoveredType.UNKNOWN, default?.value,
                emptyList(), emptyList(), ResolutionConfidence.CATALOGUED,
                "Compiled Android resource; read-only until a specific consumer method is resolved.", false,
                mapOf("resourceId" to "0x%08x".format(default?.resourceId ?: 0), "configCount" to values.size.toString(), "dataType" to (default?.dataType?.toString() ?: "unknown")))
        }
    }

    private data class ResourceValue(val resourceId: Int, val type: DiscoveredType, val value: String?, val dataType: Int, val defaultConfig: Boolean)

    private fun parsePackage(pkg: Chunk, globalStrings: List<String>, output: MutableMap<String, MutableList<ResourceValue>>) {
        val packageId = i32(pkg.offset + 8); val typeOffset = i32(pkg.offset + 268); val keyOffset = i32(pkg.offset + 276)
        if (typeOffset <= 0 || keyOffset <= 0) return
        val types = stringPool(chunk(pkg.offset + typeOffset)); val keys = stringPool(chunk(pkg.offset + keyOffset))
        children(pkg).filter { it.type == 0x0201 }.forEach { typeChunk ->
            val typeId = u8(typeChunk.offset + 8); if (typeId == 0 || typeId > types.size) return@forEach
            val flags = u8(typeChunk.offset + 9); if (flags != 0) return@forEach
            val count = i32(typeChunk.offset + 12); val entriesStart = i32(typeChunk.offset + 16)
            val defaultConfig = isDefaultConfig(typeChunk)
            repeat(count.coerceAtLeast(0)) { entryId ->
                val offsetPosition = typeChunk.offset + typeChunk.headerSize + entryId * 4
                if (offsetPosition + 4 > typeChunk.offset + typeChunk.size) return@repeat
                val entryOffset = i32(offsetPosition); if (entryOffset == -1) return@repeat
                val entry = typeChunk.offset + entriesStart + entryOffset; if (entry + 8 > data.size) return@repeat
                val entrySize = u16(entry); val entryFlags = u16(entry + 2); val keyIndex = i32(entry + 4)
                if (keyIndex !in keys.indices) return@repeat
                val name = "${types[typeId - 1]}/${keys[keyIndex]}"; val resourceId = (packageId shl 24) or (typeId shl 16) or entryId
                if (entryFlags and 1 != 0) {
                    output.getOrPut(name) { mutableListOf() } += ResourceValue(resourceId, DiscoveredType.OBJECT, null, -1, defaultConfig); return@repeat
                }
                val valueAt = entry + entrySize; if (valueAt + 8 > data.size) return@repeat
                val dataType = u8(valueAt + 3); val raw = i32(valueAt + 4)
                val (type, value) = decode(dataType, raw, globalStrings)
                output.getOrPut(name) { mutableListOf() } += ResourceValue(resourceId, type, value, dataType, defaultConfig)
            }
        }
    }

    private fun decode(type: Int, raw: Int, strings: List<String>): Pair<DiscoveredType, String?> = when (type) {
        0x03 -> DiscoveredType.STRING to strings.getOrNull(raw)
        0x04 -> DiscoveredType.FLOAT to Float.fromBits(raw).toString()
        0x10 -> DiscoveredType.INT to raw.toString()
        0x11 -> DiscoveredType.INT to "0x%08x".format(raw)
        0x12 -> DiscoveredType.BOOLEAN to (raw != 0).toString()
        0x01, 0x02 -> DiscoveredType.OBJECT to "@0x%08x".format(raw)
        in 0x1c..0x1f -> DiscoveredType.INT to "#%08x".format(raw)
        else -> DiscoveredType.UNKNOWN to "0x%08x".format(raw)
    }

    private fun isDefaultConfig(type: Chunk): Boolean {
        val configAt = type.offset + 20; if (configAt + 4 > data.size) return false
        val size = i32(configAt); if (size < 4 || configAt + size > data.size) return false
        return (configAt + 4 until configAt + size).all { data[it].toInt() == 0 }
    }

    private fun stringPool(value: Chunk): List<String> {
        if (value.type != 0x0001) return emptyList()
        val count = i32(value.offset + 8); val flags = i32(value.offset + 16); val stringsStart = i32(value.offset + 20); val utf8 = flags and 0x100 != 0
        return (0 until count).map { index ->
            var position = value.offset + stringsStart + i32(value.offset + value.headerSize + index * 4)
            if (utf8) { position = skipLength8(position); val length = readLength8(position); position = length.second; String(data, position, length.first, Charsets.UTF_8) }
            else { val length = readLength16(position); position = length.second; String(data, position, length.first * 2, Charsets.UTF_16LE) }
        }
    }
    private fun skipLength8(at: Int) = readLength8(at).second
    private fun readLength8(at: Int): Pair<Int, Int> { val first = u8(at); return if (first and 0x80 != 0) (((first and 0x7f) shl 8) or u8(at + 1)) to (at + 2) else first to (at + 1) }
    private fun readLength16(at: Int): Pair<Int, Int> { val first = u16(at); return if (first and 0x8000 != 0) (((first and 0x7fff) shl 16) or u16(at + 2)) to (at + 4) else first to (at + 2) }
    private fun children(parent: Chunk): Sequence<Chunk> = sequence { var at = parent.offset + parent.headerSize; while (at + 8 <= parent.offset + parent.size) { val child = chunk(at); if (child.size <= 0 || at + child.size > parent.offset + parent.size) break; yield(child); at += child.size } }
    private fun chunk(at: Int) = Chunk(at, u16(at), u16(at + 2), i32(at + 4))
    private fun u8(at: Int) = data[at].toInt() and 0xff
    private fun u16(at: Int) = buffer.getShort(at).toInt() and 0xffff
    private fun i32(at: Int) = buffer.getInt(at)
}
