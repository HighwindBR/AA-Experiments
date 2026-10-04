package io.github.aaexperiments.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class B1ModelRegressionTest {
    @Test fun occurrenceRolesRemainIndependentForTheSameLiteral() {
        val method = MethodFingerprint("classes.dex", "La;", "a", "()V", DiscoveredType.VOID,
            emptyList(), 2, "hash", false, emptyList(), emptyList())
        val occurrences = listOf(
            IdentifierOccurrence(method, 0, instructionOffset = 4, role = LiteralRole.ROUTE_STATE,
                kind = IdentifierKind.ROUTE_STATE, classificationConfidence = EvidenceStrength.MEDIUM),
            IdentifierOccurrence(method, 1, instructionOffset = 19, role = LiteralRole.DIAGNOSTIC_TEXT,
                kind = IdentifierKind.STRING_LITERAL, classificationConfidence = EvidenceStrength.LOW)
        )
        assertEquals(setOf(LiteralRole.ROUTE_STATE, LiteralRole.DIAGNOSTIC_TEXT), occurrences.map { it.role }.toSet())
        assertEquals(listOf(4, 19), occurrences.map { it.instructionOffset })
    }

    @Test fun storageAndSemanticTypesAreIndependent() {
        val mode = SemanticClassifier.normalize(DiscoveredIdentifier("AceFeature__mode", IdentifierNamespace.FENOTYPE_FLAG,
            DiscoveredType.LONG, "0", emptyList(), emptyList(), ResolutionConfidence.STRUCTURALLY_VALIDATED, null, true))
        assertEquals(StorageValueType.LONG, mode.storageType)
        assertEquals(SemanticValueType.ENUM_BACKED_LONG, mode.semanticType)
        assertFalse(mode.editable)
    }

    @Test fun exactBuildReferenceCannotUpgradeResolutionOrEditability() {
        val getter = MethodFingerprint("classes.dex", "La;", "a", "()Z", DiscoveredType.BOOLEAN,
            emptyList(), 2, "hash", false, emptyList(), emptyList())
        val raw = DiscoveredIdentifier("UxPrototype__enabled", IdentifierNamespace.FENOTYPE_FLAG,
            DiscoveredType.BOOLEAN, "false", emptyList(), listOf(getter), ResolutionConfidence.AMBIGUOUS, null, false)
        val enriched = ConsumerGraphAnalyzer.enrich(listOf(raw), emptyList(), ResolverRegressionOracles.STABLE_17_8_BASE_SHA256).single()
        assertEquals(MappingResolution.AMBIGUOUS, enriched.resolution)
        assertFalse(enriched.editable)
        assertTrue(enriched.semanticsReviewed)
        assertEquals(SemanticsProvenance.MANUAL_EXACT_BUILD, enriched.semanticsProvenance)
    }
}
