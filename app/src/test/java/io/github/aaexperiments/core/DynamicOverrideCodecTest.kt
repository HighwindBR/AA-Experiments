package io.github.aaexperiments.core

import io.github.aaexperiments.discovery.DiscoveredType
import org.junit.Assert.*
import org.junit.Test

class DynamicOverrideCodecTest {
    @Test fun supportedTypesRoundTrip() {
        listOf(
            TypedOverride(DiscoveredType.BOOLEAN, "false"), TypedOverride(DiscoveredType.INT, "-2"),
            TypedOverride(DiscoveredType.LONG, "42"), TypedOverride(DiscoveredType.FLOAT, "1.25"),
            TypedOverride(DiscoveredType.STRING, "")
        ).forEach { assertEquals(it, DynamicOverrideCodec.deserialize(DynamicOverrideCodec.serialize(it))) }
    }
    @Test fun nullAndEmptyStringRemainDifferent() {
        assertNull(DynamicOverrideCodec.deserialize(null))
        assertEquals("", DynamicOverrideCodec.deserialize("STRING:")!!.encodedValue)
    }
    @Test fun invalidTypedValuesAreRejected() {
        assertNull(DynamicOverrideCodec.deserialize("BOOLEAN:yes"))
        assertNull(DynamicOverrideCodec.deserialize("INT:1.5"))
    }
}
