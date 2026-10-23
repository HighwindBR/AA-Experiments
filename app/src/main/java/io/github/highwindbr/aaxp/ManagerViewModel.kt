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

data class ManagerUiState(
    val loading: Boolean = true,
    val aa: AaInstallation = AaInstallation(false),
    val framework: FrameworkState = FrameworkState(),
    val discovered: List<DiscoveredIdentifier> = emptyList(),
    val catalog: List<DiscoveredIdentifier> = emptyList(),
    val catalogTotal: Int = 0,
    val selectedDetails: DiscoveredIdentifier? = null,
    val detailsLoading: Boolean = false,
    val exportPayload: ExportPayload? = null,
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
    val scanProgress: AnalysisProgress? = null,
    val saveState: SaveState = SaveState.IDLE,
    val saveTargetKey: String? = null,
    val message: String? = null
)

enum class ExportScope { EDITABLE, MODIFIED, ALL_DISCOVERIES }
enum class ExportFormat { JSON, CSV }
data class ExportPayload(val fileName: String, val mimeType: String, val content: String)
private data class DeepResolveOutcome(val saved: Int, val resolved: Int, val inconclusive: Int, val cancelled: Boolean)
private data class OverrideRefreshOutcome(val active: Map<String, TypedOverride>, val suspended: List<SuspendedOverride>, val published: Boolean, val error: Throwable?, val experimental: ExperimentalBooleanSnapshot?, val experimentalReady: Boolean, val advanced: List<AdvancedOverrideRecord>, val advancedPublishedCount: Int, val videoTimingTraceEnabled: Boolean)

class ManagerViewModel(application: Application) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(ManagerUiState())
    private val database = CatalogDatabase(application)
    private val hiddenPreferences = application.getSharedPreferences("suspended_override_visibility", Context.MODE_PRIVATE)
    private val probePreferences = application.getSharedPreferences("read_only_getter_probe", Context.MODE_PRIVATE)
    // All profile computations, writes and build-state handoffs share one coordinator.
    private val profileRefreshMutex = ProfileRefreshGate()
    // Only one expensive catalog/DEX operation may own the foreground service.
    private val analysisOperationGate = AnalysisOperationGate()
    val state: StateFlow<ManagerUiState> = mutable
    @Volatile private var deepResolveCancelRequested = false

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
            mutable.value = mutable.value.copy(loading = true, scanProgress = null, message = "Loading installed Android Auto catalog…")
            val aa = withContext(Dispatchers.IO) {
                File(getApplication<Application>().filesDir, "imports").deleteRecursively()
                InstalledAaScanner(getApplication()).scan(::reportScanProgress).also { installation ->
                    installation.buildFingerprintSha256?.let(database::pruneToRecentBuilds)
                }
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
        if (mutable.value.aa.buildFingerprintSha256 == build)
            mutable.value = mutable.value.copy(advancedCandidates = candidates)
    }

    fun clearAdvancedCandidates() {
        if (mutable.value.advancedCandidates.isNotEmpty())
            mutable.value = mutable.value.copy(advancedCandidates = emptyList())
    }

    fun searchCatalog(query: CatalogQuery, append: Boolean = false) = viewModelScope.launch {
        val hash = mutable.value.aa.buildFingerprintSha256 ?: return@launch
        val result = withContext(Dispatchers.IO) { database.search(hash, query) }
        mutable.value = mutable.value.copy(catalog = if (append) (mutable.value.catalog + result.items).distinctBy { it.key } else result.items, catalogTotal = result.total)
    }

    fun loadDetails(key: String) = viewModelScope.launch {
        val hash = mutable.value.aa.buildFingerprintSha256 ?: return@launch
        mutable.value = mutable.value.copy(detailsLoading = true, selectedDetails = null)
        val value = withContext(Dispatchers.IO) { database.details(hash, key) }
        mutable.value = mutable.value.copy(detailsLoading = false, selectedDetails = value)
    }

    fun clearDetails() { mutable.value = mutable.value.copy(selectedDetails = null, detailsLoading = false) }

    fun requestCatalogExport(scope: ExportScope, format: ExportFormat, includeMethods: Boolean, includeDefaults: Boolean, includeOverrides: Boolean) = viewModelScope.launch {
        val hash = mutable.value.aa.buildFingerprintSha256 ?: return@launch
        mutable.value = mutable.value.copy(message = "Preparing export…")
        val overrides = mutable.value.dynamicOverrides
        val payload = withContext(Dispatchers.IO) {
            val baseQuery = when (scope) {
                ExportScope.EDITABLE -> CatalogQuery(editableOnly = true, includeUnknown = true, limit = Int.MAX_VALUE)
                ExportScope.ALL_DISCOVERIES -> CatalogQuery(includeUnknown = true, limit = Int.MAX_VALUE)
                ExportScope.MODIFIED -> CatalogQuery(editableOnly = true, includeUnknown = true, limit = Int.MAX_VALUE)
            }
            var rows = database.search(hash, baseQuery).items
            if (scope == ExportScope.MODIFIED) rows = rows.filter { it.key in overrides }
            if (includeMethods) rows = database.attachDetails(hash, rows)
            val extension = format.name.lowercase()
            val text = when (format) {
                ExportFormat.JSON -> JSONArray().apply { rows.forEach { row -> put(JSONObject().apply {
                    put("key", row.key); put("kind", row.kind.name); put("valueType", row.inferredType.name)
                    put("storageType", row.storageType.name); put("semanticType", row.semanticType.name); put("resolution", row.resolution.name)
                    put("editable", row.editable); put("runtimeConsumer", row.runtimeConsumerStatus.name); put("semanticsReviewed", row.semanticsReviewed)
                    if (includeDefaults) put("compiledDefault", row.compiledDefault ?: JSONObject.NULL)
                    if (includeOverrides) put("override", overrides[row.key]?.encodedValue ?: JSONObject.NULL)
                    if (includeMethods) { put("getters", JSONArray(row.getterCandidates.map { "${it.className}->${it.methodName}${it.descriptor}" })); put("consumers", JSONArray(row.consumers.map { "${it.method.className}->${it.method.methodName}${it.method.descriptor}" })) }
                }) } }.toString(2)
                ExportFormat.CSV -> buildString {
                    val columns = mutableListOf("key","kind","value_type","storage_type","semantic_type","resolution","editable","runtime_consumer","semantics_reviewed")
                    if (includeDefaults) columns += "compiled_default"; if (includeOverrides) columns += "override"; if (includeMethods) columns += listOf("getters","consumers")
                    appendLine(columns.joinToString(","))
                    rows.forEach { row -> val values = mutableListOf(row.key,row.kind.name,row.inferredType.name,row.storageType.name,row.semanticType.name,row.resolution.name,row.editable.toString(),row.runtimeConsumerStatus.name,row.semanticsReviewed.toString())
                        if (includeDefaults) values += row.compiledDefault.orEmpty(); if (includeOverrides) values += overrides[row.key]?.encodedValue.orEmpty()
                        if (includeMethods) { values += row.getterCandidates.joinToString("|") { "${it.className}->${it.methodName}${it.descriptor}" }; values += row.consumers.joinToString("|") { "${it.method.className}->${it.method.methodName}${it.method.descriptor}" } }
                        appendLine(values.joinToString(",") { csv(it) })
                    }
                }
            }
            ExportPayload("aa-catalog-${scope.name.lowercase()}.$extension", if (format == ExportFormat.JSON) "application/json" else "text/csv", text)
        }
        mutable.value = mutable.value.copy(exportPayload = payload, message = "Export ready.")
    }

    fun consumeExport() { mutable.value = mutable.value.copy(exportPayload = null) }

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
                context.contentResolver.openInputStream(uri).use { input -> requireNotNull(input); archive.outputStream().use(input::copyTo) }
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
                database.put(inventory, ::reportScanProgress)
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
        val ok = withContext(Dispatchers.IO) {
            ServiceBridge.dynamicPreferencesOrNull()?.edit()
                ?.putString(io.github.highwindbr.aaxp.runtime.VideoTimingProtocolTrace.KEY,
                    enabled.toString())?.commit() == true
        }
        if (!ok) {
            mutable.value = mutable.value.copy(message = "Cannot update VTC trace: LSPosed remote preferences unavailable.")
            return@launch
        }
        refreshOverridesAndProfile()
        mutable.value = mutable.value.copy(message = if (enabled)
            "VTC read-only trace enabled. Restart Android Auto, then inspect LSPosed module logs."
        else "VTC trace disabled. Existing hooks fail open; restart AA to unload them.")
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
        val result = withContext(Dispatchers.IO) { runCatching {
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
        if (result.isFailure) mutable.value = mutable.value.copy(saveState = SaveState.FAILED, message = "Override rejected: ${result.exceptionOrNull()?.message}")
        else { mutable.value = mutable.value.copy(saveState = SaveState.SAVED); refreshOverridesAndProfile() }
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
        val result = withContext(Dispatchers.IO) { runCatching {
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
        if (result.isFailure) {
            mutable.value = mutable.value.copy(message = "Getter probe rejected: ${result.exceptionOrNull()?.message}")
        } else {
            refreshOverridesAndProfile()
            mutable.value = mutable.value.copy(message =
                if (enabled) "Read-only probe armed. Restart Android Auto to install the hook; observe AAX_EVENT in logcat."
                else "Probe disabled in the profile. Restart Android Auto to remove its installed hook.")
        }
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
        val result = withContext(Dispatchers.IO) { runCatching {
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
        if (result.isFailure) {
            mutable.value = mutable.value.copy(saveState = SaveState.FAILED,
                message = "Experimental override rejected: ${result.exceptionOrNull()?.message}")
        } else {
            refreshOverridesAndProfile()
            mutable.value = mutable.value.copy(saveState = SaveState.SAVED,
                message = if (mutable.value.experimentalProfileReady)
                    "Experimental value saved and profile published. Restart Android Auto to install the new hook; check OVERRIDE_RETURNED."
                else "Experimental value preserved, but the hook profile is not ready. Do not expect the override to apply.")
        }
    }

    fun pauseExperimentalBoolean() = viewModelScope.launch {
        val build = mutable.value.aa.buildFingerprintSha256 ?: return@launch
        val result = withContext(Dispatchers.IO) { runCatching {
            val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull())
            val record = checkNotNull(ExperimentalBooleanSnapshot.decode(
                prefs.getString(ExperimentalBooleanPolicy.RECORD_KEY, null))) { "No experimental snapshot" }
            require(record.buildFingerprint == build) { "The experiment belongs to another build." }
            require(prefs.edit().putString(ExperimentalBooleanPolicy.RECORD_KEY,
                record.copy(active = false).encode()).commit()) { "Unable to pause experiment" }
        } }
        if (result.isSuccess) {
            refreshOverridesAndProfile()
            mutable.value = mutable.value.copy(message =
                "Experimental override paused. Restart Android Auto to guarantee the previous hook no longer applies.")
        } else mutable.value = mutable.value.copy(message = "Pause failed: ${result.exceptionOrNull()?.message}")
    }

    fun restoreExperimentalBaseline() = viewModelScope.launch {
        val build = mutable.value.aa.buildFingerprintSha256 ?: return@launch
        val result = withContext(Dispatchers.IO) { runCatching {
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
        if (result.isSuccess) {
            refreshOverridesAndProfile()
            mutable.value = mutable.value.copy(message =
                "Original override state restored (including its absence). Restart Android Auto to restore the normal hook layout.")
        } else mutable.value = mutable.value.copy(message =
            "Baseline restoration rejected: ${result.exceptionOrNull()?.message}")
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
        val result = withContext(Dispatchers.IO) { runCatching {
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
        if (result.isSuccess) {
            refreshOverridesAndProfile()
            mutable.value = mutable.value.copy(saveState = SaveState.SAVED,
                message = "LONG 3→3 handoff armed; original LONG:3 is intact. Restart Android Auto and check ADVANCED_OVERRIDE_RETURNED.")
        } else mutable.value = mutable.value.copy(saveState = SaveState.FAILED,
            message = "LONG handoff rejected: ${result.exceptionOrNull()?.message}")
    }

    fun setAdvancedOverride(identifier: DiscoveredIdentifier, raw: String) = viewModelScope.launch {
        val aa = mutable.value.aa
        val build = aa.buildFingerprintSha256
        if (!aa.runtimeTarget || build == null || !mutable.value.framework.connected) {
            mutable.value = mutable.value.copy(message = "Advanced overrides require the installed AA and LSPosed service.")
            return@launch
        }
        mutable.value = mutable.value.copy(saveState = SaveState.SAVING, saveTargetKey = identifier.key)
        val result = withContext(Dispatchers.IO) { runCatching {
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
        if (result.isSuccess) {
            refreshOverridesAndProfile()
            mutable.value = mutable.value.copy(saveState = SaveState.SAVED,
                message = "Advanced value saved. Restart Android Auto and inspect the LSPosed events.")
        } else mutable.value = mutable.value.copy(saveState = SaveState.FAILED,
            message = "Advanced override rejected: ${result.exceptionOrNull()?.message}")
    }

    fun pauseAdvancedOverride(key: String) = setAdvancedActivity(key, false)
    fun resumeAdvancedOverride(key: String) = setAdvancedActivity(key, true)

    private fun setAdvancedActivity(key: String, enabled: Boolean) = viewModelScope.launch {
        val build = mutable.value.aa.buildFingerprintSha256 ?: return@launch
        val result = withContext(Dispatchers.IO) { runCatching {
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
        if (result.isSuccess) {
            refreshOverridesAndProfile()
            mutable.value = mutable.value.copy(message =
                "Advanced override ${if (enabled) "resumed" else "paused"}; restart AA.")
        } else mutable.value = mutable.value.copy(message = "Advanced change failed: ${result.exceptionOrNull()?.message}")
    }

    fun restoreAdvancedBaseline(key: String) = viewModelScope.launch {
        val build = mutable.value.aa.buildFingerprintSha256 ?: return@launch
        val result = withContext(Dispatchers.IO) { runCatching {
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
        if (result.isSuccess) {
            refreshOverridesAndProfile()
            mutable.value = mutable.value.copy(message = "Advanced baseline restored, including previous absence. Restart AA.")
        } else mutable.value = mutable.value.copy(message = "Restore failed: ${result.exceptionOrNull()?.message}")
    }

    fun pauseAllAdvanced() = viewModelScope.launch {
        val build = mutable.value.aa.buildFingerprintSha256 ?: return@launch
        val result = withContext(Dispatchers.IO) { runCatching {
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
        if (result.isSuccess) {
            refreshOverridesAndProfile()
            mutable.value = mutable.value.copy(message = "All current-build Advanced overrides paused; restart AA.")
        } else mutable.value = mutable.value.copy(message = "Pause all rejected: ${result.exceptionOrNull()?.message}")
    }

    fun restoreAllAdvanced() = viewModelScope.launch {
        val build = mutable.value.aa.buildFingerprintSha256 ?: return@launch
        val result = withContext(Dispatchers.IO) { runCatching {
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
        if (result.isSuccess) {
            refreshOverridesAndProfile()
            mutable.value = mutable.value.copy(message =
                "${result.getOrNull()} current-build Advanced baselines restored; restart Android Auto.")
        } else mutable.value = mutable.value.copy(message =
            "Bulk restore rejected: ${result.exceptionOrNull()?.message}")
    }

    fun discardSuspendedOverride(preferenceKey: String) = viewModelScope.launch {
        if (!mutable.value.aa.runtimeTarget || !preferenceKey.startsWith(DynamicOverrideCodec.PREFIX)) return@launch
        val result = withContext(Dispatchers.IO) { runCatching {
            val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull()) { "Xposed service is not connected" }
            val editor = prefs.edit().remove(preferenceKey)
            DynamicOverrideCodec.nameKeyForPreference(preferenceKey)?.let(editor::remove)
            require(editor.commit()) { "Framework rejected override removal" }
        } }
        if (result.isSuccess) refreshOverridesAndProfile()
        else mutable.value = mutable.value.copy(message = "Discard failed: ${result.exceptionOrNull()?.message}")
    }

    fun restoreAllDynamic() = viewModelScope.launch {
        if (!mutable.value.aa.runtimeTarget) return@launch
        mutable.value = mutable.value.copy(saveState = SaveState.SAVING, saveTargetKey = null)
        val ok = withContext(Dispatchers.IO) {
            val prefs = ServiceBridge.dynamicPreferencesOrNull() ?: return@withContext false
            val editor = prefs.edit().remove(ExperimentalBooleanPolicy.RECORD_KEY)
            prefs.all.keys.filter { it.startsWith(DynamicOverrideCodec.PREFIX) ||
                it.startsWith(DynamicOverrideCodec.NAME_PREFIX) ||
                it.startsWith(AdvancedOverridePolicy.PREFIX) }.forEach(editor::remove)
            editor.commit()
        }
        mutable.value = mutable.value.copy(saveState = if (ok) SaveState.SAVED else SaveState.FAILED)
        refreshOverridesAndProfile()
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

    fun importDynamicJson(text: String) = viewModelScope.launch {
        if (!mutable.value.aa.runtimeTarget) { mutable.value = mutable.value.copy(message = "Imported archives are read-only."); return@launch }
        mutable.value = mutable.value.copy(saveState = SaveState.SAVING)
        val result = withContext(Dispatchers.IO) { runCatching {
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
        result.onSuccess { (accepted,rejected) -> mutable.value=mutable.value.copy(saveState=SaveState.SAVED,message="Imported $accepted override(s); rejected $rejected."); refreshOverridesAndProfile() }
            .onFailure { mutable.value=mutable.value.copy(saveState=SaveState.FAILED,message="Import failed: ${it.message}") }
    }

    private suspend fun refreshOverridesAndProfile() = profileRefreshMutex.withLock {
        val aa = mutable.value.aa
        if (!aa.runtimeTarget || aa.baseSha256 == null || aa.buildFingerprintSha256 == null) return@withLock
        val result = withContext(Dispatchers.IO) { runCatching {
            // A transient LSPosed disconnection must not publish an empty profile.
            // The last selected verified slot remains active until reconnection.
            val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull()) {
                "Remote preferences unavailable; preserving last published profile"
            }
            val allPrefs = prefs.all
            val protocolTraceOn =
                io.github.highwindbr.aaxp.runtime.VideoTimingProtocolTrace.isSupported(aa.baseSha256) &&
                io.github.highwindbr.aaxp.runtime.VideoTimingProtocolTrace.enabled(
                    allPrefs[io.github.highwindbr.aaxp.runtime.VideoTimingProtocolTrace.KEY] as? String)
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
                experimentalMappings, advancedMappings, protocolTraceOn)
            OverrideRefreshOutcome(active, suspended, published.isSuccess, published.exceptionOrNull(),
                experimental, published.isSuccess && experimentalMappings.isNotEmpty(),
                advancedRecords, if (published.isSuccess) advancedMappings.size else 0,
                protocolTraceOn)
        } }
        // Build state is handed off under the same mutex: stale queued refreshes
        // cannot overwrite an imported archive or a newly installed AA build.
        if (mutable.value.aa.buildFingerprintSha256 != aa.buildFingerprintSha256 ||
            !mutable.value.aa.runtimeTarget) return@withLock
        result.onSuccess { outcome -> mutable.value=mutable.value.copy(dynamicOverrides=outcome.active,
            suspendedOverrides=outcome.suspended, probeTargetKey=probeKeyForBuild(aa.buildFingerprintSha256),
            experimentalSnapshot=outcome.experimental, experimentalProfileReady=outcome.experimentalReady,
            advancedRecords=outcome.advanced, advancedPublishedCount=outcome.advancedPublishedCount,
            videoTimingTraceEnabled=outcome.videoTimingTraceEnabled,
            profilePublished=outcome.published,
            message=outcome.error?.let { "Dynamic profile was not published: ${it.message}" }) }
            .onFailure { mutable.value=mutable.value.copy(profilePublished=false,message="Dynamic profile was not published: ${it.message}") }
    }

    private fun csv(value: String) = "\"${value.replace("\"", "\"\"")}\""

    private fun keepAnalysisAlive(active: Boolean) {
        val context = getApplication<Application>()
        val intent = Intent(context, AnalysisKeepAliveService::class.java)
        if (active) ContextCompat.startForegroundService(context, intent) else context.stopService(intent)
    }
}
