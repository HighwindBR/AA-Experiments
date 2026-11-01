package io.github.highwindbr.aaxp.core

/** Saved intent is not proof that an override belongs to the installed build. */
enum class AdvancedOverrideStatus { DEFAULT, ACTIVE, PAUSED, SUSPENDED }

fun advancedOverrideStatus(record: AdvancedOverrideRecord?, currentBuild: String?): AdvancedOverrideStatus = when {
    record == null -> AdvancedOverrideStatus.DEFAULT
    currentBuild.isNullOrBlank() || record.buildFingerprint != currentBuild -> AdvancedOverrideStatus.SUSPENDED
    record.active -> AdvancedOverrideStatus.ACTIVE
    else -> AdvancedOverrideStatus.PAUSED
}
