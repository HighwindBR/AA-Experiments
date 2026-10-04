package io.github.aaexperiments.core

import io.github.aaexperiments.discovery.DiscoveredIdentifier

/** Pure compatibility decision shared by profile publication and regression tests. */
fun suspendedOverrideReason(
    identifier: DiscoveredIdentifier?,
    value: TypedOverride?,
): SuspendedOverrideReason? = when {
    identifier == null -> SuspendedOverrideReason.SUSPENDED_MAPPING_MISSING
    value == null -> SuspendedOverrideReason.SUSPENDED_INVALID_VALUE
    value.type != identifier.inferredType -> SuspendedOverrideReason.SUSPENDED_TYPE_CHANGED
    !identifier.editable || identifier.getterCandidates.size != 1 -> SuspendedOverrideReason.SUSPENDED_MAPPING_AMBIGUOUS
    else -> null
}
