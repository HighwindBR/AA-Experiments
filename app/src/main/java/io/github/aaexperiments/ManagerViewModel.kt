package io.github.aaexperiments

import android.app.Application
import android.net.Uri
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.aaexperiments.core.*
import io.github.aaexperiments.discovery.*
import io.github.aaexperiments.resolver.DynamicProfile
import io.github.aaexperiments.scanner.ArchiveInputResolver
import io.github.aaexperiments.scanner.InstalledAaScanner
import io.github.aaexperiments.scanner.ManifestCatalog
import io.github.aaexperiments.service.ServiceBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
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
    val changes: CatalogChangeSummary? = null,
    val resolverRun: ResolverRunSummary? = null,
    val deepResolvePending: Int = 0,
    val deepResolveRunning: Boolean = false,
    val scanProgress: AnalysisProgress? = null,
    val saveState: SaveState = SaveState.IDLE,
    val message: String? = null
)

enum class ExportScope { EDITABLE, MODIFIED, ALL_DISCOVERIES }
enum class ExportFormat { JSON, CSV }
data class ExportPayload(val fileName: String, val mimeType: String, val content: String)
private data class DeepResolveOutcome(val saved: Int, val resolved: Int, val inconclusive: Int, val cancelled: Boolean)
private data class OverrideRefreshOutcome(val active: Map<String, TypedOverride>, val suspended: List<SuspendedOverride>, val published: Boolean, val error: Throwable?)

class ManagerViewModel(application: Application) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(ManagerUiState())
    private val database = CatalogDatabase(application)
    val state: StateFlow<ManagerUiState> = mutable
    @Volatile private var deepResolveCancelRequested = false

    init {
        viewModelScope.launch {
            ServiceBridge.state.collectLatest { framework ->
                mutable.value = mutable.value.copy(framework = framework)
                refreshOverridesAndProfile()
            }
        }
        refreshInstallation()
    }

    fun refreshInstallation() = viewModelScope.launch {
        keepAnalysisAlive(true)
        try {
            mutable.value = mutable.value.copy(loading = true, scanProgress = null, message = "Loading installed Android Auto catalog…")
            val aa = withContext(Dispatchers.IO) {
                File(getApplication<Application>().filesDir, "imports").deleteRecursively()
                InstalledAaScanner(getApplication()).scan(::reportScanProgress).also { installation ->
                    installation.buildFingerprintSha256?.let(database::pruneToRecentBuilds)
                }
            }
            if (mutable.value.scanProgress != null) reportScanProgress(AnalysisProgress(AnalysisStage.LOADING_EXPERIMENTS, detail = "Loading editable experiments"))
            val page = withContext(Dispatchers.IO) { aa.buildFingerprintSha256?.let { database.editablePage(it, Int.MAX_VALUE, 0) }.orEmpty() }
            val changes = withContext(Dispatchers.IO) { aa.buildFingerprintSha256?.let { current -> database.previousSummary(current)?.let { database.editableChanges(current, it.baseSha256) } } }
            val resolverRun = withContext(Dispatchers.IO) { aa.buildFingerprintSha256?.let(database::resolverRunSummary) }
            val deepPending = withContext(Dispatchers.IO) { aa.buildFingerprintSha256?.let(database::actionableDeepResolveCount) ?: 0 }
            mutable.value = mutable.value.copy(loading = false, scanProgress = null, aa = aa, discovered = page, loadedEditableCount = page.size, changes = changes, resolverRun = resolverRun, deepResolvePending = deepPending, message = null)
            refreshOverridesAndProfile()
        } finally {
            keepAnalysisAlive(false)
        }
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
        keepAnalysisAlive(true)
        try {
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
            val resolverRun = withContext(Dispatchers.IO) { database.resolverRunSummary(checkNotNull(aa.buildFingerprintSha256)) }
            mutable.value = mutable.value.copy(loading = false, scanProgress = null, aa = aa, discovered = page, loadedEditableCount = page.size,
                dynamicOverrides = emptyMap(), profilePublished = false, changes = null, resolverRun = resolverRun,
                message = "Imported ${aa.identifierCount} identifiers for offline review; the installed-AA runtime profile was not replaced.")
            }.onFailure { error -> mutable.value = mutable.value.copy(loading = false, scanProgress = null, message = "Import failed: ${error.message}") }
        } finally {
            keepAnalysisAlive(false)
        }
    }

    private fun reportScanProgress(progress: AnalysisProgress) {
        mutable.value = mutable.value.copy(scanProgress = progress)
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
        deepResolveCancelRequested = false
        keepAnalysisAlive(true)
        try {
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
        } finally {
            deepResolveCancelRequested = false
            keepAnalysisAlive(false)
        }
    }

    fun setDynamicOverride(identifier: DiscoveredIdentifier, rawInput: String?) = viewModelScope.launch {
        mutable.value = mutable.value.copy(saveState = SaveState.SAVING, message = null)
        val result = withContext(Dispatchers.IO) { runCatching {
            require(mutable.value.aa.runtimeTarget) { "Imported archives are read-only; refresh the installed Android Auto before editing." }
            require(identifier.editable) { identifier.reason ?: "Identifier is read-only" }
            val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull()) { "Xposed service is not connected" }
            val editor = prefs.edit(); val key = DynamicOverrideCodec.preferenceKey(identifier.key)
            if (rawInput == null) editor.remove(key) else editor.putString(key, DynamicOverrideCodec.serialize(DynamicOverrideCodec.validate(identifier.inferredType, rawInput)))
            require(editor.commit()) { "Framework rejected the preference update" }
        } }
        if (result.isFailure) mutable.value = mutable.value.copy(saveState = SaveState.FAILED, message = "Override rejected: ${result.exceptionOrNull()?.message}")
        else { mutable.value = mutable.value.copy(saveState = SaveState.SAVED); refreshOverridesAndProfile() }
    }

    fun restoreAllDynamic() = viewModelScope.launch {
        if (!mutable.value.aa.runtimeTarget) return@launch
        mutable.value = mutable.value.copy(saveState = SaveState.SAVING)
        val ok = withContext(Dispatchers.IO) {
            val prefs = ServiceBridge.dynamicPreferencesOrNull() ?: return@withContext false
            val editor = prefs.edit(); prefs.all.keys.filter { it.startsWith(DynamicOverrideCodec.PREFIX) }.forEach(editor::remove); editor.commit()
        }
        mutable.value = mutable.value.copy(saveState = if (ok) SaveState.SAVED else SaveState.FAILED)
        refreshOverridesAndProfile()
    }

    fun exportDynamicJson(): String = JSONObject().apply {
        put("schemaVersion", 3); put("package", InstalledAaScanner.AA_PACKAGE)
        put("versionCode", mutable.value.aa.versionCode ?: JSONObject.NULL); put("buildFingerprintSha256", mutable.value.aa.buildFingerprintSha256 ?: JSONObject.NULL)
        put("overrides", JSONObject().apply { mutable.value.dynamicOverrides.forEach { (key, value) -> put(key, DynamicOverrideCodec.serialize(value)) } })
    }.toString(2)

    fun importDynamicJson(text: String) = viewModelScope.launch {
        if (!mutable.value.aa.runtimeTarget) { mutable.value = mutable.value.copy(message = "Imported archives are read-only."); return@launch }
        mutable.value = mutable.value.copy(saveState = SaveState.SAVING)
        val result = withContext(Dispatchers.IO) { runCatching {
            val prefs = checkNotNull(ServiceBridge.dynamicPreferencesOrNull()) { "Xposed service is not connected" }
            val root = JSONObject(text); val schema = root.getInt("schemaVersion"); require(schema in 2..3) { "Unsupported schema" }
            val sameBuild = if (schema >= 3) root.optString("buildFingerprintSha256").equals(mutable.value.aa.buildFingerprintSha256, true)
                else root.optString("baseSha256").equals(mutable.value.aa.baseSha256, true)
            require(sameBuild) { "Backup belongs to another Android Auto build; nothing was applied." }
            val values = root.getJSONObject("overrides"); val keys = buildSet { values.keys().forEach(::add) }
            val byKey = database.identifiersByKeys(checkNotNull(mutable.value.aa.buildFingerprintSha256), keys)
            val accepted = mutableMapOf<String, TypedOverride>(); val rejected = mutableSetOf<String>()
            keys.forEach { key -> val identifier=byKey[key]; val value=DynamicOverrideCodec.deserialize(values.getString(key)); if (identifier!=null && value!=null && value.type==identifier.inferredType) accepted[key]=value else rejected+=key }
            val editor=prefs.edit(); accepted.forEach { (key,value) -> editor.putString(DynamicOverrideCodec.preferenceKey(key),DynamicOverrideCodec.serialize(value)) }; require(editor.commit())
            accepted.size to rejected.size
        } }
        result.onSuccess { (accepted,rejected) -> mutable.value=mutable.value.copy(saveState=SaveState.SAVED,message="Imported $accepted override(s); rejected $rejected."); refreshOverridesAndProfile() }
            .onFailure { mutable.value=mutable.value.copy(saveState=SaveState.FAILED,message="Import failed: ${it.message}") }
    }

    private suspend fun refreshOverridesAndProfile() {
        val aa = mutable.value.aa
        if (!aa.runtimeTarget || aa.baseSha256 == null || aa.buildFingerprintSha256 == null) return
        val result = withContext(Dispatchers.IO) { runCatching {
            val snapshot = ServiceBridge.preferenceSnapshot()
            val resolved = database.identifiersByPreferenceKeys(aa.buildFingerprintSha256, snapshot.keys).toMap()
            val active = mutableMapOf<String, TypedOverride>(); val suspended = mutableListOf<SuspendedOverride>(); val mappings = mutableListOf<DynamicProfile.Mapping>()
            snapshot.forEach { (preferenceKey, raw) ->
                val identifier = resolved[preferenceKey]
                val value = DynamicOverrideCodec.deserialize(raw)
                val reason = suspendedOverrideReason(identifier, value)
                if (reason != null) suspended += SuspendedOverride(preferenceKey, identifier?.key, value?.type?.name, identifier?.inferredType?.name, reason)
                else {
                    val safeIdentifier = checkNotNull(identifier); val safeValue = checkNotNull(value); val method = safeIdentifier.getterCandidates.single()
                    active[safeIdentifier.key] = safeValue
                    mappings += DynamicProfile.Mapping(safeIdentifier.key, preferenceKey, method.className.removePrefix("L").removeSuffix(";").replace('/','.'), method.methodName, method.descriptor, safeIdentifier.inferredType.name)
                }
            }
            val published = ServiceBridge.publishProfile(aa.buildFingerprintSha256, aa.baseSha256, aa.versionCode ?: 0, aa.packageArtifactSha256, aa.dexSha256, mappings)
            OverrideRefreshOutcome(active, suspended, published.isSuccess, published.exceptionOrNull())
        } }
        result.onSuccess { outcome -> mutable.value=mutable.value.copy(dynamicOverrides=outcome.active,suspendedOverrides=outcome.suspended,profilePublished=outcome.published,message=outcome.error?.let { "Dynamic profile was not published: ${it.message}" }) }
            .onFailure { mutable.value=mutable.value.copy(profilePublished=false,message="Dynamic profile was not published: ${it.message}") }
    }

    private fun csv(value: String) = "\"${value.replace("\"", "\"\"")}\""

    private fun keepAnalysisAlive(active: Boolean) {
        val context = getApplication<Application>()
        val intent = Intent(context, AnalysisKeepAliveService::class.java)
        if (active) ContextCompat.startForegroundService(context, intent) else context.stopService(intent)
    }
}
