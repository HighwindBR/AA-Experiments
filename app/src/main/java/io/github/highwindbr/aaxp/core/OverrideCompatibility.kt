package io.github.highwindbr.aaxp.core

import io.github.highwindbr.aaxp.discovery.DiscoveredIdentifier

/**
 * Normal hooks use one zero-arg primitive/String getter. The descriptor and
 * stored value must agree, including the narrower INT return ABIs.
 * Keep this check centralized for imports, edits and profile publication.
 */
private fun hasCompatibleNormalGetter(identifier: DiscoveredIdentifier): Boolean {
    val getter = identifier.getterCandidates.singleOrNull() ?: return false
    val validDescriptors = when (identifier.inferredType) {
        io.github.highwindbr.aaxp.discovery.DiscoveredType.BOOLEAN -> setOf("()Z")
        io.github.highwindbr.aaxp.discovery.DiscoveredType.INT -> setOf("()I", "()B", "()S", "()C")
        io.github.highwindbr.aaxp.discovery.DiscoveredType.LONG -> setOf("()J")
        io.github.highwindbr.aaxp.discovery.DiscoveredType.FLOAT -> setOf("()F")
        io.github.highwindbr.aaxp.discovery.DiscoveredType.DOUBLE -> setOf("()D")
        io.github.highwindbr.aaxp.discovery.DiscoveredType.STRING -> setOf("()Ljava/lang/String;")
        else -> emptySet()
    }
    return !getter.isStaticInitializer && getter.parameterTypes.isEmpty() &&
        getter.descriptor in validDescriptors && getter.instructionCount > 0 &&
        getter.opcodeSha256.isNotBlank()
}

private fun fitsGetterRange(identifier: DiscoveredIdentifier, value: TypedOverride): Boolean {
    if (value.type != io.github.highwindbr.aaxp.discovery.DiscoveredType.INT) return true
    val number = value.encodedValue.toIntOrNull() ?: return false
    return when (identifier.getterCandidates.single().descriptor) {
        "()B" -> number in Byte.MIN_VALUE..Byte.MAX_VALUE
        "()S" -> number in Short.MIN_VALUE..Short.MAX_VALUE
        "()C" -> number in Char.MIN_VALUE.code..Char.MAX_VALUE.code
        else -> true
    }
}

/** First claimant owns a getter; additional aliases remain stored but suspended. */
fun claimNormalGetter(
    claimed: MutableMap<Triple<String, String, String>, String>,
    key: String, className: String, methodName: String, descriptor: String
): Boolean {
    val token = Triple(className, methodName, descriptor)
    if (token in claimed) return false
    claimed[token] = key
    return true
}

/** Pure compatibility decision shared by profile publication and regression tests. */
fun suspendedOverrideReason(
    identifier: DiscoveredIdentifier?,
    value: TypedOverride?,
): SuspendedOverrideReason? = when {
    identifier == null -> SuspendedOverrideReason.SUSPENDED_MAPPING_MISSING
    value == null -> SuspendedOverrideReason.SUSPENDED_INVALID_VALUE
    value.type != identifier.inferredType -> SuspendedOverrideReason.SUSPENDED_TYPE_CHANGED
    !identifier.editable || !hasCompatibleNormalGetter(identifier) -> SuspendedOverrideReason.SUSPENDED_MAPPING_AMBIGUOUS
    !fitsGetterRange(identifier, value) -> SuspendedOverrideReason.SUSPENDED_INVALID_VALUE
    else -> null
}
