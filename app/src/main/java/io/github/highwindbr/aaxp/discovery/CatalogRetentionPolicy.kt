package io.github.highwindbr.aaxp.discovery

/** Build provenance is independent of whether the UI currently displays an archive. */
enum class CatalogOrigin { INSTALLED, IMPORTED, LEGACY }

/**
 * Cap cache growth while keeping the installed build usable after offline imports.
 * The installed predecessor is preferred over an imported archive for AA-to-AA
 * regression comparisons. No pinned build means the historical two-build policy.
 */
object CatalogRetentionPolicy {
    data class Candidate(val fingerprint: String, val generatedAt: Long, val origin: CatalogOrigin)

    fun retained(
        candidates: List<Candidate>,
        foreground: String,
        installedPin: String?,
        maximumBuilds: Int = 3
    ): Set<String> {
        require(maximumBuilds >= 1)
        val rows = candidates.distinctBy { it.fingerprint }
        val known = rows.associateBy { it.fingerprint }
        val kept = linkedSetOf<String>()
        val pin = installedPin?.takeIf(known::containsKey)
        if (pin == null) {
            known[foreground]?.let { kept += it.fingerprint }
            rows.asSequence().sortedByDescending { it.generatedAt }
                .forEach { if (kept.size < minOf(maximumBuilds, 2)) kept += it.fingerprint }
            return kept
        }

        kept += pin
        known[foreground]?.let { kept += it.fingerprint }

        // One earlier installed/legacy catalog survives so imported archives
        // cannot contaminate the installed AA comparison baseline.
        val predecessor = rows.asSequence()
            .filter { it.fingerprint !in kept && it.origin != CatalogOrigin.IMPORTED }
            .maxByOrNull { it.generatedAt }
        if (kept.size < maximumBuilds) predecessor?.let { kept += it.fingerprint }

        // Preserve at most one offline archive for review after returning to AA.
        val archive = rows.asSequence()
            .filter { it.fingerprint !in kept && it.origin == CatalogOrigin.IMPORTED }
            .maxByOrNull { it.generatedAt }
        if (kept.size < maximumBuilds) archive?.let { kept += it.fingerprint }
        return kept
    }
}
