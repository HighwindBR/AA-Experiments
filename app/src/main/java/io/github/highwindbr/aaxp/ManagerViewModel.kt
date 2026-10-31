package io.github.highwindbr.aaxp

import android.app.Application
import android.net.Uri
import android.content.Intent
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.highwindbr.aaxp.core.*
import io.github.highwindbr.aaxp.discovery.*
import io.github.highwindbr.aaxp.resolver.DynamicProfile
import io.github.highwindbr.aaxp.scanner.ArchiveInputResolver
import io.github.highwindbr.aaxp.scanner.InstalledAaScanner
import io.github.highwindbr.aaxp.scanner.ManifestCatalog
import io.github.highwindbr.aaxp.service.ServiceBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.OutputStreamWriter
import java.io.BufferedWriter

data class ManagerUiState(
    val loading: Boolean = true,
    val aa: AaInstallation = AaInstallation(false),
    val framework: FrameworkState = FrameworkState(),
    val discovered: List<DiscoveredIdentifier> = emptyList(),
    val catalog: List<DiscoveredIdentifier> = emptyList(),
    val catalogTotal: Int = 0,
    val selectedDetails: DiscoveredIdentifier? = null,
    val detailsLoading: Boolean = false,
    val loadedEditableCount: Int = 0,
    val dynamicOverrides: Map<String, TypedOverride> = emptyMap(),
    val suspendedOverrides: List<SuspendedOverride> = emptyList(),
    val profilePublished: Boolean = false,
    val probeTargetKey: String? = null,
    val experimentalSnapshot: ExperimentalBooleanSnapshot? = null,
    val experimentalProfileReady: Boolean = false,
    val advancedRecords: List<AdvancedOverrideRecord> = emptyList(),
    val advancedCandidates: List<DiscoveredIdentifier> = emptyList(),
    val advancedPublishedCount: Int = 0,
    val changes: CatalogChangeSummary? = null,
    val catalogDeltas: List<EditableCatalogDelta> = emptyList(),
    val hiddenSuspendedKeys: Set<String> = emptySet(),
    val resolverRun: ResolverRunSummary? = null,
    val deepResolvePending: Int = 0,
    val deepResolveRunning: Boolean = false,
    val consumerTraceKey: String? = null,
    val videoTimingTraceEnabled: Boolean = false,
    val mediaStartupTraceEnabled: Boolean = false,
    val mediaStartupAutoEnabled: Boolean = false,
    val mediaSemanticAnalysisRunning: Boolean = false,
    val mediaSemanticReport: String? = null,
    val mediaLabArmed: Boolean = false,
    val mediaLabRoute: String = "A",
    val mediaLabAutoCapable: Boolean = false,
    val mediaLabAutoEnabled: Boolean = false,
    val scanProgress: AnalysisProgress? = null,
    val saveState: SaveState = SaveState.IDLE,
    val saveTargetKey: String? = null,
    val message: String? = null
)

enum class ExportScope { EDITABLE, MODIFIED, ALL_DISCOVERIES }
enum class ExportFormat { JSON, CSV }
data class CatalogExportRequest(val scope: ExportScope, val format: ExportFormat, val includeMethods: Boolean, val includeDefaults: Boolean, val includeOverrides: Boolean) {
    val fileName: String get() = "aa-catalog-${scope.name.lowercase()}.${format.name.lowercase()}"
}
private data class DeepResolveOutcome(val saved: Int, val resolved: Int, val inconclusive: Int, val cancelled: Boolean)
private data class OverrideRefreshOutcome(val active: Map<String, TypedOverride>, val suspended: List<SuspendedOverride>, val published: Boolean, val error: Throwable?, val experimental: ExperimentalBooleanSnapshot?, val experimentalReady: Boolean, val advanced: List<AdvancedOverrideRecord>, val advancedPublishedCount: Int, val videoTimingTraceEnabled: Boolean, val mediaStartupTraceEnabled: Boolean, val mediaStartupAutoEnabled: Boolean, val mediaLabArmed: Boolean, val mediaLabRoute: String,
    val mediaLabAutoCapable: Boolean, val mediaLabAutoEnabled: Boolean)

class ManagerViewModel(application: Application) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(ManagerUiState())
    private val database = CatalogDatabase(application)
    private val hiddenPreferences = application.getSharedPreferences("suspended_override_visibility", Context.MODE_PRIVATE)
    private val probePreferences = application.getSharedPreferences("read_only_getter_probe", Context.MODE_PRIVATE)
    private val mediaLabPreferences = application.getSharedPreferences("media_startup_pf6_lab", Context.MODE_PRIVATE)
    // All profile computations, writes and build-state handoffs share one coordinator.
    private val profileRefreshMutex = ProfileRefreshGate()
    private val profileMutationCoordinator = ProfileMutationCoordinator(profileRefreshMutex)
    // Only one expensive catalog/DEX operation may own the foreground service.
    private val analysisOperationGate = AnalysisOperationGate()
    private val catalogExportGate = AnalysisOperationGate()
    val state: StateFlow<ManagerUiState> = mutable
    @Volatile private var deepResolveCancelRequested = false
    // A slower query must never replace results from a newer screen request.
    private val advancedCandidatesGate = LatestRequestGate()
    private val catalogSearchGate = LatestRequestGate()
    private val detailsGate = LatestRequestGate()

    init {
        viewModelScope.launch {
            ServiceBridge.state.collect { framework ->
                mutable.value = mutable.value.copy(framework = framework)
                refreshOverridesAndProfile()
            }
        }
        refreshInstallation()
    }

    fun refreshInstallation() = viewModelScope.launch {
        if (!analysisOperationGate.tryBegin()) {
            mutable.value = mutable.value.copy(message = "Another catalog analysis is already running.")
            return@launch
        }
        try {
            keepAnalysisAlive(true)
            mutable.value = mutable.value.copy(loading = true, scanProgress = null, mediaSemanticReport = null,
                message = "Loading installed Android Auto catalog…")
            val aa = withContext(Dispatchers.IO) {
                File(getApplication<Application>().filesDir, "imports").deleteRecursively()
                InstalledAaScanner(getApplication()).scan(::reportScanProgress)
            }
            if (mutable.value.scanProgress != null) reportScanProgress(AnalysisProgress(AnalysisStage.LOADING_EXPERIMENTS, detail = "Loading editable experiments"))
            val page = withContext(Dispatchers.IO) { aa.buildFingerprintSha256?.let { database.editablePage(it, Int.MAX_VALUE, 0) }.orEmpty() }
            val previous = withContext(Dispatchers.IO) {
                aa.buildFingerprintSha256?.let { database.previousSummary(it) }
            }
            val deltas = withContext(Dispatchers.IO) {
                previous?.let { compareEditableCatalogs(database.editablePage(it.baseSha256, Int.MAX_VALUE, 0), page) }.orEmpty()
            }
            val changes = previous?.let { deltaSummary(deltas) }
            val resolverRun = withContext(Dispatchers.IO) { aa.buildFingerprintSha256?.let(database::resolverRunSummary) }
            val deepPending = withContext(Dispatchers.IO) { aa.buildFingerprintSha256?.let(database::actionableDeepResolveCount) ?: 0 }
            val hidden = hiddenKeysForBuild(aa.buildFingerprintSha256)
            profileRefreshMutex.withLock {
                mutable.value = mutable.value.copy(loading = false, scanProgress = null, aa = aa, discovered = page,
                    loadedEditableCount = page.size, changes = changes, catalogDeltas = deltas,
                    hiddenSuspendedKeys = hidden, resolverRun = resolverRun, deepResolvePending = deepPending, message = null)
            }
            refreshOverridesAndProfile()
        } catch (cancelled: CancellationException) {
            mutable.update { it.copy(loading = false, scanProgress = null) }
            throw cancelled
        } catch (failure: Exception) {
            mutable.update { it.copy(loading = false, scanProgress = null,
                message = "Installed catalog scan failed: ${failure.javaClass.simpleName}: ${failure.message}") }
        } finally {
            try { keepAnalysisAlive(false) } finally { analysisOperationGate.finish() }
        }
    }

    /**
     * Query Advanced candidates lazily, only after explicit Diagnostics opt-in.
     * The full read-only catalog is never copied into the Experiments page.
     * Reject results for builds which changed during the asynchronous query.
     */
    fun loadAdvancedCandidates() = viewModelScope.launch {
        val request = advancedCandidatesGate.begin()
        val build = mutable.value.aa.buildFingerprintSha256
        if (build == null || !mutable.value.aa.runtimeTarget) {
            mutable.value = mutable.value.copy(advancedCandidates = emptyList())
            return@launch
        }
        val candidates = withContext(Dispatchers.IO) {
            database.advancedCandidateKeys(build).mapNotNull { key ->
                database.details(build, key)?.takeIf {
                    AdvancedOverridePolicy.issue(it) == null
                }
            }
        }
        if (advancedCandidatesGate.accepts(request) && mutable.value.aa.buildFingerprintSha256 == build &&
            mutable.value.aa.runtimeTarget)
            mutable.value = mutable.value.copy(advancedCandidates = candidates)
    }

    fun clearAdvancedCandidates() {
        advancedCandidatesGate.invalidate()
        if (mutable.value.advancedCandidates.isNotEmpty())
            mutable.value = mutable.value.copy(advancedCandidates = emptyList())
    }

    fun searchCatalog(query: CatalogQuery, append: Boolean = false) = viewModelScope.launch {
        val request = catalogSearchGate.begin()
        val hash = mutable.value.aa.buildFingerprintSha256 ?: return@launch
        val result = withContext(Dispatchers.IO) { database.search(hash, query) }
        if (!catalogSearchGate.accepts(request) || mutable.value.aa.buildFingerprintSha256 != hash) return@launch
        mutable.value = mutable.value.copy(catalog = if (append) (mutable.value.catalog + result.items).distinctBy { it.key } else result.items, catalogTotal = result.total)
    }

    fun loadDetails(key: String) = viewModelScope.launch {
        val request = detailsGate.begin()
        val hash = mutable.value.aa.buildFingerprintSha256 ?: return@launch
        mutable.value = mutable.value.copy(detailsLoading = true, selectedDetails = null)
        val value = withContext(Dispatchers.IO) { database.details(hash, key) }
        if (!detailsGate.accepts(request) || mutable.value.aa.buildFingerprintSha256 != hash) return@launch
        mutable.value = mutable.value.copy(detailsLoading = false, selectedDetails = value)
    }

    fun clearDetails() {
        detailsGate.invalidate()
        mutable.value = mutable.value.copy(selectedDetails = null, detailsLoading = false)
    }

    /** Bounded, direct-to-document export: no 50k-row objects in the manager UI. */
    fun exportCatalogToUri(destination: Uri, request: CatalogExportRequest) = viewModelScope.launch {
        val build = mutable.value.aa.buildFingerprintSha256 ?: return@launch
        if (!catalogExportGate.tryBegin()) {
            mutable.update { it.copy(message = "Another catalog export is already running.") }
            return@launch
        }
        mutable.update { it.copy(message = "Exporting catalog…") }
        try {
            val exported = withContext(Dispatchers.IO) {
                val context = getApplication<Application>()
                val overrides = mutable.value.dynamicOverrides
                val scopeKeys = if (request.scope == ExportScope.MODIFIED)
                    database.existingKeys(build, overrides.keys) else null
                context.contentResolver.openOutputStream(destination, "wt").use { stream ->
                    val output = checkNotNull(stream) { "The destination cannot be opened." }
                    BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8), 32 * 1024).use { writer ->
                        val query = CatalogQuery(editableOnly = request.scope != ExportScope.ALL_DISCOVERIES,
                            includeUnknown = true)
                        CatalogExportWriter.write(writer, request.format,
                            request.includeMethods, request.includeDefaults, request.includeOverrides,
                            overrides, pages = { offset, size ->
                                check(mutable.value.aa.buildFingerprintSha256 == build) {
                                    "Installed/catalog build changed during export."
                                }
                                database.exportPage(build, query, offset, size,
                                    request.includeMethods, scopeKeys)
                            })
                    }
                }
            }
            mutable.update { it.copy(message = "Catalog exported: $exported identifiers.") }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (ex: Exception) {
            runCatching { getApplication<Application>().contentResolver.delete(destination, null, null) }
            mutable.update { it.copy(message = "Export failed: ${ex.message}; any partial file should be discarded.") }
        } finally {
            catalogExportGate.finish()
        }
    }

    fun importArchive(uri: Uri) = viewModelScope.launch {
        if (!analysisOperationGate.tryBegin()) {
            mutable.value = mutable.value.copy(message = "Finish the current catalog analysis before importing an archive.")
            return@launch
        }
        try {
            keepAnalysisAlive(true)
            mutable.value = mutable.value.copy(loading = true, scanProgress = AnalysisProgress(AnalysisStage.PREPARING_ARCHIVE, detail = "Copying and opening archive"), message = "Importing and analyzing archive…")
            val result = withContext(Dispatchers.IO) { runCatching {
            val context = getApplication<Application>()
            val imports = File(context.filesDir, "imports").apply { mkdirs() }
            val directory = File(imports, "session-${System.currentTimeMillis()}").apply { mkdirs() }
            val archive = File(directory, "import-${System.currentTimeMillis()}.apkm")
            try {
                context.contentResolver.openInputStream(uri).use { input ->
                    ArchiveInputResolver.copyInputBounded(requireNotNull(input), archive)
                }
                val resolved = ArchiveInputResolver.resolve(archive, directory); val base = resolved.base
                val info = context.packageManager.getPackageArchiveInfo(base.absolutePath, ManifestCatalog.PACKAGE_FLAGS)
                val versionCode = info?.let { PackageInfoCompat.getLongVersionCode(it) }
                val identity = BuildFingerprint.compute(versionCode ?: 0L, base, resolved.splits)
                val previous = database.latestEditableMappings()
                val inventory = DexCatalogAnalyzer().analyze(
                    base,
                    resolved.splits,
                    previousMappings = previous?.second.orEmpty(),
                    previousBaseSha256 = previous?.first?.baseSha256,
                    buildIdentity = identity,
                    onProgress = ::reportScanProgress
                ).let { scanned ->
                    if (info == null) scanned else scanned.copy(identifiers = (scanned.identifiers + ManifestCatalog.components(info)).distinctBy { it.key }.sortedBy { it.key })
                }
                database.put(inventory, ::reportScanProgress, CatalogOrigin.IMPORTED)
                AaInstallation(installed=true, versionName=info?.versionName, versionCode=versionCode, baseSha256=inventory.baseSha256,
                    dexSha256=inventory.dexSha256, identifierCount=inventory.identifiers.size, editableCount=inventory.identifiers.count { it.editable },
                    discoveryErrorCount=inventory.errors.size, runtimeTarget=false, buildFingerprintSha256=inventory.buildFingerprintSha256,
                    packageArtifactSha256=inventory.packageArtifactSha256)
            } finally {
                directory.deleteRecursively()
                if (imports.listFiles().isNullOrEmpty()) imports.delete()
            }
        } }
            result.onSuccess { aa ->
            reportScanProgress(AnalysisProgress(AnalysisStage.LOADING_EXPERIMENTS, detail = "Loading editable experiments"))
            val page = withContext(Dispatchers.IO) { database.editablePage(checkNotNull(aa.buildFingerprintSha256), Int.MAX_VALUE, 0) }
            val previous = withContext(Dispatchers.IO) { database.previousSummary(checkNotNull(aa.buildFingerprintSha256)) }
            val deltas = withContext(Dispatchers.IO) {
                previous?.let { compareEditableCatalogs(database.editablePage(it.baseSha256, Int.MAX_VALUE, 0), page) }.orEmpty()
            }
            val resolverRun = withContext(Dispatchers.IO) { database.resolverRunSummary(checkNotNull(aa.buildFingerprintSha256)) }
            profileRefreshMutex.withLock {
                mutable.value = mutable.value.copy(loading = false, scanProgress = null, aa = aa, discovered = page, loadedEditableCount = page.size,
                    dynamicOverrides = emptyMap(), suspendedOverrides = emptyList(), hiddenSuspendedKeys = emptySet(),
                    profilePublished = false, changes = previous?.let { deltaSummary(deltas) }, catalogDeltas = deltas, resolverRun = resolverRun,
                    message = "Imported ${aa.identifierCount} identifiers for offline review; the installed-AA runtime profile was not replaced.")
            }
            }.onFailure { error -> mutable.value = mutable.value.copy(loading = false, scanProgress = null, message = "Import failed: ${error.message}") }
        } catch (cancelled: CancellationException) {
            mutable.update { it.copy(loading = false, scanProgress = null) }
            throw cancelled
        } catch (failure: Exception) {
            mutable.update { it.copy(loading = false, scanProgress = null,
                message = "Archive analysis failed: ${failure.javaClass.simpleName}: ${failure.message}") }
        } finally {
            try { keepAnalysisAlive(false) } finally { analysisOperationGate.finish() }
        }
    }

    private fun reportScanProgress(progress: AnalysisProgress) {
        // This callback runs on the DEX/IO worker thread; avoid overwriting
        // simultaneous framework state changes with a non-atomic read-copy-write.
        mutable.update { it.copy(scanProgress = progress) }
    }

    /**
     * One-key, explicit and read-only reverse call graph for Advanced getters.
     * Does not modify getter identity, editor permissions, baseline or profiles.
     */
    fun traceAdvancedConsumers(key: String) = viewModelScope.launch {
        if (mutable.value.deepResolveRunning || mutable.value.loading) {
            mutable.value = mutable.value.copy(message = "Wait for the current catalog analysis to finish.")
            return@launch
        }
        val aa = mutable.value.aa
        val hash = aa.buildFingerprintSha256
        val basePath = aa.basePath
        if (!aa.runtimeTarget || hash == null || basePath == null) {
            mutable.value = mutable.value.copy(message = "Consumer tracing requires the installed Android Auto build.")
            return@launch
        }
        val result = withContext(Dispatchers.IO) { runCatching {
            val id = checkNotNull(database.details(hash, key)) { "Identifier not in this build." }
            require(!id.editable && AdvancedOverridePolicy.issue(id) == null) {
                "Only structurally eligible, non-editable Advanced getters may be traced."
            }
        } }
        if (result.isFailure) {
            mutable.value = mutable.value.copy(message = "Trace unavailable: ${result.exceptionOrNull()?.message}")
            return@launch
        }
        if (!analysisOperationGate.tryBegin()) {
            mutable.value = mutable.value.copy(message = "Another catalog analysis is already running.")
            return@launch
        }
        deepResolveCancelRequested = false
        try {
            keepAnalysisAlive(true)
            mutable.value = mutable.value.copy(loading = true, deepResolveRunning = true,
                consumerTraceKey = key, selectedDetails = null,
                scanProgress = AnalysisProgress(AnalysisStage.DEEP_RESOLVING, 0, 1, "Tracing one Advanced getter"))
            val tracing = withContext(Dispatchers.IO) { runCatching {
                val selected = setOf(key)
                val baseline = database.compatibilityMappingsByKeys(hash, selected)
                var found: DiscoveredIdentifier? = null
                DexCatalogAnalyzer().analyze(
                    File(basePath), aa.splitPaths.map(::File),
                    previousMappings = baseline, previousBaseSha256 = hash,
                    buildIdentity = BuildFingerprint.compute(aa.versionCode ?: 0,
                        File(basePath), aa.splitPaths.map(::File)),
                    deepResolveKeys = selected, streamDeepResolveResults = true,
                    shouldContinueDeepResolve = { !deepResolveCancelRequested },
                    onDeepResolveResult = { enriched, _, _ ->
                        if (enriched.key == key) {
                            database.updateDeepResolveResults(hash, listOf(enriched))
                            found = enriched
                        }
                    },
                    onProgress = ::reportScanProgress
                )
                checkNotNull(found) { "The getter was not located in the inspected DEX." }
                val updated = checkNotNull(database.details(hash, key)) { "Saved evidence missing." }
                updated
            } }
            tracing.onSuccess { updated ->
                mutable.value = mutable.value.copy(
                    selectedDetails = updated, message = "Consumer trace completed for $key: " +
                        "${updated.consumers.size} static path(s); does not prove runtime invocation."
                )
            }.onFailure { error ->
                mutable.value = mutable.value.copy(
                    message = "Consumer trace failed: ${error.javaClass.simpleName}: ${error.message}"
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            mutable.update { it.copy(message =
                "Consumer tracing stopped: ${failure.javaClass.simpleName}: ${failure.message}") }
        } finally {
            mutable.update { it.copy(loading = false, deepResolveRunning = false,
                consumerTraceKey = null, scanProgress = null) }
            try { keepAnalysisAlive(false) } finally { analysisOperationGate.finish() }
        }
    }

    fun setVideoTimingProtocolTrace(enabled: Boolean) = viewModelScope.launch {
        val aa = mutable.value.aa
        if (!aa.runtimeTarget || !io.github.highwindbr.aaxp.runtime.VideoTimingProtocolTrace.isSupported(aa.baseSha256)) {
            mutable.value = mutable.value.copy(message = "Read-only VTC protocol tracing is pinned to the inspected Android Auto 18.0 base APK.")
            return@launch
        }
        val result = writePreferencesAndPublish {
            withContext(Dispatchers.IO) {
                check(ServiceBridge.dynamicPreferencesOrNull()?.edit()
                    ?.putString(io.github.highwindbr.aaxp.runtime.VideoTimingProtocolTrace.KEY,
                        enabled.toString())?.commit() == true) {
                    "LSPosed remote preferences unavailable."
                }
            }
        }
        mutable.value = mutable.value.copy(message = when {
            result.isFailure -> "Cannot update VTC trace: ${result.exceptionOrNull()?.message}"
            result.getOrThrow() != ProfileMutationCoordinator.Outcome.PUBLISHED ->
                "VTC trace preference saved, but runtime profile was NOT published. Check LSPosed."
            enabled -> "VTC read-only trace enabled and published. Restart Android Auto, then inspect LSPosed module logs."
            else -> "VTC trace disabled and profile published. Restart AA to unload existing hooks."
        })
    }

    /** Media Startup diagnostic opt-in; profile scope and pinned SHA stay unchanged. */
    fun setMediaStartupTrace(enabled: Boolean) = viewModelScope.launch {
        val aa = mutable.value.aa
        if (!aa.runtimeTarget ||
            !io.github.highwindbr.aaxp.runtime.MediaStartupTrace.isSupported(aa.baseSha256)) {
            mutable.value = mutable.value.copy(message =
                "Media Startup observation requires the exact inspected Android Auto 18.1 base APK.")
            return@launch
        }
        val result = writePreferencesAndPublish {
            withContext(Dispatchers.IO) {
                check(ServiceBridge.dynamicPreferencesOrNull()?.edit()
                    ?.putString(io.github.highwindbr.aaxp.runtime.MediaStartupTrace.KEY,
                        enabled.toString())
                    ?.apply {
                        if (!enabled) putString(
                            io.github.highwindbr.aaxp.runtime.MediaStartupAutoGate.KEY, "false")
                    }?.commit() == true) {
                    "LSPosed remote preferences unavailable."
                }
            }
        }
        mutable.value = mutable.value.copy(message = when {
            result.isFailure -> "Cannot update Media Startup trace: ${result.exceptionOrNull()?.message}"
            result.getOrThrow() != ProfileMutationCoordinator.Outcome.PUBLISHED ->
                "Media Startup trace preference saved, but runtime profile was NOT published. Check LSPosed."
            enabled -> "Media Startup trace enabled and published. Restart Android Auto and inspect LSPosed logs."
            else -> "Media Startup trace disabled and profile published. Restart Android Auto to unload its hooks."
        })
    }

    /**
     * PF5 discovery does NOT publish runtime hooks for an unknown build.
     * Explicitly invoked, bounded scan on the installed AA base APK.
     */
    fun inspectMediaStartupSemantics() = viewModelScope.launch {
        val aa = mutable.value.aa
        val base = aa.basePath
        if (!aa.runtimeTarget || base == null) {
            mutable.value = mutable.value.copy(message =
                "Compatibility checks require the installed Android Auto APK, not an imported archive.")
            return@launch
        }
        if (!analysisOperationGate.tryBegin()) {
            mutable.value = mutable.value.copy(message = "Another APK analysis is already running.")
            return@launch
        }
        mutable.value = mutable.value.copy(mediaSemanticAnalysisRunning = true,
            mediaSemanticReport = null)
        try {
            val outcome = withContext(Dispatchers.IO) {
                io.github.highwindbr.aaxp.resolver.MediaStartupSemanticResolver.inspect(File(base))
            }
            if (mutable.value.aa.basePath == base &&
                mutable.value.aa.buildFingerprintSha256 == aa.buildFingerprintSha256) {
                mutable.value = mutable.value.copy(
                    mediaSemanticReport = outcome.summary(),
                    message = "Media Startup compatibility: ${outcome.status}. Opening settings were not changed.")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            mutable.value = mutable.value.copy(message =
                "Compatibility scan could not finish: ${failure.javaClass.simpleName}")
        } finally {
            mutable.value = mutable.value.copy(mediaSemanticAnalysisRunning = false)
            analysisOperationGate.finish()
        }
    }

    /**
     * PF6.2: arm build-bound manual A/B routes and optionally the
     * ActivityTracker observer needed for a separate automatic opt-in.
     * Every new proof starts with PF6 auto OFF, independently of PF4.
     */
    fun armMediaStartupLab() = viewModelScope.launch {
        val aa = mutable.value.aa
        val base = aa.basePath
        val fingerprint = aa.buildFingerprintSha256
        val hash = aa.baseSha256
        if (!aa.runtimeTarget || base == null || fingerprint == null || hash == null ||
            !mutable.value.framework.connected) {
            mutable.value = mutable.value.copy(message = "Install Android Auto and connect LSPosed first.")
            return@launch
        }
        if (!analysisOperationGate.tryBegin()) {
            mutable.value = mutable.value.copy(message = "Another APK analysis is already running.")
            return@launch
        }
        mutable.value = mutable.value.copy(mediaSemanticAnalysisRunning = true)
        try {
            val evidence = withContext(Dispatchers.IO) {
                io.github.highwindbr.aaxp.resolver.MediaStartupSemanticResolver.inspect(File(base))
            }
            val required = setOf("projection.ready", "projection.end",
                "projection.teardown", "launch.controllerFactory", "launch.startCarActivity",
                "launch.optionsNoArgConstructor", "launch.auxiliary", "media.selectedApp")
            if (evidence.status !in setOf(
                    io.github.highwindbr.aaxp.resolver.MediaStartupSemanticResolver.Status.KNOWN_BUILD_VALIDATED,
                    io.github.highwindbr.aaxp.resolver.MediaStartupSemanticResolver.Status.REVIEW_ONLY) ||
                evidence.baseSha256 != hash || !evidence.roles.keys.containsAll(required)) {
                mutable.value = mutable.value.copy(mediaSemanticReport = evidence.summary(),
                    message = "Dynamic routes not armed: incomplete, ambiguous or incompatible DEX proof.")
                return@launch
            }
            if (mutable.value.aa.buildFingerprintSha256 != fingerprint ||
                mutable.value.aa.baseSha256 != hash) {
                mutable.value = mutable.value.copy(message = "Android Auto changed during analysis; restart the scan.")
                return@launch
            }
            val lab = JSONObject().apply {
                put("mode", io.github.highwindbr.aaxp.runtime.MediaStartupLabProfile.MODE)
                put("baseSha256", hash)
                put("buildFingerprintSha256", fingerprint)
                put("roles", JSONObject(evidence.roles))
            }
            // A single explicit click requests setup + auto-opening, but never
            // arms the automatic gate before the new profile was VERIFIED.
            // A future build has a different fingerprint and requires a new click.
            val requestAutomatic = MediaStartupConsentPolicy.allowAutoAfterExplicitResolution(
                evidence.status, evidence.roles)
            val (stored, published, automaticEnabled) = profileRefreshMutex.withLock {
                if (mutable.value.aa.buildFingerprintSha256 != fingerprint ||
                    mutable.value.aa.baseSha256 != hash || !mutable.value.aa.runtimeTarget) {
                    Triple(false, false, false)
                } else {
                    val disabledAuto = withContext(Dispatchers.IO) {
                        ServiceBridge.dynamicPreferencesOrNull()?.edit()
                            ?.putString(io.github.highwindbr.aaxp.runtime.MediaStartupAutoGate.KEY, "false")
                            ?.putString(io.github.highwindbr.aaxp.runtime.MediaStartupDynamicAuto.KEY, "false")
                            ?.putString(io.github.highwindbr.aaxp.runtime.MediaStartupDynamicAuto.LEGACY_PF6_KEY, "false")
                            ?.commit() == true
                    }
                    // Persist a disabled baseline first: failed publication cannot
                    // leave remote automatic opening active, even on existing hooks.
                    val saved = disabledAuto && mediaLabPreferences.edit()
                        .putString("auto-selected:$fingerprint", "false")
                        .putString(fingerprint, lab.toString()).commit()
                    if (!saved) Triple(false, false, false)
                    else {
                        mutable.value = mutable.value.copy(mediaSemanticReport = evidence.summary())
                        val publishedNow = refreshOverridesAndProfileLocked()
                        var enabledNow = false
                        if (publishedNow && requestAutomatic &&
                            mutable.value.aa.buildFingerprintSha256 == fingerprint) {
                            // Consent is build-scoped and is only enabled AFTER
                            // successful profile readback + slot activation.
                            val localSaved = mediaLabPreferences.edit()
                                .putString("auto-selected:$fingerprint", "true").commit()
                            if (localSaved) {
                                enabledNow = withContext(Dispatchers.IO) {
                                    ServiceBridge.dynamicPreferencesOrNull()?.edit()
                                        ?.putString(io.github.highwindbr.aaxp.runtime.MediaStartupDynamicAuto.KEY, "true")
                                        ?.commit() == true
                                }
                                if (!enabledNow) {
                                    mediaLabPreferences.edit()
                                        .putString("auto-selected:$fingerprint", "false").commit()
                                    withContext(Dispatchers.IO) {
                                        ServiceBridge.dynamicPreferencesOrNull()?.edit()
                                            ?.putString(io.github.highwindbr.aaxp.runtime.MediaStartupDynamicAuto.KEY, "false")
                                            ?.commit()
                                    }
                                }
                            }
                        }
                        mutable.value = mutable.value.copy(mediaLabAutoEnabled = enabledNow)
                        Triple(true, publishedNow, enabledNow)
                    }
                }
            }
            mutable.value = mutable.value.copy(message = when {
                !stored -> "Could not save the Media Startup compatibility proof. Automatic opening is unchanged."
                !published -> "Compatibility proof saved, but its profile was NOT published. Automatic opening remains off. Check LSPosed."
                automaticEnabled -> "Compatibility verified. Automatic media opening is ON for this build. Restart Android Auto, then reconnect while parked."
                requestAutomatic -> "Routes are ready, but automatic opening could not be enabled. Check LSPosed or use advanced options."
                else -> "Opening routes verified, but no unique automatic trigger was found. Manual testing is available."
            })
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (e: Exception) {
            mutable.value = mutable.value.copy(message =
                "Media Startup scan failed safely: ${e.javaClass.simpleName}")
        } finally {
            mutable.value = mutable.value.copy(mediaSemanticAnalysisRunning = false)
            analysisOperationGate.finish()
        }
    }

    fun disarmMediaStartupLab() = viewModelScope.launch {
        val fingerprint = mutable.value.aa.buildFingerprintSha256 ?: return@launch
        val (stored, published) = profileRefreshMutex.withLock {
            if (mutable.value.aa.buildFingerprintSha256 != fingerprint) {
                false to false
            } else {
                val remoteDisabled = withContext(Dispatchers.IO) {
                    ServiceBridge.dynamicPreferencesOrNull()?.edit()
                        ?.putString(io.github.highwindbr.aaxp.runtime.MediaStartupDynamicAuto.KEY, "false")
                        ?.putString(io.github.highwindbr.aaxp.runtime.MediaStartupDynamicAuto.LEGACY_PF6_KEY, "false")
                        ?.commit() == true
                }
                val saved = remoteDisabled && mediaLabPreferences.edit().remove(fingerprint)
                    .putString("auto-selected:$fingerprint", "false").commit()
                if (saved) true to refreshOverridesAndProfileLocked() else false to false
            }
        }
        mutable.value = mutable.value.copy(message = when {
            !stored -> "Dynamic route disable could not be saved. Do not assume the current hook has stopped."
            published -> "Dynamic routes disarmed and profile published; automatic attempts disabled. Restart Android Auto to unload the hooks."
            else -> "Dynamic routes disarmed in preferences, but runtime profile was NOT published. Check LSPosed; restart AA after resolving the failure."
        })
    }

    fun chooseMediaLabRoute(route: String) = viewModelScope.launch {
        if (route != "A" && route != "B") return@launch
        val fingerprint = mutable.value.aa.buildFingerprintSha256 ?: return@launch
        val persisted = profileRefreshMutex.withLock {
            if (mutable.value.aa.buildFingerprintSha256 != fingerprint) false
            else {
                val saved = withContext(Dispatchers.IO) {
                    ServiceBridge.dynamicPreferencesOrNull()?.edit()
                        ?.putString(io.github.highwindbr.aaxp.runtime.MediaStartupLabGate.ROUTE_KEY,
                            route)?.commit() == true
                }
                saved && mediaLabPreferences.edit().putString(
                    "route:$fingerprint", route).commit()
            }
        }
        mutable.value = mutable.value.copy(
            mediaLabRoute = if (persisted) route else mutable.value.mediaLabRoute,
            message = if (persisted) "Preferred Media Startup route: ${route}. Applies to the next automatic attempt."
                else "Could not persist Media Startup route.")
    }

    /** PF6.2: experimental automatic open is a separate per-build user opt-in. */
    fun setMediaLabAuto(enabled: Boolean) = viewModelScope.launch {
        val state = mutable.value
        val fingerprint = state.aa.buildFingerprintSha256 ?: return@launch
        if (!state.framework.connected ||
            (enabled && (!state.mediaLabArmed || !state.mediaLabAutoCapable ||
                !state.profilePublished || state.mediaStartupAutoEnabled))) {
            mutable.value = state.copy(message =
                "Automatic opening requires a verified Media Startup profile with a unique ActivityTracker and legacy PF4 OFF.")
            return@launch
        }
        val key = io.github.highwindbr.aaxp.runtime.MediaStartupDynamicAuto.KEY
        val status = profileRefreshMutex.withLock {
            val current = mutable.value
            if (current.aa.buildFingerprintSha256 != fingerprint ||
                !current.framework.connected ||
                (enabled && (!current.mediaLabArmed || !current.mediaLabAutoCapable ||
                    !current.profilePublished || current.mediaStartupAutoEnabled))) {
                "STALE"
            } else {
                val wrote = withContext(Dispatchers.IO) {
                    ServiceBridge.dynamicPreferencesOrNull()?.edit()
                        ?.putString(key, enabled.toString())
                        ?.putString(io.github.highwindbr.aaxp.runtime.MediaStartupDynamicAuto.LEGACY_PF6_KEY,
                            "false")?.commit() == true
                }
                if (!wrote) "REMOTE_FAILED"
                else if (mediaLabPreferences.edit()
                        .putString("auto-selected:$fingerprint", enabled.toString()).commit()) "SAVED"
                else {
                    // Fail closed if the local per-build consent cannot be stored.
                    val rolledBack = withContext(Dispatchers.IO) {
                        ServiceBridge.dynamicPreferencesOrNull()?.edit()
                            ?.putString(key, "false")?.commit() == true
                    }
                    if (rolledBack) "LOCAL_FAILED" else "ROLLBACK_FAILED"
                }
            }
        }
        mutable.value = mutable.value.copy(
            mediaLabAutoEnabled = when (status) {
                "SAVED" -> enabled
                "LOCAL_FAILED" -> false // Verified remote rollback: reflect fail-closed state.
                else -> mutable.value.mediaLabAutoEnabled
            },
            message = when (status) {
                "SAVED" -> if (enabled)
                    "Automatic media opening enabled for this Android Auto build's NEXT connection. Select your media player in Android Auto first."
                    else "Automatic media opening disabled. Any queued attempt will be skipped."
                "STALE" -> "Media Startup state changed; reopen the current build and try again."
                "REMOTE_FAILED" -> "Could not update automatic Media Startup preference in LSPosed."
                "LOCAL_FAILED" -> "Per-build media consent could not be saved; remote automatic opening was disabled for safety."
                else -> "Per-build consent write and remote rollback failed. Disable automatic opening in LSPosed before using AA."
            })
    }

    fun requestMediaLabManualTest() = viewModelScope.launch {
        val ui = mutable.value
        if (!ui.framework.connected || !ui.profilePublished || !ui.mediaLabArmed) {
            mutable.value = ui.copy(message =
                "Media Startup is not armed. Resolve, publish profile and restart Android Auto.")
            return@launch
        }
        val request = "v1|" + android.os.SystemClock.elapsedRealtime() + "|" +
            java.util.UUID.randomUUID().toString() + "|" + ui.mediaLabRoute
        val saved = withContext(Dispatchers.IO) {
            ServiceBridge.dynamicPreferencesOrNull()?.edit()
                ?.putString(io.github.highwindbr.aaxp.runtime.MediaStartupLabGate.REQUEST_KEY,
                    request)?.commit() == true
        }
        mutable.value = mutable.value.copy(message = if (saved)
            "Media Startup route ${ui.mediaLabRoute} manual test requested. Verify LSPosed MEDIA_PF6_* logs and the head unit; one attempt per connection."
        else "Media Startup LSPosed request failed.")
    }

    /** PF4 opt-in: default off, and never allowed unless PF2/PF3 trace is armed. */
    fun setMediaStartupAuto(enabled: Boolean) = viewModelScope.launch {
        val current = mutable.value
        // Turning OFF must remain possible even after AA updates to an
        // uninspected build; only enabling requires a verified runtime.
        if (!current.framework.connected ||
            (enabled && (current.mediaLabArmed || !current.aa.runtimeTarget ||
                !io.github.highwindbr.aaxp.runtime.MediaStartupTrace.isSupported(current.aa.baseSha256) ||
                !current.mediaStartupTraceEnabled || !current.profilePublished))) {
            mutable.value = mutable.value.copy(message =
                "PF4 requires the exact inspected AA 18.1 and a published Media Startup trace.")
            return@launch
        }
        val ok = profileRefreshMutex.withLock {
            val live = mutable.value
            val eligible = !enabled || (!live.mediaLabArmed && live.aa.runtimeTarget &&
                io.github.highwindbr.aaxp.runtime.MediaStartupTrace.isSupported(live.aa.baseSha256) &&
                live.mediaStartupTraceEnabled && live.profilePublished)
            if (!live.framework.connected || !eligible ||
                live.aa.buildFingerprintSha256 != current.aa.buildFingerprintSha256) false
            else withContext(Dispatchers.IO) {
                ServiceBridge.dynamicPreferencesOrNull()?.edit()
                    ?.putString(io.github.highwindbr.aaxp.runtime.MediaStartupAutoGate.KEY,
                        enabled.toString())?.commit() == true
            }
        }
        if (!ok) {
            mutable.value = mutable.value.copy(message =
                "Cannot update PF4 auto-start: LSPosed preferences unavailable.")
            return@launch
        }
        // This is deliberately a remote preference, not a change to the
        // normal flag catalog or profile schema. Armed at NEXT projection ready.
        mutable.value = mutable.value.copy(mediaStartupAutoEnabled = enabled,
            message = if (enabled)
                "PF4 enabled for the NEXT projection session. Disconnect/reconnect AA. Spotify must already be the active media app."
            else "PF4 auto-start disabled. Any pending automatic attempt will be skipped.")
    }

    /** Experimental PF3: one manually confirmed, short-lived Spotify request. */
    fun requestSpotifyManualLaunch() = viewModelScope.launch {
        val value = mutable.value
        if (!value.framework.connected || !value.profilePublished ||
                !value.mediaStartupTraceEnabled || !value.aa.runtimeTarget ||
                !io.github.highwindbr.aaxp.runtime.MediaStartupTrace.isSupported(
                    value.aa.baseSha256)) {
            mutable.value = mutable.value.copy(message =
                "PF3 unavailable. Arm Media Startup, publish the profile and restart Android Auto.")
            return@launch
        }
        val request = io.github.highwindbr.aaxp.runtime.MediaStartupLaunchGate.makeRequest(
            android.os.SystemClock.elapsedRealtime(), java.util.UUID.randomUUID().toString())
        val committed = withContext(Dispatchers.IO) {
            ServiceBridge.dynamicPreferencesOrNull()?.edit()
                ?.putString(io.github.highwindbr.aaxp.runtime.MediaStartupLaunchGate.REQUEST_KEY,
                    request)?.commit() == true
        }
        mutable.value = mutable.value.copy(message = if (committed)
            "PF3 request saved. This does not prove launch: check LSPosed MEDIA_PF3_* events. Once per active projection session."
        else "PF3 request failed: LSPosed remote preferences unavailable.")
    }

    fun deepResolveInconclusive() = viewModelScope.launch {
        if (mutable.value.deepResolveRunning) {
            deepResolveCancelRequested = true
            mutable.value = mutable.value.copy(message = "Deep resolve will stop after the current mapping.")
            return@launch
        }
        val aa = mutable.value.aa
        val hash = aa.buildFingerprintSha256
        val basePath = aa.basePath
        if (!aa.runtimeTarget || hash == null || basePath == null) {
            mutable.value = mutable.value.copy(message = "Directed deep resolve is available for the installed Android Auto catalog.")
            return@launch
        }
        val keys = withContext(Dispatchers.IO) {
            (database.inconclusiveKeys(hash, Int.MAX_VALUE) + database.targetedRetryKeys(hash)).distinct()
        }
        if (keys.isEmpty()) {
            mutable.value = mutable.value.copy(message = "No pending inconclusive mapping remains for directed analysis.")
            return@launch
        }
        if (!analysisOperationGate.tryBegin()) {
            mutable.value = mutable.value.copy(message = "Another catalog analysis is already running.")
            return@launch
        }
        deepResolveCancelRequested = false
        try {
            keepAnalysisAlive(true)
            mutable.value = mutable.value.copy(
            loading = true,
            deepResolveRunning = true,
            scanProgress = AnalysisProgress(AnalysisStage.DEEP_RESOLVING, 0, keys.size, "Preparing one shared DEX graph"),
            message = "Deep resolving up to ${keys.size} inconclusive mappings with one DEX graph…"
        )
            val result = withContext(Dispatchers.IO) { runCatching {
            val baseline = database.compatibilityMappingsByKeys(hash, keys.toSet())
            var saved = 0
            var resolved = 0
            var inconclusive = 0
            DexCatalogAnalyzer().analyze(
                File(basePath),
                aa.splitPaths.map(::File),
                previousMappings = baseline,
                previousBaseSha256 = hash,
                buildIdentity = BuildFingerprint.compute(aa.versionCode ?: 0, File(basePath), aa.splitPaths.map(::File)),
                deepResolveKeys = keys.toSet(),
                streamDeepResolveResults = true,
                shouldContinueDeepResolve = { !deepResolveCancelRequested },
                onDeepResolveResult = { identifier, current, total ->
                    database.updateDeepResolveResults(hash, listOf(identifier))
                    saved++
                    if (identifier.metadata["deepResolveStatus"] == "RESOLVED") resolved++ else inconclusive++
                    reportScanProgress(AnalysisProgress(AnalysisStage.DEEP_RESOLVING, current, total, identifier.key))
                },
                onProgress = ::reportScanProgress
            )
            DeepResolveOutcome(saved, resolved, inconclusive, deepResolveCancelRequested)
        } }
            val resolverRun = withContext(Dispatchers.IO) { database.resolverRunSummary(hash) }
            val pending = withContext(Dispatchers.IO) { database.actionableDeepResolveCount(hash) }
            result.onSuccess { outcome ->
            val stoppedForMemory = !outcome.cancelled && outcome.saved < keys.size
            mutable.value = mutable.value.copy(
                loading = false,
                deepResolveRunning = false,
                scanProgress = null,
                resolverRun = resolverRun,
                deepResolvePending = pending,
                message = when {
                    outcome.cancelled -> "Deep resolve cancelled: ${outcome.resolved} resolved, ${outcome.inconclusive} still inconclusive, $pending pending."
                    stoppedForMemory -> "Paused safely for memory: ${outcome.resolved} resolved, ${outcome.inconclusive} still inconclusive, $pending pending."
                    else -> "Deep resolve complete: ${outcome.resolved} resolved, ${outcome.inconclusive} still inconclusive, $pending pending."
                }
            )
            }.onFailure { error ->
            mutable.value = mutable.value.copy(loading = false, deepResolveRunning = false, scanProgress = null, resolverRun = resolverRun,
                deepResolvePending = pending, message = "Deep resolve stopped: ${error.javaClass.simpleName}: ${error.message}. Completed mappings were preserved.")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            mutable.update { it.copy(message =
                "Deep analysis failed: ${failure.javaClass.simpleName}: ${failure.message}") }
        } finally {
            deepResolveCancelRequested = false
            mutable.update { it.copy(loading = false, deepResolveRunning = false, scanProgress = null) }
            try { keepAnalysisAlive(false) } finally { analysisOperationGate.finish() }
        }
    }

    fun setDynamicOverride(identifier: DiscoveredIdentifier, rawInput: String?) = viewModelScope.launch {
        mutable.value = mutable.value.copy(saveState = SaveState.SAVING, saveTargetKey = identifier.key, message = null)
        val result = writePreferencesAndPublish { withContext(Dispatchers.IO) {
            require(mutable.value.aa.runtimeTarget) { "Imported archives are read-only; refresh the installed Android Auto before editing." }
            val build = checkNotNull(mutable.value.aa.buildFingerprintSha256) { "No installed Android Auto build" }
            val current = checkNotNull(database.details(build, identifier.key)) { "Flag absent from current build" }
            require(current.inferredType == identifier.inferredType) {
                "Flag type changed; reopen the current installed-build catalog."
            }
            val value = rawInput?.let { DynamicOverrideCodec.validate(current.inferredType, it) }
            require(current.editable && (value == null ||
                suspendedOverrideReason(current, value) == null)) {
                "Getter is no longer safely editable or value exceeds its return type."
            }
            require(probeKeyForBuild(build) != current.key) {
                "Disarm the read-only probe for this getter before editing."
            }
            require(ServiceBridge.dynamicPreferencesOrNull()?.getString(
                AdvancedOverridePolicy.preferenceKey(identifier.key), null) == null) {
                "Restore the advanced baseline before editing normally."
            }
            require(!(identifier.key == ExperimentalBooleanPolicy.KEY &&
                mutable.value.experimentalSnapshot?.buildFingerprint == mutable.value.aa.buildFingerprintSha256)) {
                "Restore the experimental baseline before editing this flag normally."
            }
            val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull()) { "Xposed service is not connected" }
            val editor = prefs.edit(); val key = DynamicOverrideCodec.preferenceKey(identifier.key)
            if (rawInput == null) {
                editor.remove(key)
                editor.remove(DynamicOverrideCodec.namePreferenceKey(identifier.key))
            } else {
                editor.putString(key, DynamicOverrideCodec.serialize(checkNotNull(value)))
                editor.putString(DynamicOverrideCodec.namePreferenceKey(identifier.key), identifier.key)
            }
            require(editor.commit()) { "Framework rejected the preference update" }
        } }
        when {
            result.isFailure -> mutable.value = mutable.value.copy(saveState = SaveState.FAILED,
                message = "Override rejected: ${result.exceptionOrNull()?.message}")
            result.getOrThrow() == ProfileMutationCoordinator.Outcome.PUBLISHED ->
                mutable.value = mutable.value.copy(saveState = SaveState.SAVED, message = "Override saved and runtime profile published. Restart Android Auto to apply.")
            else -> mutable.value = mutable.value.copy(saveState = SaveState.FAILED,
                message = "Override saved, but runtime profile was NOT published. Check LSPosed and retry publication.")
        }
    }

    private fun deltaSummary(items: List<EditableCatalogDelta>) = CatalogChangeSummary(
        items.count { it.kind == EditableDeltaKind.ADDED },
        items.count { it.kind == EditableDeltaKind.REMOVED },
        items.count { it.kind == EditableDeltaKind.CHANGED }
    )

    private fun hiddenKeysForBuild(fingerprint: String?): Set<String> {
        if (fingerprint == null) return emptySet()
        if (hiddenPreferences.getString("fingerprint", null) != fingerprint) {
            hiddenPreferences.edit().putString("fingerprint", fingerprint).remove("keys").apply()
            return emptySet()
        }
        return hiddenPreferences.getStringSet("keys", emptySet())?.toSet().orEmpty()
    }

    /** Affects only the All tab for this precise Android Auto build fingerprint. */
    fun hideSuspendedFromAll(preferenceKey: String) {
        val current = mutable.value
        val fingerprint = current.aa.buildFingerprintSha256 ?: return
        if (current.suspendedOverrides.none { it.preferenceKey == preferenceKey }) return
        val previous = hiddenKeysForBuild(fingerprint)
        val next = previous + preferenceKey
        hiddenPreferences.edit().putString("fingerprint", fingerprint)
            .putStringSet("keys", next).apply()
        mutable.value = current.copy(hiddenSuspendedKeys = next)
    }

    private fun probeKeyForBuild(buildFingerprint: String): String? =
        probePreferences.getString("build", null)?.takeIf { it == buildFingerprint }
            ?.let { probePreferences.getString("key", null) }

    /** Only arms a read-only method-call trace for the exact installed build. */
    /** Preflight12-A: choose one reviewed DOUBLE getter for a no-write runtime test. */
    fun setDoubleGetterProbe(key: String, enabled: Boolean) = viewModelScope.launch {
        val aa = mutable.value.aa
        val build = aa.buildFingerprintSha256
        if (!aa.runtimeTarget || build == null ||
            !GetterProbePolicy.isInspectedDoubleBuild(aa.baseSha256) ||
            key !in GetterProbePolicy.doubleCandidates) {
            mutable.value = mutable.value.copy(
                message = "DOUBLE probe unavailable: unsupported Android Auto build or getter.")
            return@launch
        }
        val id = withContext(Dispatchers.IO) { database.details(build, key) }
        if (id == null) {
            mutable.value = mutable.value.copy(message = "DOUBLE getter absent from the installed catalog.")
            return@launch
        }
        if (enabled && probeKeyForBuild(build) != null) {
            mutable.value = mutable.value.copy(
                message = "Disarm the existing getter probe before selecting another.")
            return@launch
        }
        setReadOnlyProbe(id, enabled)
    }

    fun setReadOnlyProbe(identifier: DiscoveredIdentifier, enabled: Boolean) = viewModelScope.launch {
        val aa = mutable.value.aa
        val fingerprint = aa.buildFingerprintSha256 ?: return@launch
        if (!aa.runtimeTarget || !mutable.value.framework.connected) {
            mutable.value = mutable.value.copy(message = "Read-only probes need the installed Android Auto and LSPosed service.")
            return@launch
        }
        val result = writePreferencesAndPublish { withContext(Dispatchers.IO) {
            if (enabled) {
                val current = checkNotNull(database.details(fingerprint, identifier.key)) { "Flag is not in the installed catalog" }
                require(GetterProbePolicy.issue(current) == null) { GetterProbePolicy.issue(current) ?: "Not eligible" }
                require(current.key == identifier.key)
                if (current.key in GetterProbePolicy.doubleCandidates) {
                    require(GetterProbePolicy.isInspectedDoubleBuild(aa.baseSha256)) {
                        "DOUBLE probe requires the exact inspected base APK."
                    }
                    val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull())
                    require(prefs.getString(DynamicOverrideCodec.preferenceKey(current.key), null) == null) {
                        "A normal DOUBLE override already owns this getter. Restore it first."
                    }
                }
                require(ServiceBridge.dynamicPreferencesOrNull()?.getString(
                    AdvancedOverridePolicy.preferenceKey(current.key), null) == null) {
                    "Restore the advanced baseline before probing the getter."
                }
                require(current.key != ExperimentalBooleanPolicy.KEY ||
                    ExperimentalBooleanSnapshot.decode(ServiceBridge.dynamicPreferencesOrNull()?.getString(
                        ExperimentalBooleanPolicy.RECORD_KEY, null)) == null) {
                    "Restore the experimental baseline before arming this getter for read-only probing."
                }
                require(probePreferences.edit().putString("build", fingerprint)
                    .putString("key", current.key).commit()) { "Failed to save probe choice" }
            } else {
                require(probeKeyForBuild(fingerprint) == identifier.key) { "Probe already changed" }
                require(probePreferences.edit().clear().commit()) { "Failed to disable probe" }
            }
        } }
        mutable.value = mutable.value.copy(message = when {
            result.isFailure -> "Getter probe rejected: ${result.exceptionOrNull()?.message}"
            result.getOrThrow() != ProfileMutationCoordinator.Outcome.PUBLISHED ->
                "Getter probe preference saved, but runtime profile was NOT published. Check LSPosed."
            enabled -> "Read-only probe armed and profile published. Restart Android Auto; observe AAX_EVENT in logcat."
            else -> "Probe disabled and profile published. Restart Android Auto to remove its installed hook."
        })
    }

    /** Only one BOOLEAN experiment is permitted in this beta4 preflight. */
    fun setExperimentalBoolean(identifier: DiscoveredIdentifier, value: Boolean) = viewModelScope.launch {
        val aa = mutable.value.aa
        val build = aa.buildFingerprintSha256
        if (!aa.runtimeTarget || build == null || !mutable.value.framework.connected) {
            mutable.value = mutable.value.copy(message = "Experimental overrides require the installed Android Auto and LSPosed service.")
            return@launch
        }
        mutable.value = mutable.value.copy(saveState = SaveState.SAVING, saveTargetKey = identifier.key, message = null)
        val result = writePreferencesAndPublish { withContext(Dispatchers.IO) {
            val current = checkNotNull(database.details(build, identifier.key)) { "Flag not found for installed Android Auto" }
            require(ExperimentalBooleanPolicy.issue(current) == null) {
                ExperimentalBooleanPolicy.issue(current) ?: "Getter not eligible"
            }
            require(probeKeyForBuild(build) != current.key) {
                "Disarm the read-only getter probe and restart Android Auto before editing."
            }
            val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull()) { "Xposed service disconnected" }
            val all = prefs.all
            val recordRaw = all[ExperimentalBooleanPolicy.RECORD_KEY] as? String
            val previous = ExperimentalBooleanSnapshot.decode(recordRaw)
            require(recordRaw == null || previous != null) { "Existing experimental snapshot is unreadable; refusing to overwrite its baseline." }
            require(previous == null || ExperimentalBooleanPolicy.matches(current, previous, build)) {
                "Previous experimental snapshot belongs to another build or getter; cannot reapply automatically."
            }
            val regular = DynamicOverrideCodec.preferenceKey(current.key)
            val name = DynamicOverrideCodec.namePreferenceKey(current.key)
            val previousNormalRaw = all[regular] as? String
            require(previous != null || previousNormalRaw == null ||
                DynamicOverrideCodec.deserialize(previousNormalRaw)?.type == DiscoveredType.BOOLEAN) {
                "Existing normal override is malformed; refusing to replace its saved value."
            }
            val updated = if (previous != null) previous.copy(value = value, active = true) else
                ExperimentalBooleanPolicy.snapshot(
                    current, build, value, previousNormalRaw, all[name] as? String,
                    System.currentTimeMillis()
                )
            require(prefs.edit().putString(ExperimentalBooleanPolicy.RECORD_KEY, updated.encode())
                .remove(regular).remove(name).commit()) { "Failed to atomically save baseline and experiment." }
        } }
        when {
            result.isFailure -> mutable.value = mutable.value.copy(saveState = SaveState.FAILED,
                message = "Experimental override rejected: ${result.exceptionOrNull()?.message}")
            result.getOrThrow() == ProfileMutationCoordinator.Outcome.PUBLISHED &&
                mutable.value.experimentalProfileReady ->
                mutable.value = mutable.value.copy(saveState = SaveState.SAVED,
                    message = "Experimental value saved and profile published. Restart Android Auto to install the new hook; check OVERRIDE_RETURNED.")
            else -> mutable.value = mutable.value.copy(saveState = SaveState.FAILED,
                message = "Experimental value saved, but its hook profile is NOT ready or published. Check LSPosed before restarting AA.")
        }
    }

    fun pauseExperimentalBoolean() = viewModelScope.launch {
        val build = mutable.value.aa.buildFingerprintSha256 ?: return@launch
        val result = writePreferencesAndPublish { withContext(Dispatchers.IO) {
            val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull())
            val record = checkNotNull(ExperimentalBooleanSnapshot.decode(
                prefs.getString(ExperimentalBooleanPolicy.RECORD_KEY, null))) { "No experimental snapshot" }
            require(record.buildFingerprint == build) { "The experiment belongs to another build." }
            require(prefs.edit().putString(ExperimentalBooleanPolicy.RECORD_KEY,
                record.copy(active = false).encode()).commit()) { "Unable to pause experiment" }
        } }
        mutable.value = mutable.value.copy(message = when {
            result.isFailure -> "Pause failed: ${result.exceptionOrNull()?.message}"
            result.getOrThrow() == ProfileMutationCoordinator.Outcome.PUBLISHED ->
                "Experimental override paused and profile published. Restart Android Auto to unload the previous hook."
            else -> "Experimental pause saved, but runtime profile was NOT published. Check LSPosed and restart AA only after recovery."
        })
    }

    fun restoreExperimentalBaseline() = viewModelScope.launch {
        val build = mutable.value.aa.buildFingerprintSha256 ?: return@launch
        val result = writePreferencesAndPublish { withContext(Dispatchers.IO) {
            val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull())
            val record = checkNotNull(ExperimentalBooleanSnapshot.decode(
                prefs.getString(ExperimentalBooleanPolicy.RECORD_KEY, null))) { "No valid baseline to restore" }
            require(record.buildFingerprint == build) { "Cannot restore a baseline onto a different Android Auto build." }
            val regular = DynamicOverrideCodec.preferenceKey(ExperimentalBooleanPolicy.KEY)
            val name = DynamicOverrideCodec.namePreferenceKey(ExperimentalBooleanPolicy.KEY)
            val editor = prefs.edit().remove(ExperimentalBooleanPolicy.RECORD_KEY)
            if (record.previousOverride == null) editor.remove(regular)
            else editor.putString(regular, record.previousOverride)
            if (record.previousName == null) editor.remove(name)
            else editor.putString(name, record.previousName)
            require(editor.commit()) { "Atomic baseline restoration failed" }
        } }
        mutable.value = mutable.value.copy(message = when {
            result.isFailure -> "Baseline restoration rejected: ${result.exceptionOrNull()?.message}"
            result.getOrThrow() == ProfileMutationCoordinator.Outcome.PUBLISHED ->
                "Original override state restored and profile published (including previous absence). Restart Android Auto."
            else -> "Original override baseline restored, but runtime profile was NOT published. Check LSPosed."
        })
    }

    /**
     * Beta4-only shadow handoff for the already proven Assistant LONG getter.
     * Preserve the normal preference LONG:3 throughout the experiment: ONLY
     * the build-scoped profile decides which single hook owns the getter.
     */
    fun startLongHandoff() = viewModelScope.launch {
        val aa = mutable.value.aa
        val build = aa.buildFingerprintSha256
        if (!aa.runtimeTarget || build == null || !mutable.value.framework.connected) {
            mutable.value = mutable.value.copy(message = "LONG handoff requires installed AA and LSPosed.")
            return@launch
        }
        mutable.value = mutable.value.copy(saveState = SaveState.SAVING,
            saveTargetKey = LongHandoffPolicy.KEY, message = null)
        val result = writePreferencesAndPublish { withContext(Dispatchers.IO) {
            val id = checkNotNull(database.details(build, LongHandoffPolicy.KEY))
            require(LongHandoffPolicy.issue(id, aa.baseSha256) == null) {
                LongHandoffPolicy.issue(id, aa.baseSha256) ?: "Not eligible"
            }
            require(probeKeyForBuild(build) != LongHandoffPolicy.KEY) {
                "Disarm the competing getter probe first."
            }
            val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull())
            val recordKey = AdvancedOverridePolicy.preferenceKey(LongHandoffPolicy.KEY)
            require(prefs.getString(recordKey, null) == null) {
                "Restore the existing Advanced record before starting a new handoff."
            }
            val normalKey = DynamicOverrideCodec.preferenceKey(LongHandoffPolicy.KEY)
            val nameKey = DynamicOverrideCodec.namePreferenceKey(LongHandoffPolicy.KEY)
            val record = LongHandoffPolicy.record(id, build, aa.baseSha256,
                prefs.getString(normalKey, null), prefs.getString(nameKey, null),
                System.currentTimeMillis())
            require(prefs.edit().putString(recordKey, record.encode()).commit()) {
                "Could not atomically save the handoff record."
            }
        } }
        when {
            result.isFailure -> mutable.value = mutable.value.copy(saveState = SaveState.FAILED,
                message = "LONG handoff rejected: ${result.exceptionOrNull()?.message}")
            result.getOrThrow() == ProfileMutationCoordinator.Outcome.PUBLISHED ->
                mutable.value = mutable.value.copy(saveState = SaveState.SAVED,
                    message = "LONG 3→3 handoff published; original LONG:3 is intact. Restart Android Auto and check ADVANCED_OVERRIDE_RETURNED.")
            else -> mutable.value = mutable.value.copy(saveState = SaveState.FAILED,
                message = "LONG handoff saved, but runtime profile was NOT published. Keep original LONG:3 and check LSPosed.")
        }
    }

    fun setAdvancedOverride(identifier: DiscoveredIdentifier, raw: String) = viewModelScope.launch {
        val aa = mutable.value.aa
        val build = aa.buildFingerprintSha256
        if (!aa.runtimeTarget || build == null || !mutable.value.framework.connected) {
            mutable.value = mutable.value.copy(message = "Advanced overrides require the installed AA and LSPosed service.")
            return@launch
        }
        mutable.value = mutable.value.copy(saveState = SaveState.SAVING, saveTargetKey = identifier.key)
        val result = writePreferencesAndPublish { withContext(Dispatchers.IO) {
            val id = checkNotNull(database.details(build, identifier.key))
            require(AdvancedOverridePolicy.issue(id) == null) { AdvancedOverridePolicy.issue(id) ?: "Not eligible" }
            require(probeKeyForBuild(build) != id.key) { "Disarm the read-only probe first." }
            require(id.key != ExperimentalBooleanPolicy.KEY) { "Restore the legacy experimental baseline first." }
            val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull())
            val all = prefs.all
            val recordKey = AdvancedOverridePolicy.preferenceKey(id.key)
            val serialized = all[recordKey] as? String
            val earlier = AdvancedOverrideRecord.decode(serialized)
            require(serialized == null || earlier != null) { "Unreadable advanced snapshot; refusing to overwrite." }
            require(earlier == null || AdvancedOverridePolicy.matches(id, earlier, build)) {
                "Saved experiment belongs to another build or getter; keep it suspended."
            }
            val normalKey = DynamicOverrideCodec.preferenceKey(id.key)
            val nameKey = DynamicOverrideCodec.namePreferenceKey(id.key)
            val record = if (earlier == null) AdvancedOverridePolicy.newRecord(id, build, raw,
                all[normalKey] as? String, all[nameKey] as? String, System.currentTimeMillis())
            else earlier.copy(value = DynamicOverrideCodec.validate(earlier.type, raw).encodedValue, active = true)
            require(prefs.edit().putString(recordKey, record.encode()).remove(normalKey).remove(nameKey).commit()) {
                "Unable to atomically preserve the baseline."
            }
        } }
        when {
            result.isFailure -> mutable.value = mutable.value.copy(saveState = SaveState.FAILED,
                message = "Advanced override rejected: ${result.exceptionOrNull()?.message}")
            result.getOrThrow() == ProfileMutationCoordinator.Outcome.PUBLISHED ->
                mutable.value = mutable.value.copy(saveState = SaveState.SAVED,
                    message = "Advanced value saved and profile published. Restart Android Auto and inspect LSPosed events.")
            else -> mutable.value = mutable.value.copy(saveState = SaveState.FAILED,
                message = "Advanced value saved, but runtime profile was NOT published. Check LSPosed and retry.")
        }
    }

    fun pauseAdvancedOverride(key: String) = setAdvancedActivity(key, false)
    fun resumeAdvancedOverride(key: String) = setAdvancedActivity(key, true)

    private fun setAdvancedActivity(key: String, enabled: Boolean) = viewModelScope.launch {
        val build = mutable.value.aa.buildFingerprintSha256 ?: return@launch
        val result = writePreferencesAndPublish { withContext(Dispatchers.IO) {
            val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull())
            val storageKey = AdvancedOverridePolicy.preferenceKey(key)
            val record = checkNotNull(AdvancedOverrideRecord.decode(prefs.getString(storageKey, null)))
            require(record.key == key && record.buildFingerprint == build) { "Record from another AA build." }
            if (enabled) {
                val id = checkNotNull(database.details(build, key))
                require(AdvancedOverridePolicy.matches(id, record, build, mutable.value.aa.baseSha256)) {
                    "Method/type changed; cannot resume."
                }
                if (record.handoffMode != null) require(LongHandoffPolicy.baselineIntact(
                    record, prefs.getString(DynamicOverrideCodec.preferenceKey(key), null),
                    prefs.getString(DynamicOverrideCodec.namePreferenceKey(key), null))) {
                    "Original LONG:3 changed; cannot resume."
                }
            }
            require(prefs.edit().putString(storageKey, record.copy(active = enabled).encode()).commit())
        } }
        mutable.value = mutable.value.copy(message = when {
            result.isFailure -> "Advanced change failed: ${result.exceptionOrNull()?.message}"
            result.getOrThrow() == ProfileMutationCoordinator.Outcome.PUBLISHED ->
                "Advanced override ${if (enabled) "resumed" else "paused"} and profile published; restart AA."
            else -> "Advanced ${if (enabled) "resume" else "pause"} saved, but runtime profile was NOT published. Check LSPosed."
        })
    }

    fun restoreAdvancedBaseline(key: String) = viewModelScope.launch {
        val build = mutable.value.aa.buildFingerprintSha256 ?: return@launch
        val result = writePreferencesAndPublish { withContext(Dispatchers.IO) {
            val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull())
            val storageKey = AdvancedOverridePolicy.preferenceKey(key)
            val record = checkNotNull(AdvancedOverrideRecord.decode(prefs.getString(storageKey, null))) {
                "Missing or unreadable baseline"
            }
            require(record.key == key && record.buildFingerprint == build) {
                "Cannot restore previous build's baseline on the current build."
            }
            val normalKey = DynamicOverrideCodec.preferenceKey(key)
            val nameKey = DynamicOverrideCodec.namePreferenceKey(key)
            val edit = prefs.edit().remove(storageKey)
            if (record.handoffMode != null) {
                require(record.handoffMode == LongHandoffPolicy.MODE &&
                    record.buildFingerprint == build &&
                    LongHandoffPolicy.baselineIntact(record,
                        prefs.getString(normalKey, null), prefs.getString(nameKey, null))) {
                    "Normal LONG baseline changed; refuse to overwrite it."
                }
                // Shadow handoff NEVER removed the normal preference.
            } else {
                if (record.previousOverride == null) edit.remove(normalKey)
                else edit.putString(normalKey, record.previousOverride)
                if (record.previousName == null) edit.remove(nameKey)
                else edit.putString(nameKey, record.previousName)
            }
            require(edit.commit())
        } }
        mutable.value = mutable.value.copy(message = when {
            result.isFailure -> "Restore failed: ${result.exceptionOrNull()?.message}"
            result.getOrThrow() == ProfileMutationCoordinator.Outcome.PUBLISHED ->
                "Advanced baseline restored and profile published, including previous absence. Restart AA."
            else -> "Advanced baseline restored, but runtime profile was NOT published. Check LSPosed."
        })
    }

    fun pauseAllAdvanced() = viewModelScope.launch {
        val build = mutable.value.aa.buildFingerprintSha256 ?: return@launch
        val result = writePreferencesAndPublish { withContext(Dispatchers.IO) {
            val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull())
            val editor = prefs.edit()
            prefs.all.forEach { (name, value) ->
                if (name.startsWith(AdvancedOverridePolicy.PREFIX)) {
                    val record = checkNotNull(AdvancedOverrideRecord.decode(value as? String))
                    require(AdvancedOverridePolicy.preferenceKey(record.key) == name) { "Invalid advanced storage key." }
                    if (record.buildFingerprint == build) editor.putString(name, record.copy(active = false).encode())
                }
            }
            require(editor.commit())
        } }
        mutable.value = mutable.value.copy(message = when {
            result.isFailure -> "Pause all rejected: ${result.exceptionOrNull()?.message}"
            result.getOrThrow() == ProfileMutationCoordinator.Outcome.PUBLISHED ->
                "All current-build Advanced overrides paused and profile published; restart AA."
            else -> "Advanced overrides paused in storage, but runtime profile was NOT published. Check LSPosed."
        })
    }

    fun restoreAllAdvanced() = viewModelScope.launch {
        val build = mutable.value.aa.buildFingerprintSha256 ?: return@launch
        val result = writePreferencesAndPublishWithResult { withContext(Dispatchers.IO) {
            val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull())
            val records = prefs.all.filterKeys { it.startsWith(AdvancedOverridePolicy.PREFIX) }
                .map { (key, value) ->
                    val record = checkNotNull(AdvancedOverrideRecord.decode(value as? String)) {
                        "Unreadable advanced record; abort restore."
                    }
                    require(AdvancedOverridePolicy.preferenceKey(record.key) == key) { "Invalid record key." }
                    key to record
                }.filter { it.second.buildFingerprint == build }
            val editor = prefs.edit()
            records.forEach { (storageKey, record) ->
                editor.remove(storageKey)
                val normalKey = DynamicOverrideCodec.preferenceKey(record.key)
                val nameKey = DynamicOverrideCodec.namePreferenceKey(record.key)
                if (record.handoffMode != null) {
                    require(record.handoffMode == LongHandoffPolicy.MODE &&
                        LongHandoffPolicy.baselineIntact(record,
                            prefs.getString(normalKey, null), prefs.getString(nameKey, null))) {
                        "Normal LONG baseline changed; abort entire restore-all."
                    }
                } else {
                    if (record.previousOverride == null) editor.remove(normalKey)
                    else editor.putString(normalKey, record.previousOverride)
                    if (record.previousName == null) editor.remove(nameKey)
                    else editor.putString(nameKey, record.previousName)
                }
            }
            require(editor.commit())
            records.size
        } }
        mutable.value = mutable.value.copy(message = when {
            result.isFailure -> "Bulk restore rejected: ${result.exceptionOrNull()?.message}"
            result.getOrThrow().second == ProfileMutationCoordinator.Outcome.PUBLISHED ->
                "${result.getOrThrow().first} current-build Advanced baselines restored and profile published; restart Android Auto."
            else -> "${result.getOrThrow().first} Advanced baselines restored in storage, but runtime profile was NOT published. Check LSPosed."
        })
    }

    fun discardSuspendedOverride(preferenceKey: String) = viewModelScope.launch {
        if (!mutable.value.aa.runtimeTarget || !preferenceKey.startsWith(DynamicOverrideCodec.PREFIX)) return@launch
        val result = writePreferencesAndPublish { withContext(Dispatchers.IO) {
            val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull()) { "Xposed service is not connected" }
            val editor = prefs.edit().remove(preferenceKey)
            DynamicOverrideCodec.nameKeyForPreference(preferenceKey)?.let(editor::remove)
            require(editor.commit()) { "Framework rejected override removal" }
        } }
        mutable.value = mutable.value.copy(message = when {
            result.isFailure -> "Discard failed: ${result.exceptionOrNull()?.message}"
            result.getOrThrow() == ProfileMutationCoordinator.Outcome.PUBLISHED ->
                "Suspended override removed and runtime profile published."
            else -> "Suspended override removed, but runtime profile was NOT published. Check LSPosed."
        })
    }

    fun restoreAllDynamic() = viewModelScope.launch {
        if (!mutable.value.aa.runtimeTarget) return@launch
        mutable.value = mutable.value.copy(saveState = SaveState.SAVING, saveTargetKey = null)
        val result = writePreferencesAndPublish {
            withContext(Dispatchers.IO) {
                val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull()) { "LSPosed is disconnected" }
                val editor = prefs.edit().remove(ExperimentalBooleanPolicy.RECORD_KEY)
                prefs.all.keys.filter { it.startsWith(DynamicOverrideCodec.PREFIX) ||
                    it.startsWith(DynamicOverrideCodec.NAME_PREFIX) ||
                    it.startsWith(AdvancedOverridePolicy.PREFIX) }.forEach(editor::remove)
                require(editor.commit()) { "Framework rejected reset" }
            }
        }
        mutable.value = mutable.value.copy(
            saveState = if (result.getOrNull() == ProfileMutationCoordinator.Outcome.PUBLISHED)
                SaveState.SAVED else SaveState.FAILED,
            message = when {
                result.isFailure -> "Reset rejected: ${result.exceptionOrNull()?.message}"
                result.getOrThrow() == ProfileMutationCoordinator.Outcome.PUBLISHED ->
                    "All override records removed and runtime profile published. Restart Android Auto."
                else -> "Override records removed, but runtime profile was NOT published. Check LSPosed."
            })
    }

    fun exportDynamicJson(): String = JSONObject().apply {
        put("schemaVersion", 3); put("package", InstalledAaScanner.AA_PACKAGE)
        put("versionCode", mutable.value.aa.versionCode ?: JSONObject.NULL); put("buildFingerprintSha256", mutable.value.aa.buildFingerprintSha256 ?: JSONObject.NULL)
        put("overrides", JSONObject().apply { mutable.value.dynamicOverrides.forEach { (key, value) -> put(key, DynamicOverrideCodec.serialize(value)) } })
        put("suspended", JSONObject().apply {
            mutable.value.suspendedOverrides.forEach { entry ->
                entry.serializedValue?.let { serialized ->
                    put(entry.preferenceKey, JSONObject().apply {
                        put("raw", serialized)
                        put("identifier", entry.identifierKey ?: JSONObject.NULL)
                    })
                }
            }
        })
    }.toString(2)

    /** Only the exact current AA build may export/restore all special baselines. */
    fun exportCompleteRecoveryJson(): String {
        val aa = mutable.value.aa
        require(aa.runtimeTarget && aa.buildFingerprintSha256 != null && aa.versionCode != null) {
            "Installed Android Auto is not available."
        }
        val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull()) {
            "LSPosed remote preferences are disconnected."
        }
        return FullRecoveryBundle.create(prefs.all, aa.buildFingerprintSha256, aa.versionCode)
    }

    fun reportRecoveryMessage(text: String) {
        mutable.update { it.copy(message = text.take(250)) }
    }

    /** Exact replacement of managed keys only; never touches debug/traces or slot pointers. */
    fun restoreCompleteRecoveryJson(text: String) = viewModelScope.launch {
        mutable.update { it.copy(saveState = SaveState.SAVING, message = "Checking complete recovery bundle…") }
        val result = withContext(Dispatchers.IO) { runCatching {
            profileRefreshMutex.withLock {
                val aa = mutable.value.aa
                val build = checkNotNull(aa.buildFingerprintSha256) { "No installed build fingerprint" }
                val version = checkNotNull(aa.versionCode) { "No installed AA version code" }
                require(aa.runtimeTarget) { "Imported archives are read-only." }
                val bundle = FullRecoveryBundle.decode(text, build, version)
                val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull()) {
                    "LSPosed is not connected"
                }
                val current = prefs.all
                // Paused/historical snapshots must be recoverable even if their
                // former getter is no longer eligible. Only hooks to be resumed
                // on this build require current runtime-eligibility checks.
                val activeAdv = bundle.activeAdvancedRecords()
                val exp = bundle.activeExperimentalRecord()
                val keys = activeAdv.map { it.key }.toSet() +
                    listOfNotNull(if (exp != null) ExperimentalBooleanPolicy.KEY else null)
                val identifiers = database.identifiersByKeys(build, keys)
                activeAdv.forEach { record ->
                    val id = checkNotNull(identifiers[record.key]) {
                        "Advanced getter is absent from the installed build: ${record.key}"
                    }
                    require(AdvancedOverridePolicy.matches(id, record, build, aa.baseSha256)) {
                        "Advanced getter or safety policy changed: ${record.key}"
                    }
                    if (record.handoffMode != null) require(LongHandoffPolicy.baselineIntact(
                        record,
                        bundle.entries[DynamicOverrideCodec.preferenceKey(record.key)],
                        bundle.entries[DynamicOverrideCodec.namePreferenceKey(record.key)])) {
                        "LONG handoff baseline missing or modified."
                    }
                }
                if (exp != null) {
                    val id = checkNotNull(identifiers[ExperimentalBooleanPolicy.KEY]) {
                        "Experimental BOOLEAN getter is missing."
                    }
                    require(ExperimentalBooleanPolicy.matches(id, exp, build)) {
                        "Experimental BOOLEAN getter or policy no longer matches."
                    }
                }
                require(mutable.value.aa.buildFingerprintSha256 == build) {
                    "Android Auto build changed before restoring."
                }
                val edit = prefs.edit()
                current.keys.filter(FullRecoveryBundle::managedKey).forEach(edit::remove)
                bundle.entries.forEach { (key, value) -> edit.putString(key, value) }
                require(edit.commit()) { "Framework rejected the complete recovery transaction." }
                // Publish under the same gate used by normal/Advanced edits.
                // The committed recovery remains available if publication fails.
                bundle to refreshOverridesAndProfileLocked()
            }
        } }
        result.onSuccess { (bundle, publicationSucceeded) ->
            val published = publicationSucceeded &&
                mutable.value.aa.buildFingerprintSha256 == bundle.fingerprint
            mutable.update { it.copy(saveState = if (published) SaveState.SAVED else SaveState.FAILED,
                message = if (published)
                    "Restored ${bundle.normal} normal, ${bundle.advanced} Advanced and ${bundle.experimental} Experimental records. Restart Android Auto after checking settings."
                else "Recovery records were restored, but the profile was NOT published. Check LSPosed connection and Diagnostics before restarting Android Auto.") }
        }.onFailure { ex ->
            mutable.update { it.copy(saveState = SaveState.FAILED,
                message = "Complete recovery rejected without changes: ${ex.message}") }
        }
    }

    fun importDynamicJson(text: String) = viewModelScope.launch {
        if (!mutable.value.aa.runtimeTarget) { mutable.value = mutable.value.copy(message = "Imported archives are read-only."); return@launch }
        mutable.value = mutable.value.copy(saveState = SaveState.SAVING)
        val result = writePreferencesAndPublishWithResult { withContext(Dispatchers.IO) {
            val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull()) { "Xposed service is not connected" }
            require(prefs.getString(ExperimentalBooleanPolicy.RECORD_KEY, null) == null &&
                prefs.all.keys.none { it.startsWith(AdvancedOverridePolicy.PREFIX) }) {
                "Restore experimental and advanced baselines before importing standard overrides."
            }
            val root = JSONObject(text); val schema = root.getInt("schemaVersion"); require(schema in 2..3) { "Unsupported schema" }
            val sameBuild = if (schema >= 3) root.optString("buildFingerprintSha256").equals(mutable.value.aa.buildFingerprintSha256, true)
                else root.optString("baseSha256").equals(mutable.value.aa.baseSha256, true)
            require(sameBuild) { "Backup belongs to another Android Auto build; nothing was applied." }
            val values = root.getJSONObject("overrides"); val keys = buildSet { values.keys().forEach(::add) }
            val byKey = database.identifiersByKeys(checkNotNull(mutable.value.aa.buildFingerprintSha256), keys)
            val accepted = mutableMapOf<String, TypedOverride>(); val rejected = mutableSetOf<String>()
            keys.forEach { key ->
                val identifier = byKey[key]
                val value = DynamicOverrideCodec.deserialize(values.getString(key))
                if (suspendedOverrideReason(identifier, value) == null) accepted[key] = checkNotNull(value)
                else rejected += key
            }
            val editor=prefs.edit()
            accepted.forEach { (key,value) ->
                editor.putString(DynamicOverrideCodec.preferenceKey(key),DynamicOverrideCodec.serialize(value))
                editor.putString(DynamicOverrideCodec.namePreferenceKey(key),key)
            }
            val suspendedImport = root.optJSONObject("suspended")
            suspendedImport?.keys()?.forEach { preferenceKey ->
                if (!preferenceKey.startsWith(DynamicOverrideCodec.PREFIX)) return@forEach
                val record = suspendedImport.optJSONObject(preferenceKey) ?: return@forEach
                val raw = record.optString("raw")
                if (DynamicOverrideCodec.deserialize(raw) == null) return@forEach
                // Never take over a currently valid mapping restored above.
                if (accepted.keys.any { DynamicOverrideCodec.preferenceKey(it) == preferenceKey }) return@forEach
                editor.putString(preferenceKey, raw)
                val identifierName = record.optString("identifier").takeIf { it.isNotBlank() && it != "null" }
                if (identifierName != null && DynamicOverrideCodec.preferenceKey(identifierName) == preferenceKey)
                    editor.putString(DynamicOverrideCodec.namePreferenceKey(identifierName), identifierName)
            }
            require(editor.commit())
            accepted.size to rejected.size
        } }
        result.onSuccess { (counts, publication) ->
            val (accepted, rejected) = counts
            mutable.value = mutable.value.copy(
                saveState = if (publication == ProfileMutationCoordinator.Outcome.PUBLISHED) SaveState.SAVED else SaveState.FAILED,
                message = if (publication == ProfileMutationCoordinator.Outcome.PUBLISHED)
                    "Imported $accepted override(s); rejected $rejected; runtime profile published."
                else "Imported $accepted override(s); rejected $rejected; runtime profile was NOT published. Check LSPosed.")
        }.onFailure { mutable.value = mutable.value.copy(saveState = SaveState.FAILED, message = "Import failed: ${it.message}") }
    }

    /** Prevent queued edits from writing into a catalog that changed before the lock. */
    private fun requireCurrentInstalledBuild(expected: String?) {
        require(expected != null && mutable.value.aa.runtimeTarget &&
            mutable.value.aa.buildFingerprintSha256 == expected) {
            "Installed Android Auto build changed before saving; reopen the current catalog."
        }
    }

    private suspend fun <T> writePreferencesAndPublishWithResult(write: suspend () -> T):
        Result<Pair<T, ProfileMutationCoordinator.Outcome>> {
        val expectedBuild = mutable.value.aa.buildFingerprintSha256
        return try {
            Result.success(profileMutationCoordinator.executeWithResult({
                requireCurrentInstalledBuild(expectedBuild)
                write()
            }) { refreshOverridesAndProfileLocked() })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    /**
     * Keep the preference commit and profile publication in one serialized scope.
     * A saved value remains recoverable when publication fails, but is not reported
     * as applied until the verified slot pointer is committed.
     */
    private suspend fun writePreferencesAndPublish(write: suspend () -> Unit):
        Result<ProfileMutationCoordinator.Outcome> {
        val expectedBuild = mutable.value.aa.buildFingerprintSha256
        return try {
            Result.success(profileMutationCoordinator.execute({
                requireCurrentInstalledBuild(expectedBuild)
                write()
            }) { refreshOverridesAndProfileLocked() })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    private suspend fun refreshOverridesAndProfile(): Boolean =
        profileRefreshMutex.withLock { refreshOverridesAndProfileLocked() }

    private suspend fun refreshOverridesAndProfileLocked(): Boolean {
        val aa = mutable.value.aa
        if (!aa.runtimeTarget || aa.baseSha256 == null || aa.buildFingerprintSha256 == null) return false
        val result = withContext(Dispatchers.IO) { runCatching {
            // A transient LSPosed disconnection must not publish an empty profile.
            // The last selected verified slot remains active until reconnection.
            val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull()) {
                "Remote preferences unavailable; preserving last published profile"
            }
            val allPrefs = prefs.all.toMutableMap()
            // Retire the two historical diagnostics no longer reachable from
            // the UI. Disable persisted VTC trace before publishing a new
            // profile; disarm old DOUBLE read-only probes without affecting
            // unrelated probes or experiment baselines.
            val obsoleteTraceKey =
                io.github.highwindbr.aaxp.runtime.VideoTimingProtocolTrace.KEY
            if (io.github.highwindbr.aaxp.runtime.VideoTimingProtocolTrace.enabled(
                    allPrefs[obsoleteTraceKey] as? String)) {
                check(prefs.edit().putString(obsoleteTraceKey, "false").commit()) {
                    "Could not disable retired Video Timing trace"
                }
                allPrefs[obsoleteTraceKey] = "false"
            }
            if (probePreferences.getString("key", null) in GetterProbePolicy.doubleCandidates.keys) {
                check(probePreferences.edit().clear().commit()) {
                    "Could not disarm retired DOUBLE probe"
                }
            }
            val protocolTraceOn = false
            val mediaStartupTraceOn =
                io.github.highwindbr.aaxp.runtime.MediaStartupTrace.isSupported(aa.baseSha256) &&
                io.github.highwindbr.aaxp.runtime.MediaStartupTrace.enabled(
                    allPrefs[io.github.highwindbr.aaxp.runtime.MediaStartupTrace.KEY] as? String)
            // Report the saved opt-in even when a new AA build is unsupported,
            // so Diagnostics can offer a visible OFF switch.
            val mediaStartupAutoOn =
                io.github.highwindbr.aaxp.runtime.MediaStartupAutoGate.enabled(
                    allPrefs[io.github.highwindbr.aaxp.runtime.MediaStartupAutoGate.KEY] as? String)
            val savedLab = mediaLabPreferences.getString(aa.buildFingerprintSha256, null)
                ?.let { runCatching { JSONObject(it) }.getOrNull() }
                ?.takeIf { it.optString("buildFingerprintSha256") == aa.buildFingerprintSha256 &&
                    it.optString("baseSha256") == aa.baseSha256 &&
                    it.optString("mode") in setOf(
                        io.github.highwindbr.aaxp.runtime.MediaStartupLabProfile.LEGACY_MODE,
                        io.github.highwindbr.aaxp.runtime.MediaStartupLabProfile.MODE) }
            val labAutoCapable = savedLab?.optString("mode") ==
                io.github.highwindbr.aaxp.runtime.MediaStartupLabProfile.MODE &&
                savedLab.optJSONObject("roles")?.has("activity.firstProjected") == true
            val labAutoEnabled = labAutoCapable &&
                (mediaLabPreferences.getString("auto-selected:${aa.buildFingerprintSha256}", "false") == "true")
            // Restore remote preference only for this installed build.
            if ((allPrefs[
                io.github.highwindbr.aaxp.runtime.MediaStartupDynamicAuto.KEY] as? String)
                != labAutoEnabled.toString()) {
                check(prefs.edit().putString(
                    io.github.highwindbr.aaxp.runtime.MediaStartupDynamicAuto.KEY,
                    labAutoEnabled.toString()).commit()) { "Unable to synchronize PF6 auto preference" }
            }
            val selectedRoute = mediaLabPreferences.getString(
                "route:${aa.buildFingerprintSha256}", "A")
                ?.takeIf { it == "A" || it == "B" } ?: "A"
            // Remote preference applies in :projection; restore each build's
            // preferred route before publishing its verified profile.
            if ((allPrefs[
                io.github.highwindbr.aaxp.runtime.MediaStartupLabGate.ROUTE_KEY] as? String)
                != selectedRoute && savedLab != null) {
                check(prefs.edit().putString(
                    io.github.highwindbr.aaxp.runtime.MediaStartupLabGate.ROUTE_KEY,
                    selectedRoute).commit()) { "Unable to synchronize PF6 route" }
            }
            val snapshot = allPrefs.mapNotNull { (key, value) ->
                if (key.startsWith(DynamicOverrideCodec.PREFIX) && value is String) key to value else null
            }.toMap()
            val names = database.historicalOverrideNames(snapshot.keys)
            val resolved = database.identifiersByPreferenceKeys(aa.buildFingerprintSha256, snapshot.keys).toMap()
            val nameEditor = prefs.edit()
            var recoveredNames = 0
            val advancedRecords = allPrefs.mapNotNull { (key, raw) ->
                if (!key.startsWith(AdvancedOverridePolicy.PREFIX)) null
                else AdvancedOverrideRecord.decode(raw as? String)?.takeIf {
                    AdvancedOverridePolicy.preferenceKey(it.key) == key
                }
            }
            val experimental = ExperimentalBooleanSnapshot.decode(
                allPrefs[ExperimentalBooleanPolicy.RECORD_KEY] as? String)
            val activeExperiment = experimental?.takeIf { it.active && it.buildFingerprint == aa.buildFingerprintSha256 }
            val experimentId = activeExperiment?.let { database.details(aa.buildFingerprintSha256, ExperimentalBooleanPolicy.KEY) }
            val approvedExperiment = experimentId?.takeIf { id ->
                ExperimentalBooleanPolicy.matches(id, checkNotNull(activeExperiment), aa.buildFingerprintSha256)
            }
            val active = mutableMapOf<String, TypedOverride>(); val suspended = mutableListOf<SuspendedOverride>(); val mappings = mutableListOf<DynamicProfile.Mapping>()
            val claimedNormalGetters = mutableMapOf<Triple<String, String, String>, String>()
            // Stable arbitration avoids registering two competing hooks on the same getter.
            snapshot.toSortedMap().forEach { (preferenceKey, raw) ->
                val identifier = resolved[preferenceKey]
                val value = DynamicOverrideCodec.deserialize(raw)
                val reason = suspendedOverrideReason(identifier, value)
                val nameKey = DynamicOverrideCodec.nameKeyForPreference(preferenceKey)
                val storedName = nameKey?.let { allPrefs[it] as? String }
                    ?.takeIf { DynamicOverrideCodec.preferenceKey(it) == preferenceKey }
                val history = names[preferenceKey]
                val identifierName = identifier?.key ?: storedName ?: history?.first
                if (storedName == null && identifierName != null && nameKey != null) {
                    nameEditor.putString(nameKey, identifierName)
                    recoveredNames++
                }
                if (reason != null) suspended += SuspendedOverride(
                    preferenceKey, identifierName, value?.type?.name, identifier?.inferredType?.name,
                    reason, value?.encodedValue, raw, history?.second
                )
                else {
                    val safeIdentifier = checkNotNull(identifier); val safeValue = checkNotNull(value); val method = safeIdentifier.getterCandidates.single()
                    val cls = method.className.removePrefix("L").removeSuffix(";").replace('/', '.')
                    if (!claimNormalGetter(claimedNormalGetters, safeIdentifier.key,
                            cls, method.methodName, method.descriptor)) {
                        suspended += SuspendedOverride(preferenceKey, identifierName,
                            safeValue.type.name, safeIdentifier.inferredType.name,
                            SuspendedOverrideReason.SUSPENDED_TARGET_COLLISION,
                            safeValue.encodedValue, raw, history?.second)
                    } else {
                        active[safeIdentifier.key] = safeValue
                        mappings += DynamicProfile.Mapping(safeIdentifier.key, preferenceKey,
                            cls, method.methodName, method.descriptor, safeIdentifier.inferredType.name)
                    }
                }
            }
            if (recoveredNames > 0) require(nameEditor.commit()) { "Failed to persist recovered mapping names" }
            val probeKey = probeKeyForBuild(aa.buildFingerprintSha256)
            val probeIdentifier = probeKey?.let { database.details(aa.buildFingerprintSha256, it) }
            val selected = probeIdentifier?.takeIf {
                GetterProbePolicy.issue(it) == null &&
                (it.key !in GetterProbePolicy.doubleCandidates ||
                    GetterProbePolicy.isInspectedDoubleBuild(aa.baseSha256))
            }
            val probes = selected?.let { id ->
                val method = id.getterCandidates.single()
                val className = method.className.removePrefix("L").removeSuffix(";").replace('/', '.')
                val conflict = mappings.any { it.className == className && it.methodName == method.methodName && it.descriptor == method.descriptor }
                if (conflict) emptyList() else listOf(DynamicProfile.Probe(id.key, className, method.methodName, method.descriptor))
            }.orEmpty()
            val experimentalMappings = approvedExperiment?.let { id ->
                val getter = id.getterCandidates.single()
                val className = getter.className.removePrefix("L").removeSuffix(";").replace('/', '.')
                val conflict = mappings.any { it.className == className && it.methodName == getter.methodName && it.descriptor == getter.descriptor } ||
                    probes.any { it.className == className && it.methodName == getter.methodName && it.descriptor == getter.descriptor }
                if (conflict) emptyList() else listOf(DynamicProfile.ExperimentalMapping(
                    id.key, className, getter.methodName, getter.descriptor
                ))
            }.orEmpty()
            // Handoff controls hook ownership, not the stored normal LONG:3.
            // Keep the preference intact so an already-running normal hook can
            // continue returning 3 until AA restarts into its new profile.
            val handoff = advancedRecords.singleOrNull {
                it.key == LongHandoffPolicy.KEY && it.active &&
                    it.buildFingerprint == aa.buildFingerprintSha256 &&
                    it.handoffMode == LongHandoffPolicy.MODE
            }
            val handoffId = handoff?.let {
                database.details(aa.buildFingerprintSha256, LongHandoffPolicy.KEY)
            }
            val handoffValid = handoff != null && handoffId != null &&
                LongHandoffPolicy.matches(handoffId, handoff,
                    aa.buildFingerprintSha256, aa.baseSha256) &&
                LongHandoffPolicy.baselineIntact(handoff,
                    allPrefs[DynamicOverrideCodec.preferenceKey(LongHandoffPolicy.KEY)] as? String,
                    allPrefs[DynamicOverrideCodec.namePreferenceKey(LongHandoffPolicy.KEY)] as? String)
            if (handoffValid) {
                mappings.removeAll { it.key == LongHandoffPolicy.KEY }
            }
            val reserved = (mappings.map { Triple(it.className, it.methodName, it.descriptor) } +
                probes.map { Triple(it.className, it.methodName, it.descriptor) } +
                experimentalMappings.map { Triple(it.className, it.methodName, it.descriptor) }).toMutableSet()
            val advancedMappings = mutableListOf<DynamicProfile.AdvancedMapping>()
            advancedRecords.filter { it.active && it.buildFingerprint == aa.buildFingerprintSha256 }.forEach { record ->
                val id = database.details(aa.buildFingerprintSha256, record.key)
                if (id != null && AdvancedOverridePolicy.matches(id, record, aa.buildFingerprintSha256, aa.baseSha256) &&
                    (record.handoffMode == null || (handoffValid && record.key == LongHandoffPolicy.KEY))) {
                    val getter = id.getterCandidates.single()
                    val cls = getter.className.removePrefix("L").removeSuffix(";").replace('/', '.')
                    val token = Triple(cls, getter.methodName, getter.descriptor)
                    if (reserved.add(token)) advancedMappings += DynamicProfile.AdvancedMapping(
                        id.key, cls, getter.methodName, getter.descriptor,
                        id.inferredType.name, AdvancedOverridePolicy.preferenceKey(id.key)
                    )
                }
            }
            val published = ServiceBridge.publishProfile(aa.buildFingerprintSha256, aa.baseSha256,
                aa.versionCode ?: 0, aa.packageArtifactSha256, aa.dexSha256, mappings, probes,
                experimentalMappings, advancedMappings, protocolTraceOn, mediaStartupTraceOn, savedLab)
            OverrideRefreshOutcome(active, suspended, published.isSuccess, published.exceptionOrNull(),
                experimental, published.isSuccess && experimentalMappings.isNotEmpty(),
                advancedRecords, if (published.isSuccess) advancedMappings.size else 0,
                protocolTraceOn, mediaStartupTraceOn, mediaStartupAutoOn,
                savedLab != null, selectedRoute, labAutoCapable, labAutoEnabled)
        } }
        // Build state is handed off under the same mutex: stale queued refreshes
        // cannot overwrite an imported archive or a newly installed AA build.
        if (mutable.value.aa.buildFingerprintSha256 != aa.buildFingerprintSha256 ||
            !mutable.value.aa.runtimeTarget) return false
        result.onSuccess { outcome -> mutable.value=mutable.value.copy(dynamicOverrides=outcome.active,
            suspendedOverrides=outcome.suspended, probeTargetKey=probeKeyForBuild(aa.buildFingerprintSha256),
            experimentalSnapshot=outcome.experimental, experimentalProfileReady=outcome.experimentalReady,
            advancedRecords=outcome.advanced, advancedPublishedCount=outcome.advancedPublishedCount,
            videoTimingTraceEnabled=outcome.videoTimingTraceEnabled,
            mediaStartupTraceEnabled=outcome.mediaStartupTraceEnabled,
            mediaStartupAutoEnabled=outcome.mediaStartupAutoEnabled,
            mediaLabArmed=outcome.mediaLabArmed,
            mediaLabRoute=outcome.mediaLabRoute,
            mediaLabAutoCapable=outcome.mediaLabAutoCapable,
            mediaLabAutoEnabled=outcome.mediaLabAutoEnabled,
            profilePublished=outcome.published,
            message=outcome.error?.let { "Dynamic profile was not published: ${it.message}" }) }
            .onFailure { mutable.value=mutable.value.copy(profilePublished=false,message="Dynamic profile was not published: ${it.message}") }
        return result.getOrNull()?.published == true
    }

    private fun csv(value: String) = "\"${value.replace("\"", "\"\"")}\""

    private fun keepAnalysisAlive(active: Boolean) {
        val context = getApplication<Application>()
        val intent = Intent(context, AnalysisKeepAliveService::class.java)
        if (active) ContextCompat.startForegroundService(context, intent) else context.stopService(intent)
    }
}
