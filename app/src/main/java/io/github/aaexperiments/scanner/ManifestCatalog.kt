package io.github.aaexperiments.scanner

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import io.github.aaexperiments.discovery.*

object ManifestCatalog {
    const val PACKAGE_FLAGS = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
        PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or
        PackageManager.GET_PERMISSIONS or PackageManager.GET_CONFIGURATIONS or
        PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS

    fun components(info: PackageInfo): List<DiscoveredIdentifier> = buildList {
        info.activities?.forEach { add("activity:${it.name}") }; info.services?.forEach { add("service:${it.name}") }
        info.receivers?.forEach { add("receiver:${it.name}") }; info.providers?.forEach { add("provider:${it.name}") }
        info.requestedPermissions?.forEach { add("uses-permission:$it") }
        info.reqFeatures?.forEach { feature -> add("uses-feature:${feature.name ?: "gles:${feature.glEsVersion}"}") }
        info.configPreferences?.forEach { config -> add("uses-configuration:${config.reqTouchScreen}:${config.reqKeyboardType}:${config.reqNavigation}") }
        info.applicationInfo?.metaData?.keySet()?.forEach { add("application-meta-data:$it") }
    }.distinct().map { name -> DiscoveredIdentifier(
        name, IdentifierNamespace.MANIFEST_COMPONENT, DiscoveredType.OBJECT, null, emptyList(), emptyList(),
        ResolutionConfidence.CATALOGUED, "Manifest declaration; diagnostic only.", false
    ) }
}
