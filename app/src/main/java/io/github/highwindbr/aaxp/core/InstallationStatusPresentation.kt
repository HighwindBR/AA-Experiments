package io.github.highwindbr.aaxp.core

/** The initial PackageManager probe is not a failed discovery. */
object InstallationStatusPresentation {
    fun title(
        loading: Boolean,
        progressAvailable: Boolean,
        installed: Boolean,
        scanError: String?
    ): String = when {
        loading && progressAvailable -> "Analyzing Android Auto catalog…"
        loading -> "Checking for Android Auto…"
        installed -> "Installed and ready"
        else -> scanError ?: "Android Auto not found"
    }

    fun detail(
        loading: Boolean,
        progressAvailable: Boolean,
        installed: Boolean,
        versionName: String?,
        versionCode: Long?
    ): String = when {
        loading && progressAvailable -> "Reading and preparing the Android Auto catalog"
        loading -> "Finding the active installation and loading its catalog"
        installed -> "${versionName ?: "Android Auto"} (${versionCode ?: "unknown"})"
        else -> "No compatible installation was detected"
    }
}
