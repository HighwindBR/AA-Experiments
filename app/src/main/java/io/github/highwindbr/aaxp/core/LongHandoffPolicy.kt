package io.github.highwindbr.aaxp.core

import io.github.highwindbr.aaxp.discovery.*

/**
 * Temporary beta4 diagnostic, NOT a generalized override path for editable flags.
 * Hook ownership changes, but the user's normal LONG:3 preference is never removed.
 */
object LongHandoffPolicy {
    const val KEY = "Assistant__icon_experiment_variant"
    const val MODE = "NORMAL_LONG_VALIDATION"
    const val ORIGINAL = "LONG:3"
    const val EFFECTIVE = "3"
    const val EXPECTED_BASE =
        "cebd7c3c50885fb7dbb8fce202b5f33f0412c8f3801fac91a509da9482b9c60d"

    fun issue(identifier: DiscoveredIdentifier, baseSha256: String?): String? {
        if (!EXPECTED_BASE.equals(baseSha256, ignoreCase = true)) return "This diagnostic requires the inspected AA 18.0 base."
        if (identifier.key != KEY || identifier.kind != IdentifierKind.FENOTYPE_FLAG ||
            !identifier.editable || identifier.inferredType != DiscoveredType.LONG)
            return "Not the specifically reviewed normal LONG getter."
        SafetyPolicy.blockedReason(identifier.key)?.let { return it }
        if (identifier.resolution !in setOf(MappingResolution.DIRECT, MappingResolution.STRUCTURAL))
            return "Getter mapping is not direct/structural."
        if (identifier.compiledDefault != "0") return "Compiled default differs from reviewed value."
        val getter = identifier.getterCandidates.singleOrNull() ?: return "Getter is not unique."
        if (getter.className != "Laczh;" || getter.methodName != "l" || getter.descriptor != "()J" ||
            getter.parameterTypes.isNotEmpty() || getter.instructionCount <= 0 ||
            getter.opcodeSha256.isBlank()) return "Getter identity changed."
        return null
    }

    fun record(identifier: DiscoveredIdentifier, build: String, baseSha256: String?,
        serializedNormal: String?, name: String?, now: Long): AdvancedOverrideRecord {
        require(issue(identifier, baseSha256) == null) { issue(identifier, baseSha256) ?: "Not eligible" }
        require(serializedNormal == ORIGINAL && name == KEY) {
            "Handoff requires an intact LONG:3 normal override and its exact name."
        }
        val getter = identifier.getterCandidates.single()
        return AdvancedOverrideRecord(
            identifier.key, DiscoveredType.LONG, EFFECTIVE, build, getter.className,
            getter.methodName, getter.descriptor, getter.opcodeSha256, getter.instructionCount,
            ORIGINAL, KEY, true, now, MODE
        )
    }

    fun matches(identifier: DiscoveredIdentifier, record: AdvancedOverrideRecord,
        build: String, baseSha256: String?): Boolean {
        if (record.handoffMode != MODE || issue(identifier, baseSha256) != null ||
            record.key != KEY || record.type != DiscoveredType.LONG ||
            record.value != EFFECTIVE || record.previousOverride != ORIGINAL ||
            record.previousName != KEY || record.buildFingerprint != build) return false
        val getter = identifier.getterCandidates.single()
        return record.targetClass == getter.className &&
            record.targetMethod == getter.methodName &&
            record.descriptor == getter.descriptor &&
            record.opcodeFingerprint == getter.opcodeSha256 &&
            record.instructionCount == getter.instructionCount
    }

    fun baselineIntact(record: AdvancedOverrideRecord, normal: String?, name: String?): Boolean =
        record.handoffMode == MODE && record.key == KEY &&
            record.previousOverride == ORIGINAL && record.previousName == KEY &&
            normal == ORIGINAL && name == KEY && record.value == EFFECTIVE
}
