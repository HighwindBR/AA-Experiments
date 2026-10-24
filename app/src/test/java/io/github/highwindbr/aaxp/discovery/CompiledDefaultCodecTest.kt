package io.github.highwindbr.aaxp.discovery

import org.junit.Assert.assertEquals
import org.junit.Test

class CompiledDefaultCodecTest {
    @Test fun decodesProvenDoubleRegistrationBits() {
        assertEquals("1.67", CompiledDefaultCodec.normalize(
            "4610199830550355640", DiscoveredType.DOUBLE, "INVOKE_ARGUMENT"))
        assertEquals("0.2", CompiledDefaultCodec.normalize(
            "4596373779694328218", DiscoveredType.DOUBLE, "REGISTRY_FLOW"))
        assertEquals("0.0", CompiledDefaultCodec.normalize(
            "0", DiscoveredType.DOUBLE, "INVOKE_ARGUMENT"))
    }

    @Test fun decodesProvenFloatRegistrationBits() {
        assertEquals("0.5", CompiledDefaultCodec.normalize(
            "1056964608", DiscoveredType.FLOAT, "INVOKE_ARGUMENT"))
    }

    @Test fun preservesDirectReturnsUnknownOriginsAndOtherTypes() {
        assertEquals("1.67", CompiledDefaultCodec.normalize(
            "1.67", DiscoveredType.DOUBLE, "INVOKE_ARGUMENT"))
        assertEquals("1.67", CompiledDefaultCodec.normalize(
            "1.67", DiscoveredType.DOUBLE, "DIRECT_RETURN"))
        assertEquals("4610199830550355640", CompiledDefaultCodec.normalize(
            "4610199830550355640", DiscoveredType.DOUBLE, null))
        assertEquals("4610199830550355640", CompiledDefaultCodec.normalize(
            "4610199830550355640", DiscoveredType.LONG, "INVOKE_ARGUMENT"))
        assertEquals(null, CompiledDefaultCodec.normalize(
            null, DiscoveredType.DOUBLE, "INVOKE_ARGUMENT"))
    }

    @Test fun nonFiniteAndMalformedBitsStayUnmodified() {
        assertEquals("9221120237041090560", CompiledDefaultCodec.normalize(
            "9221120237041090560", DiscoveredType.DOUBLE, "INVOKE_ARGUMENT"))
        assertEquals("garbage", CompiledDefaultCodec.normalize(
            "garbage", DiscoveredType.DOUBLE, "INVOKE_ARGUMENT"))
    }
}
