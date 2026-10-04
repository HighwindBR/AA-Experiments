package io.github.aaexperiments.scanner

import android.content.Context
import android.content.pm.PackageManager
import io.github.aaexperiments.core.AaInstallation
import io.github.aaexperiments.core.BuildFingerprint
import io.github.aaexperiments.discovery.DexCatalogAnalyzer
import io.github.aaexperiments.discovery.*
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

class InstalledAaScanner(private val context: Context) {
    fun scan(onProgress: (AnalysisProgress) -> Unit = {}): AaInstallation = try {
        val info = context.packageManager.getPackageInfo(AA_PACKAGE, ManifestCatalog.PACKAGE_FLAGS)
        val app = info.applicationInfo ?: return AaInstallation(false, scanError = "ApplicationInfo unavailable")
        val base = app.sourceDir
        val splits = app.splitSourceDirs?.toList().orEmpty()
        val identity = BuildFingerprint.compute(info.longVersionCode, File(base), splits.map(::File))
        val baseHash = identity.baseSha256
        val database = CatalogDatabase(context)
        var summary = database.summary(identity.fingerprintSha256)
        if (summary == null || summary.resolverSchemaVersion != SemanticClassifier.SCHEMA_VERSION) {
            // Resolver schema changes alter mapping evidence, so an old inventory
            // cannot be relabelled as current. Overrides live separately and survive.
            val previous = database.compatibilityBaselineMappings(identity.fingerprintSha256)
            val inventory = DexCatalogAnalyzer().analyze(
                File(base),
                splits.map(::File),
                previousMappings = previous?.second.orEmpty(),
                previousBaseSha256 = previous?.first?.baseSha256,
                buildIdentity = identity,
                onProgress = onProgress
            ).let { scanned ->
                scanned.copy(identifiers = (scanned.identifiers + ManifestCatalog.components(info)).distinctBy { it.key }.sortedBy { it.key })
            }
            database.put(inventory, onProgress)
            summary = checkNotNull(database.summary(identity.fingerprintSha256))
        }
        AaInstallation(
            installed = true,
            versionName = info.versionName,
            versionCode = info.longVersionCode,
            basePath = base,
            splitPaths = splits,
            baseSha256 = baseHash,
            dexSha256 = summary.dexSha256,
            identifierCount = summary.identifierCount,
            editableCount = summary.editableCount,
            discoveryErrorCount = summary.errorCount,
            runtimeTarget = true,
            buildFingerprintSha256 = identity.fingerprintSha256,
            packageArtifactSha256 = identity.artifactSha256
        )
    } catch (_: PackageManager.NameNotFoundException) {
        AaInstallation(false, scanError = "Android Auto is not installed or is hidden by package visibility")
    } catch (t: Throwable) {
        AaInstallation(false, scanError = "Scan failed: ${t.javaClass.simpleName}: ${t.message}")
    }

    private fun dexHashes(apk: File): Map<String, String> = ZipFile(apk).use { zip ->
        zip.entries().asSequence().filter { it.name.matches(Regex("classes(\\d*)\\.dex")) }.associate { entry ->
            val md = MessageDigest.getInstance("SHA-256")
            zip.getInputStream(entry).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    md.update(buffer, 0, count)
                }
            }
            entry.name to md.digest().joinToString("") { "%02x".format(it) }
        }
    }

    companion object { const val AA_PACKAGE = "com.google.android.projection.gearhead" }

}
