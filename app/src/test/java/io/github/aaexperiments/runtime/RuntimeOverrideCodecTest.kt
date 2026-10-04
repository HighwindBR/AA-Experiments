package io.github.aaexperiments.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RuntimeOverrideCodecTest {
    @Test fun decodesEverySupportedRuntimeAbiType() {
        assertEquals(true, RuntimeOverrideCodec.decode("BOOLEAN:true", Boolean::class.javaPrimitiveType))
        assertEquals(7.toByte(), RuntimeOverrideCodec.decode("INT:7", Byte::class.javaPrimitiveType))
        assertEquals(7.toShort(), RuntimeOverrideCodec.decode("INT:7", Short::class.javaPrimitiveType))
        assertEquals('A', RuntimeOverrideCodec.decode("INT:65", Char::class.javaPrimitiveType))
        assertEquals(7, RuntimeOverrideCodec.decode("INT:7", Int::class.javaPrimitiveType))
        assertEquals(7L, RuntimeOverrideCodec.decode("LONG:7", Long::class.javaPrimitiveType))
        assertEquals(1.25f, RuntimeOverrideCodec.decode("FLOAT:1.25", Float::class.javaPrimitiveType))
        assertEquals(1.25, RuntimeOverrideCodec.decode("DOUBLE:1.25", Double::class.javaPrimitiveType))
        assertEquals("value", RuntimeOverrideCodec.decode("STRING:value", String::class.java))
        assertThrows(IllegalArgumentException::class.java) { RuntimeOverrideCodec.decode("FLOAT:1.25", Double::class.javaPrimitiveType) }
    }
}
