package io.github.aaexperiments.discovery

import org.junit.Assert.assertEquals
import org.junit.Test

class InventoryJsonTest {
    @Test fun normalizedEncodingRoundTripsSharedMethodsAndMetadata() {
        val method = MethodFingerprint("classes.dex", "La;", "b", "()Z", DiscoveredType.BOOLEAN,
            emptyList(), 3, "abc", false, listOf("La;->c:Z"), emptyList())
        val identifier = DiscoveredIdentifier("Feature__enabled", IdentifierNamespace.FENOTYPE_FLAG,
            DiscoveredType.BOOLEAN, "true", listOf(IdentifierOccurrence(method, 2, "true", registryId = "7", defaultSource = "INVOKE_ARGUMENT")), listOf(method),
            ResolutionConfidence.UNIQUE_GETTER, null, true, mapOf("source" to "test"))
        val source = DexInventory(baseSha256 = "base", dexSha256 = mapOf("classes.dex" to "dex"),
            generatedAtEpochMs = 123, identifiers = listOf(identifier))

        assertEquals(source, InventoryJson.decode(InventoryJson.encode(source)))
    }
}
