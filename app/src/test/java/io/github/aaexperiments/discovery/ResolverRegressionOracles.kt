package io.github.aaexperiments.discovery

/**
 * Human-audited expectations from the 16.9 -> 17.8 resolver report.
 *
 * This file intentionally lives in the regression suite. The production scanner cannot import it
 * and therefore cannot turn an expected answer into a runtime mapping.
 */
object ResolverRegressionOracles {
    const val STABLE_17_8_BASE_SHA256 = "676205e71a75f38e0565b9be7d462c296b14490cef9a3feda3791098e5baae04"

    data class Classification(
        val key: String,
        val kind: IdentifierKind,
        val storageType: StorageValueType,
        val semanticType: SemanticValueType,
        val editable: Boolean
    )

    val stableClassifications = listOf(
        Classification("CIELO_DASHBOARD", IdentifierKind.JAVA_ENUM_LITERAL, StorageValueType.UNKNOWN, SemanticValueType.UNKNOWN, false),
        Classification("NeoplanFeature__enabled", IdentifierKind.FENOTYPE_FLAG, StorageValueType.LONG, SemanticValueType.LONG, true),
        Classification("AceFeature__mode", IdentifierKind.FENOTYPE_FLAG, StorageValueType.LONG, SemanticValueType.ENUM_BACKED_LONG, false),
        Classification("BROWSE", IdentifierKind.ROUTE_STATE, StorageValueType.UNKNOWN, SemanticValueType.UNKNOWN, false)
    )

    data class Remap(
        val key: String,
        val oldMethod: String,
        val expectedMethod: String,
        val knownFalsePositive: String? = null
    )

    val remaps = listOf(
        Remap("CieloFeature__earth_enabled", "Laclt;->g()Z", "Lacrt;->i()Z"),
        Remap("subgraph-wrapper-reference", "Llzl;->n", "Lmav;->n", "Llyt;->q")
    )
}
