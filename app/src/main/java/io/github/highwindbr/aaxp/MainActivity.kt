@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.highwindbr.aaxp

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.animation.AnimatedContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import io.github.highwindbr.aaxp.core.*
import io.github.highwindbr.aaxp.discovery.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private val model by viewModels<ManagerViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { ManagerRoot(model) } }
}

private enum class ThemeMode { SYSTEM, LIGHT, DARK }
private enum class AccentPalette { LAB_GREEN, MATERIAL_YOU }
private enum class ExperimentFilter { ALL, MODIFIED, SUSPENDED, ADDED, REMOVED, CHANGED }
private val AaGreen = Color(0xFF006D3F)
private val AaGreenDark = Color(0xFF72D99A)

private val AaLightScheme = lightColorScheme(
    primary = AaGreen, onPrimary = Color.White, primaryContainer = Color(0xFFA0F4BA), onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF4E6354), tertiary = Color(0xFF3A6470), background = Color(0xFFF7FBF5),
    surface = Color(0xFFF7FBF5), surfaceVariant = Color(0xFFDDE5DC), error = Color(0xFFBA1A1A)
)
private val AaDarkScheme = darkColorScheme(
    primary = AaGreenDark, onPrimary = Color(0xFF00391D), primaryContainer = Color(0xFF00522D), onPrimaryContainer = Color(0xFF8DD8A8),
    secondary = Color(0xFFB5CCBA), tertiary = Color(0xFFA1CEDA), background = Color(0xFF101510),
    surface = Color(0xFF101510), surfaceVariant = Color(0xFF414941), error = Color(0xFFFFB4AB)
)
@Composable private fun ManagerRoot(model: ManagerViewModel) {
    val context = LocalContext.current; val prefs = remember { context.getSharedPreferences("manager_ui", 0) }
    var theme by remember { mutableStateOf(runCatching { ThemeMode.valueOf(prefs.getString("theme_mode", prefs.getString("theme", "SYSTEM"))!!) }.getOrDefault(ThemeMode.SYSTEM)) }
    val storedPalette = prefs.getString("accent_palette", prefs.getString("colors", "BRAND"))
    var accent by remember { mutableStateOf(when (storedPalette) { "SYSTEM", "System colors", "Material You", "MATERIAL_YOU" -> AccentPalette.MATERIAL_YOU; else -> AccentPalette.LAB_GREEN }) }
    val dark = theme == ThemeMode.DARK || (theme == ThemeMode.SYSTEM && isSystemInDarkTheme())
    val colors = if (accent == AccentPalette.MATERIAL_YOU && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) AaDarkScheme else AaLightScheme
    val view = LocalView.current
    SideEffect {
        val window = (context as? MainActivity)?.window ?: return@SideEffect
        window.statusBarColor = colors.background.toArgb()
        window.navigationBarColor = colors.surface.toArgb()
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = colors) { ManagerApp(model, theme, { theme = it; prefs.edit().putString("theme_mode", it.name).apply() }, accent) { accent = it; prefs.edit().putString("accent_palette", it.name).apply() } }
}

@Composable private fun ManagerApp(model: ManagerViewModel, theme: ThemeMode, onTheme: (ThemeMode) -> Unit, accent: AccentPalette, onAccent: (AccentPalette) -> Unit) {
    val state by model.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var experimentKey by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsPageName by rememberSaveable { mutableStateOf(SettingsPage.ROOT.name) }
    val settingsPage = runCatching { SettingsPage.valueOf(settingsPageName) }.getOrDefault(SettingsPage.ROOT)
    val uiContext = LocalContext.current
    val managerPrefs = remember { uiContext.getSharedPreferences("manager_ui", 0) }
    var advancedAccess by rememberSaveable {
        mutableStateOf(managerPrefs.getBoolean("advanced_editing_opt_in", false))
    }
    var advancedOptInWarning by remember { mutableStateOf(false) }
    fun setAdvancedAccess(enabled: Boolean) {
        advancedAccess = enabled
        managerPrefs.edit().putBoolean("advanced_editing_opt_in", enabled).apply()
    }
    LaunchedEffect(advancedAccess, state.aa.buildFingerprintSha256, state.aa.runtimeTarget) {
        if (advancedAccess && state.aa.runtimeTarget) model.loadAdvancedCandidates()
        else model.clearAdvancedCandidates()
    }
    var experimentQuery by rememberSaveable { mutableStateOf("") }
    var experimentFilterName by rememberSaveable { mutableStateOf(ExperimentFilter.ALL.name) }
    val experimentFilter = runCatching { ExperimentFilter.valueOf(experimentFilterName) }.getOrDefault(ExperimentFilter.ALL)
    var experimentReviewedOnly by rememberSaveable { mutableStateOf(false) }
    var experimentTypeName by rememberSaveable { mutableStateOf<String?>(null) }
    val experimentListState = rememberLazyListState()
    val experiment = experimentKey?.let { key ->
        state.discovered.firstOrNull { it.key == key } ?:
            state.advancedCandidates.firstOrNull { it.key == key }
    }
    val context = LocalContext.current
    experiment?.let { identifier ->
        BackHandler { model.clearDetails(); experimentKey = null }
        ExperimentDetailScreen(identifier, state.selectedDetails,
            state.dynamicOverrides[identifier.key], state.framework.connected && state.aa.runtimeTarget,
            state.saveState, state.saveTargetKey, state.message,
            { value -> if (identifier.editable) model.setDynamicOverride(identifier, value) },
            { model.loadDetails(identifier.key) },
            { model.clearDetails(); experimentKey = null },
            isAdvanced = !identifier.editable,
            advancedRecord = state.advancedRecords.firstOrNull { it.key == identifier.key },
            advancedBuild = state.aa.buildFingerprintSha256,
            advancedPublishedCount = state.advancedPublishedCount,
            onAdvancedSave = model::setAdvancedOverride,
            onAdvancedPause = model::pauseAdvancedOverride,
            onAdvancedResume = model::resumeAdvancedOverride,
            onAdvancedRestore = model::restoreAdvancedBaseline)
        return
    }
    val rootBackAction = backAction(tab, settingsPage, false)
    BackHandler(enabled = rootBackAction != BackAction.EXIT) {
        when (rootBackAction) {
            BackAction.OPEN_DIAGNOSTICS -> settingsPageName = SettingsPage.DIAGNOSTICS.name
            BackAction.OPEN_SETTINGS -> settingsPageName = SettingsPage.ROOT.name
            BackAction.OPEN_STATUS -> tab = 0
            else -> Unit
        }
    }
    val destinations = listOf(Triple("Status", Icons.Default.Home, 0), Triple("Experiments", Icons.Default.Science, 1), Triple("Settings", Icons.Default.Settings, 2))
    val feedbackHost = remember { SnackbarHostState() }
    // Initialize with the existing message so returning from Details does not
    // replay an earlier Advanced save/restore notification.
    var lastFeedback by remember { mutableStateOf(state.message) }
    LaunchedEffect(state.message) {
        val current = state.message
        if (current != lastFeedback) {
            lastFeedback = current
            if (current != null &&
                !current.startsWith("Loading", ignoreCase = true) &&
                !current.startsWith("Preparing", ignoreCase = true) &&
                !current.startsWith("Importing and analyzing", ignoreCase = true) &&
                !current.startsWith("Deep resolving", ignoreCase = true)
            ) feedbackHost.showSnackbar(current.take(220), withDismissAction = true)
        }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Row(verticalAlignment = Alignment.CenterVertically) { Image(painterResource(R.drawable.app_icon), null, Modifier.size(32.dp), contentScale = ContentScale.Fit); Spacer(Modifier.width(10.dp)); Text("AA Experiments", fontWeight = FontWeight.SemiBold) } }, actions = { if (tab == 0) IconButton(model::refreshInstallation) { Icon(Icons.Default.Refresh, "Refresh status") } }) },
        bottomBar = { NavigationBar { destinations.forEach { (label, icon, index) -> NavigationBarItem(tab == index, { tab = index }, { Icon(icon, label) }, label = { Text(label) }) } } },
        snackbarHost = { SnackbarHost(feedbackHost) }
    ) { padding -> Box(Modifier.padding(padding).fillMaxSize()) { when (tab) {
        0 -> StatusScreen(state, model) { chosen -> experimentFilterName = chosen.name; tab = 1 }
        1 -> AdvancedScreen(state, model, experimentQuery, { experimentQuery = it }, experimentFilter, { experimentFilterName = it.name }, experimentReviewedOnly, { experimentReviewedOnly = it }, experimentTypeName, { experimentTypeName = it }, experimentListState, advancedAccess) { experimentKey = it.key }
        else -> SettingsScreen(state, model, theme, onTheme, accent, onAccent, settingsPage,
            { settingsPageName = it.name }, advancedAccess,
            { wantEnabled -> if (wantEnabled) advancedOptInWarning = true else setAdvancedAccess(false) })
    } } }
    if (advancedOptInWarning) AlertDialog(
        onDismissRequest = { advancedOptInWarning = false },
        title = { Text("Enable Advanced Overrides?") },
        text = { Text("Eligible Advanced flags will appear in Experiments with an ADVANCED label. Only structurally verified getters passing the safety policy are listed. Experimental values can crash Android Auto and each write still needs confirmation. Hiding Advanced later will NOT pause or remove existing overrides.") },
        confirmButton = { TextButton({
            advancedOptInWarning = false; setAdvancedAccess(true)
        }) { Text("Enable Advanced") } },
        dismissButton = { TextButton({ advancedOptInWarning = false }) { Text("Cancel") } }
    )
    state.scanProgress?.let { progress -> ScanProgressDialog(progress, if (state.deepResolveRunning) ({ model.deepResolveInconclusive() }) else null) }
}

@Composable private fun StatusScreen(state: ManagerUiState, model: ManagerViewModel, onViewDelta: (ExperimentFilter) -> Unit) {
    var auditOpen by remember { mutableStateOf(false) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::importArchive) }
    val activeAdvanced = state.advancedRecords.count {
        it.active && it.buildFingerprint == state.aa.buildFingerprintSha256
    }
    val oldExperimental = if (state.experimentalSnapshot?.active == true &&
        state.experimentalSnapshot.buildFingerprint == state.aa.buildFingerprintSha256) 1 else 0
    val runtime = runtimeReadiness(state.aa, state.framework,
        state.dynamicOverrides.size + activeAdvanced + oldExperimental, state.profilePublished)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item { ScreenTitle("Status", "Environment, resolver and runtime readiness") }
    item { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) { Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { StatusDot(state.aa.installed); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("Android Auto", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(if (state.aa.installed) "Installed and ready" else state.aa.scanError ?: "Not found", color = if (state.aa.installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }; Image(painterResource(R.drawable.app_icon), null, Modifier.size(42.dp)) }
        Text(if (state.aa.installed) "${state.aa.versionName} (${state.aa.versionCode})" else "No compatible installation was detected", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStatus(Modifier.weight(1f), Icons.Default.Extension, "LSPosed", if (state.framework.connected) "API ${state.framework.api}" else "Disconnected", state.framework.connected)
            MiniStatus(Modifier.weight(1f), Icons.Default.Security, "Scope", if (state.framework.scope.any { it == "com.google.android.projection.gearhead" }) "Configured" else "Check scope", state.framework.scope.any { it == "com.google.android.projection.gearhead" })
        }
    } } }
    item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Resolver & runtime", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        CompactStatusRow(Icons.Default.Storage, "Base APK", state.aa.baseSha256?.let { it.take(16) + "…" } ?: "Not available")
        CompactStatusRow(Icons.Default.Search, "Inventory", if (state.aa.runtimeTarget) "Installed Android Auto" else "Imported archive")
        RuntimeBanner(runtime)
        Text("Identifiers: ${state.aa.editableCount} editable · ${state.dynamicOverrides.size} modified", style = MaterialTheme.typography.bodySmall)
        state.probeTargetKey?.let { key ->
            Text("Read-only getter probe armed: ${technicalDisplay(key)} (restart AA to install)",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        state.experimentalSnapshot?.let { record ->
            val matchesBuild = record.buildFingerprint == state.aa.buildFingerprintSha256
            Text(when {
                !matchesBuild -> "Experimental BOOLEAN override suspended: previous Android Auto build."
                !record.active -> "Experimental BOOLEAN override paused (baseline retained)."
                state.experimentalProfileReady -> "Experimental BOOLEAN override published; restart Android Auto to apply."
                else -> "Experimental BOOLEAN override saved but its hook is not published."
            }, style = MaterialTheme.typography.labelSmall,
                color = if (matchesBuild && record.active && state.experimentalProfileReady)
                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        }
        if (state.advancedRecords.isNotEmpty()) {
            val count = state.advancedRecords.count {
                it.active && it.buildFingerprint == state.aa.buildFingerprintSha256
            }
            Text("Advanced Overrides: $count active · ${state.advancedRecords.size} retained · ${state.advancedPublishedCount} published",
                style = MaterialTheme.typography.bodySmall,
                color = if (count > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton({ model.pauseAllAdvanced() }, enabled = count > 0) {
                Icon(Icons.Default.Security, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Emergency: pause all Advanced")
            }
            Text("Available even when the hidden editor is locked. Reboot Android Auto to remove installed hooks.",
                style = MaterialTheme.typography.labelSmall)
        }
        state.changes?.let { change ->
            Text("Editable flags since the previous catalog:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(Triple(ExperimentFilter.ADDED, "Added", change.added),
                    Triple(ExperimentFilter.REMOVED, "Removed", change.removed),
                    Triple(ExperimentFilter.CHANGED, "Changed", change.changed)
                ).forEach { (category, label, count) ->
                    TextButton(onClick = { onViewDelta(category) }, enabled = count > 0) {
                        Text("$count $label")
                        if (count > 0) Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp))
                    }
                }
            }
        }
    } } }
    item { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("Analysis", fontWeight = FontWeight.Bold); Text(if (state.profilePublished) "Dynamic profile published" else "Profile not published"); Text("Overrides apply only when the complete installed build fingerprint matches.", style = MaterialTheme.typography.bodySmall); if (state.suspendedOverrides.isNotEmpty()) TextButton({ auditOpen = true }) { Icon(Icons.Default.Security, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("${state.suspendedOverrides.size} suspended overrides · View details", color = MaterialTheme.colorScheme.error) } } } }
    item { OutlinedButton({ importer.launch(arrayOf("application/vnd.android.package-archive", "application/zip", "application/octet-stream")) }, Modifier.fillMaxWidth()) { Text("Import APK, APKM or APKS") } }
    }
    if (auditOpen) SuspendedAuditListDialog(
        state.suspendedOverrides, state.hiddenSuspendedKeys, { auditOpen = false },
        model::discardSuspendedOverride, model::hideSuspendedFromAll
    )
}

@Composable private fun ScanProgressDialog(progress: AnalysisProgress, onCancel: (() -> Unit)? = null) {
    val title = when (progress.stage) {
        AnalysisStage.PREPARING_ARCHIVE -> "Preparing archive"
        AnalysisStage.READING_DEX -> "Reading DEX files"
        AnalysisStage.RESOLVING_IDENTIFIERS -> "Resolving identifiers"
        AnalysisStage.REDISCOVERING_MAPPINGS -> "Comparing with previous build"
        AnalysisStage.DEEP_RESOLVING -> "Deep resolving inconclusive mappings"
        AnalysisStage.CLASSIFYING_EXPERIMENTS -> "Classifying experiments"
        AnalysisStage.READING_RESOURCES -> "Reading resources"
        AnalysisStage.SAVING_CATALOG -> "Saving catalog"
        AnalysisStage.LOADING_EXPERIMENTS -> "Loading experiments"
    }
    Dialog(onDismissRequest = {}) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Analyzing Android Auto", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                progress.fraction?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                    ?: LinearProgressIndicator(Modifier.fillMaxWidth())
                // Reserve exactly three text lines: short and long identifiers cannot resize this dialog.
                Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.TopStart) {
                    Text(technicalDisplay(progress.detail.orEmpty()), maxLines = 3, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.CenterStart) {
                    if (progress.current != null && progress.total != null)
                        Text("${progress.current} of ${progress.total}", style = MaterialTheme.typography.labelMedium)
                }
                Text("Keep AA Experiments open while the catalog is being prepared.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.fillMaxWidth().height(42.dp), contentAlignment = Alignment.CenterEnd) {
                    onCancel?.let { TextButton(it) { Text("Cancel after current") } }
                }
            }
        }
    }
}

@Composable private fun ScreenTitle(title: String, subtitle: String? = null) { Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold); subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
@Composable private fun MiniStatus(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, ok: Boolean) { Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(22.dp), tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error); Spacer(Modifier.width(8.dp)); Column { Text(title, style = MaterialTheme.typography.labelMedium); Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium) } } } }
@Composable private fun RuntimeBanner(runtime: RuntimeReadiness) {
    val concise = when (runtime.kind) {
        RuntimeReadinessKind.READY_AWAITING_PROCESS -> "Ready — waiting for Android Auto"
        RuntimeReadinessKind.NO_OVERRIDES -> "Idle — no active overrides"
        else -> runtime.label
    }
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.PlayCircle, null, tint = if (runtime.warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary); Spacer(Modifier.width(10.dp)); Column { Text("Runtime", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(concise, fontWeight = FontWeight.SemiBold); Text("Details in Settings → Diagnostics", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
}

@Composable private fun StatusDot(ok: Boolean) = Surface(Modifier.size(12.dp), shape = MaterialTheme.shapes.extraLarge, color = if (ok) AaGreen else MaterialTheme.colorScheme.error) {}
@Composable private fun CompactStatusRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, tint: Color = MaterialTheme.colorScheme.primary) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(12.dp)); Text(title, Modifier.weight(1f), fontWeight = FontWeight.Medium); Text(value, style = MaterialTheme.typography.bodySmall) }
}

@Composable private fun StatusCard(title: String, value: String) = Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(title, fontWeight = FontWeight.Bold); Text(value) } }

@Composable private fun AdvancedScreen(
    state: ManagerUiState,
    model: ManagerViewModel,
    query: String,
    onQuery: (String) -> Unit,
    filter: ExperimentFilter,
    onFilter: (ExperimentFilter) -> Unit,
    reviewedOnly: Boolean,
    onReviewedOnly: (Boolean) -> Unit,
    typeName: String?,
    onTypeName: (String?) -> Unit,
    listState: LazyListState,
    advancedAccess: Boolean,
    onOpen: (DiscoveredIdentifier) -> Unit
) {
    var filtersOpen by remember { mutableStateOf(false) }
    var selectedSuspended by remember { mutableStateOf<SuspendedOverride?>(null) }
    var selectedDelta by remember { mutableStateOf<EditableCatalogDelta?>(null) }
    val type = typeName?.let { runCatching { DiscoveredType.valueOf(it) }.getOrNull() }
    val deltaCounts = state.catalogDeltas.groupingBy { it.kind }.eachCount()
    val filterCounts = mapOf(
        ExperimentFilter.MODIFIED to (state.dynamicOverrides.size +
            if (advancedAccess) state.advancedRecords.count { rec ->
                state.advancedCandidates.any { it.key == rec.key }
            } else 0),
        ExperimentFilter.SUSPENDED to state.suspendedOverrides.size,
        ExperimentFilter.ADDED to (deltaCounts[EditableDeltaKind.ADDED] ?: 0),
        ExperimentFilter.REMOVED to (deltaCounts[EditableDeltaKind.REMOVED] ?: 0),
        ExperimentFilter.CHANGED to (deltaCounts[EditableDeltaKind.CHANGED] ?: 0)
    )
    val availableFilters = ExperimentFilter.entries.filter { it == ExperimentFilter.ALL || (filterCounts[it] ?: 0) > 0 }
    LaunchedEffect(filter, state.loading, availableFilters) {
        if (!state.loading && filter !in availableFilters) onFilter(ExperimentFilter.ALL)
    }
    val availableTypes = remember(state.discovered, state.advancedCandidates,
        state.catalogDeltas, state.suspendedOverrides, advancedAccess) {
        (
            state.discovered.map { it.inferredType } +
            (if (advancedAccess) state.advancedCandidates.map { it.inferredType } else emptyList()) +
            state.catalogDeltas.mapNotNull { it.after?.type ?: it.before?.type } +
            state.suspendedOverrides.flatMap { entry ->
                listOfNotNull(entry.currentType, entry.storedType).mapNotNull {
                    runCatching { DiscoveredType.valueOf(it) }.getOrNull()
                }
            }
        ).toSet()
    }
    val hasReviewed = remember(state.discovered, state.catalogDeltas) {
        state.discovered.any { it.semanticsReviewed } ||
            state.catalogDeltas.any { (it.after ?: it.before)?.semanticsReviewed == true }
    }
    LaunchedEffect(type, availableTypes) { if (type != null && type !in availableTypes) onTypeName(null) }
    LaunchedEffect(reviewedOnly, hasReviewed) { if (reviewedOnly && !hasReviewed) onReviewedOnly(false) }

    val live = if (filter != ExperimentFilter.ALL && filter != ExperimentFilter.MODIFIED) emptyList()
    else (state.discovered + if (advancedAccess) state.advancedCandidates else emptyList())
        .distinctBy { it.key }.filter { identifier ->
        (filter != ExperimentFilter.MODIFIED || identifier.key in state.dynamicOverrides ||
            (advancedAccess && state.advancedRecords.any { it.key == identifier.key })) &&
            (!reviewedOnly || identifier.semanticsReviewed) &&
            (type == null || identifier.inferredType == type) &&
            (query.isBlank() || listOf(identifier.key, identifier.namespace.name, identifier.kind.name,
                identifier.inferredType.name, identifier.confidence.name, identifier.resolution.name,
                identifier.runtimeConsumerStatus.name, identifier.editabilityReason?.name.orEmpty(),
                identifier.reason.orEmpty(), identifier.metadata["reviewedSemantics"].orEmpty()
            ).any { it.contains(query, true) })
    }

    val suspended = (if (filter == ExperimentFilter.ALL || filter == ExperimentFilter.SUSPENDED)
        state.suspendedOverrides else emptyList()).filter { entry ->
        (filter != ExperimentFilter.ALL || entry.preferenceKey !in state.hiddenSuspendedKeys) &&
            (type == null || entry.currentType == type.name || entry.storedType == type.name) &&
            (query.isBlank() || entry.identifierKey?.contains(query, true) == true ||
                entry.preferenceKey.contains(query, true))
    }

    val category = when (filter) {
        ExperimentFilter.ADDED -> EditableDeltaKind.ADDED
        ExperimentFilter.REMOVED -> EditableDeltaKind.REMOVED
        ExperimentFilter.CHANGED -> EditableDeltaKind.CHANGED
        else -> null
    }
    val deltas = if (category == null) emptyList() else state.catalogDeltas.filter { entry ->
        val data = entry.after ?: entry.before
        entry.kind == category &&
            (type == null || data?.type == type) &&
            (!reviewedOnly || data?.semanticsReviewed == true) &&
            (query.isBlank() || entry.key.contains(query, true))
    }
    val filteredCount = live.size + suspended.size + deltas.size

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(query, onQuery, placeholder = { Text("Search experiments") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { IconButton({ filtersOpen = true }) { Icon(Icons.Default.FilterList, "Filters") } },
            singleLine = true, shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 54.dp))
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Experiments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (filter != ExperimentFilter.ALL)
                Text("Filter: ${friendly(filter.name)}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            Text("$filteredCount shown · ${state.dynamicOverrides.size} normal modified" +
                (if (advancedAccess) " · ${state.advancedCandidates.size} Advanced" else "") +
                " · ${state.suspendedOverrides.size} suspended",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (category != null) Text(
            if (category == EditableDeltaKind.REMOVED)
                "No longer editable in this build; it may still exist in the APK."
            else "Changes compared with the previously retained Android Auto build.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 16.dp)) {
            items(suspended, key = { "suspended:" + it.preferenceKey }) { entry ->
                SuspendedExperimentCard(entry) { selectedSuspended = entry }
            }
            items(live, key = { it.key }) { identifier ->
                CompactExperimentCard(identifier, state.dynamicOverrides[identifier.key],
                    if (!identifier.editable) state.advancedRecords.firstOrNull { it.key == identifier.key } else null,
                    !identifier.editable) { onOpen(identifier) }
            }
            items(deltas, key = { "delta:" + it.kind.name + ":" + it.key }) { delta ->
                EditableDeltaCard(delta) { selectedDelta = delta }
            }
        }
    }
    if (filtersOpen) ModalBottomSheet(onDismissRequest = { filtersOpen = false }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Filter experiments", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (availableFilters.size > 1) {
                Text("Show", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    availableFilters.forEach { option ->
                        FilterChip(selected = filter == option, onClick = { onFilter(option) },
                            label = { Text(if (option == ExperimentFilter.ALL) "All"
                                else "${friendly(option.name)} (${filterCounts[option]})") })
                    }
                }
            }
            Text("Value type", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val choices = listOf<DiscoveredType?>(null) + listOf(
                    DiscoveredType.BOOLEAN, DiscoveredType.INT, DiscoveredType.LONG,
                    DiscoveredType.FLOAT, DiscoveredType.DOUBLE, DiscoveredType.STRING
                ).filter { it in availableTypes }
                choices.forEach { choice ->
                    FilterChip(selected = type == choice, onClick = { onTypeName(choice?.name) },
                        label = { Text(choice?.let { friendly(it.name) } ?: "All") })
                }
            }
            if (hasReviewed) FilterToggle("Reviewed semantics",
                "Consumer meaning was manually audited", reviewedOnly, onReviewedOnly)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton({ onFilter(ExperimentFilter.ALL); onReviewedOnly(false); onTypeName(null) }) { Text("Reset") }
                Button({ filtersOpen = false }) { Text("Done") }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    selectedSuspended?.let { entry ->
        SuspendedOverrideDetailsDialog(entry, { selectedSuspended = null },
            model::discardSuspendedOverride, model::hideSuspendedFromAll,
            entry.preferenceKey in state.hiddenSuspendedKeys)
    }
    selectedDelta?.let { delta ->
        EditableDeltaDetailsDialog(delta, { selectedDelta = null }) { key ->
            state.discovered.firstOrNull { it.key == key }?.let {
                selectedDelta = null
                onOpen(it)
            }
        }
    }
    if (state.detailsLoading) AlertDialog({}, confirmButton = {}, title = { Text("Loading evidence…") },
        text = { LinearProgressIndicator(Modifier.fillMaxWidth()) })
}

@Composable private fun EditableDeltaCard(delta: EditableCatalogDelta, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(technicalDisplay(delta.key), fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { contentDescription = delta.key })
                val former = delta.before
                val current = delta.after
                Text(when (delta.kind) {
                    EditableDeltaKind.ADDED -> "ADDED · ${current?.type} · default ${current?.compiledDefault ?: "not inferred"}"
                    EditableDeltaKind.REMOVED -> "REMOVED FROM EDITABLE · previously ${former?.type}"
                    EditableDeltaKind.CHANGED -> "CHANGED · ${former?.compiledDefault ?: "∅"} → ${current?.compiledDefault ?: "∅"}"
                }, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Icon(Icons.Default.ChevronRight, "Compare builds")
        }
    }
}

@Composable private fun EditableDeltaDetailsDialog(
    delta: EditableCatalogDelta,
    onDismiss: () -> Unit,
    onOpenCurrent: (String) -> Unit
) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("${friendly(delta.kind.name)} · editable catalog") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(technicalDisplay(delta.key), fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold)
                run {
                    Text("Previous build", style = MaterialTheme.typography.titleSmall)
                    delta.before?.let { item ->
                        Text("Type: ${item.type} · default: ${item.compiledDefault ?: "not inferred"}")
                        Text("Confidence: ${item.confidence} · mapping: ${item.resolution}")
                        Text("Consumer: ${item.runtimeConsumerStatus}")
                    } ?: Text("Not present in previous editable catalog.")
                }
                HorizontalDivider()
                Text("Current build", style = MaterialTheme.typography.titleSmall)
                delta.after?.let { item ->
                    Text("Type: ${item.type} · default: ${item.compiledDefault ?: "not inferred"}")
                    Text("Confidence: ${item.confidence} · mapping: ${item.resolution}")
                    Text("Consumer: ${item.runtimeConsumerStatus}")
                } ?: Text("Not present in current editable catalog; the key might still exist as read-only.")
            }
        },
        confirmButton = {
            if (delta.after != null) TextButton({ onOpenCurrent(delta.key) }) { Text("Open experiment") }
            else TextButton(onDismiss) { Text("Close") }
        },
        dismissButton = {
            if (delta.after != null) TextButton(onDismiss) { Text("Close") }
        }
    )
}

private fun suspendedReasonLabel(reason: SuspendedOverrideReason): String = when (reason) {
    SuspendedOverrideReason.SUSPENDED_TYPE_CHANGED -> "Type changed"
    SuspendedOverrideReason.SUSPENDED_MAPPING_MISSING -> "Not found in this build"
    SuspendedOverrideReason.SUSPENDED_MAPPING_AMBIGUOUS -> "Resolver mapping unavailable or ambiguous"
    SuspendedOverrideReason.SUSPENDED_INVALID_VALUE -> "Stored value invalid"
    SuspendedOverrideReason.SUSPENDED_TARGET_COLLISION -> "Multiple flags resolve to the same getter"
}

private fun suspendedName(entry: SuspendedOverride): String =
    entry.identifierKey ?: "Unknown name · fingerprint ${entry.preferenceKey.takeLast(12)}"

@Composable private fun SuspendedExperimentCard(entry: SuspendedOverride, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(technicalDisplay(suspendedName(entry)), style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { contentDescription = suspendedName(entry) })
                Text("SUSPENDED · ${suspendedReasonLabel(entry.reason)}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            Icon(Icons.Default.ChevronRight, "View suspended override")
        }
    }
}

@Composable private fun SuspendedAuditListDialog(
    entries: List<SuspendedOverride>,
    hiddenKeys: Set<String>,
    onDismiss: () -> Unit,
    onDiscard: (String) -> Unit,
    onHide: (String) -> Unit
) {
    var selected by remember { mutableStateOf<SuspendedOverride?>(null) }
    if (selected != null) {
        val current = checkNotNull(selected)
        SuspendedOverrideDetailsDialog(current, { selected = null }, onDiscard, onHide,
            current.preferenceKey in hiddenKeys)
    } else {
        AlertDialog(onDismissRequest = onDismiss, title = { Text("Suspended overrides (${entries.size})") },
            text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text("Preserved but excluded from the published runtime profile.",
                    style = MaterialTheme.typography.bodySmall)
                entries.forEach { entry ->
                    TextButton({ selected = entry }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(technicalDisplay(suspendedName(entry)), maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text(suspendedReasonLabel(entry.reason), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            } }, confirmButton = { TextButton(onDismiss) { Text("Close") } })
    }
}

@Composable private fun SuspendedOverrideDetailsDialog(
    entry: SuspendedOverride,
    onDismiss: () -> Unit,
    onDiscard: (String) -> Unit,
    onHide: (String) -> Unit,
    alreadyHidden: Boolean
) {
    var confirmDiscard by remember(entry.preferenceKey) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Suspended override") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(technicalDisplay(suspendedName(entry)), fontWeight = FontWeight.SemiBold)
                Text("Reason: ${suspendedReasonLabel(entry.reason)}")
                Text("Saved type: ${entry.storedType ?: "unknown"}")
                Text("Saved value: ${entry.storedValue ?: "unreadable"}", maxLines = 4, overflow = TextOverflow.Ellipsis)
                Text("Current type: ${entry.currentType ?: "not resolved"}")
                entry.lastSeenVersionCode?.let { Text("Last mapped versionCode: $it") }
                Text("The value remains stored but is not included in the active runtime profile.",
                    style = MaterialTheme.typography.bodySmall)
                if (alreadyHidden) {
                    Text("Hidden from All for this Android Auto build. Still visible under Suspended.",
                        style = MaterialTheme.typography.labelSmall)
                } else {
                    OutlinedButton({
                        onHide(entry.preferenceKey)
                        onDismiss()
                    }, Modifier.fillMaxWidth()) {
                        Text("Keep suspended & hide from All")
                    }
                    Text("It will reappear in All if still suspended after the Android Auto build changes.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton({ confirmDiscard = true }) { Text("Discard override", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onDismiss) { Text("Keep suspended") } })
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false },
        title = { Text("Discard saved override?") },
        text = { Text("This permanently deletes the stored value. Keeping it suspended is safer if a future Android Auto update restores compatibility.") },
        confirmButton = { TextButton({
            confirmDiscard = false
            onDiscard(entry.preferenceKey)
            onDismiss()
        }) { Text("Discard", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirmDiscard = false }) { Text("Cancel") } })
}

@Composable private fun FilterToggle(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    // One semantic switch on the entire 48dp+ row, not two competing TalkBack targets.
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .toggleable(value = checked, role = Role.Switch, onValueChange = onChecked)
        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable private fun CatalogScreen(state: ManagerUiState, model: ManagerViewModel,
    advancedEnabled: Boolean) {
    var pendingCatalogExport by remember { mutableStateOf<CatalogExportRequest?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val request = pendingCatalogExport
        pendingCatalogExport = null
        if (uri != null && request != null) model.exportCatalogToUri(uri, request)
    }
    var query by remember { mutableStateOf("") }; var includeUnknown by remember { mutableStateOf(false) }
    var kind by remember { mutableStateOf<IdentifierKind?>(null) }; var type by remember { mutableStateOf<DiscoveredType?>(null) }
    var resolution by remember { mutableStateOf<MappingResolution?>(null) }; var reviewed by remember { mutableStateOf(false) }
    var runtime by remember { mutableStateOf<RuntimeConsumerStatus?>(null) }; var editable by remember { mutableStateOf(false) }
    var modified by remember { mutableStateOf(false) }; var killSwitch by remember { mutableStateOf(false) }
    var exportOpen by remember { mutableStateOf(false) }; var filtersOpen by remember { mutableStateOf(false) }
    var advancedTarget by remember { mutableStateOf<DiscoveredIdentifier?>(null) }
    var bulkRestoreWarning by remember { mutableStateOf(false) }
    val activeQuery = CatalogQuery(query, editableOnly = editable, includeUnknown = includeUnknown, kind = kind, type = type, resolution = resolution,
        runtimeStatus = runtime, reviewedOnly = reviewed, killSwitchOnly = killSwitch, overrideKeys = if (modified) state.dynamicOverrides.keys else null)
    LaunchedEffect(state.aa.buildFingerprintSha256, query, includeUnknown, kind, type, resolution, reviewed, runtime, editable, modified, killSwitch, state.dynamicOverrides.keys) {
        delay(300); model.searchCatalog(activeQuery)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Full Catalog", fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 6.dp))
            Text(if (advancedEnabled) "Advanced: ON" else "Advanced: OFF",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
        }
        if (advancedEnabled) {
            Text("UNSAFE ADVANCED OVERRIDES · not automatically reused after an AA build change",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            val total = state.advancedRecords.size
            val active = state.advancedRecords.count { it.active &&
                it.buildFingerprint == state.aa.buildFingerprintSha256 }
            Text("$active active / $total saved · ${state.advancedPublishedCount} published",
                style = MaterialTheme.typography.labelSmall)
            if (total > 0) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton({ model.pauseAllAdvanced() }) { Text("Pause all") }
                OutlinedButton({ bulkRestoreWarning = true }) { Text("Restore baselines") }
            }
        }
        OutlinedTextField(query, { query = it }, label = { Text("Search the complete catalog") }, trailingIcon = { IconButton({ filtersOpen=true }) { Icon(Icons.Default.FilterList, "Filters") } }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton({ exportOpen = true }) { Text("Export discoveries") } }
        Text("Showing ${state.catalog.size} of ${state.catalogTotal}. Search and filters run against the complete database.", style = MaterialTheme.typography.labelSmall)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
            items(state.catalog, key = { it.key }) { item -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text(item.key, fontWeight = FontWeight.Medium); Text("${item.kind} · ${item.storageType} / ${item.semanticType} · ${item.resolution}${if (item.editable) " · Editable" else ""}", style = MaterialTheme.typography.labelSmall)
                TextButton({ model.loadDetails(item.key) }) { Text("View evidence") }
                if (advancedEnabled) {
                    val record = state.advancedRecords.firstOrNull { it.key == item.key }
                    Text(if (record != null) "Advanced: ${if (record.active) "stored" else "paused"}"
                        else "Advanced eligibility: open View evidence",
                        style = MaterialTheme.typography.labelSmall)
                }
            } } }
            if (state.catalog.size < state.catalogTotal) item { OutlinedButton({ model.searchCatalog(activeQuery.copy(offset = state.catalog.size), append = true) }, Modifier.fillMaxWidth()) { Text("Load 500 more") } }
        }
    }
    if (state.detailsLoading) AlertDialog({}, confirmButton = {}, title = { Text("Loading evidence…") }, text = { LinearProgressIndicator(Modifier.fillMaxWidth()) })
    state.selectedDetails?.let {
        IdentifierDetailsDialog(it, model::clearDetails, state.probeTargetKey,
            state.aa.runtimeTarget && state.framework.connected, model::setReadOnlyProbe,
            state.experimentalSnapshot, state.experimentalProfileReady, state.aa.buildFingerprintSha256,
            state.message, model::setExperimentalBoolean, model::pauseExperimentalBoolean,
            model::restoreExperimentalBaseline, advancedEnabled, { id ->
                model.clearDetails()
                advancedTarget = id
            }, model::traceAdvancedConsumers,
            state.aa.baseSha256,
            state.dynamicOverrides[LongHandoffPolicy.KEY]?.let { DynamicOverrideCodec.serialize(it) },
            state.advancedRecords.firstOrNull { it.key == LongHandoffPolicy.KEY },
            model::startLongHandoff, model::pauseAdvancedOverride,
            model::resumeAdvancedOverride, model::restoreAdvancedBaseline)
    }
    advancedTarget?.let { id ->
        AdvancedOverrideEditor(id, state.advancedRecords.firstOrNull { it.key == id.key },
            state.aa.buildFingerprintSha256, state.advancedPublishedCount, state.message,
            model::setAdvancedOverride, model::pauseAdvancedOverride, model::resumeAdvancedOverride,
            model::restoreAdvancedBaseline, { advancedTarget = null })
    }
    if (bulkRestoreWarning) AlertDialog(onDismissRequest = { bulkRestoreWarning = false },
        title = { Text("Restore all current-build baselines?") },
        text = { Text("Restores the exact previous normal override or its absence, while leaving historical records from other Android Auto builds preserved. Restart Android Auto afterward.") },
        confirmButton = { TextButton({
            bulkRestoreWarning = false; model.restoreAllAdvanced()
        }) { Text("Restore") } },
        dismissButton = { TextButton({ bulkRestoreWarning = false }) { Text("Cancel") } })
    if (exportOpen) ExportDialog({ exportOpen = false }) { scope, format, methods, defaults, overrides ->
        val request = CatalogExportRequest(scope, format, methods, defaults, overrides)
        pendingCatalogExport = request
        exporter.launch(request.fileName)
    }
    if (filtersOpen) ModalBottomSheet(onDismissRequest={ filtersOpen=false }) { Column(Modifier.fillMaxWidth().padding(horizontal=20.dp).navigationBarsPadding(), verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Catalog filters", style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Bold)
        CompactEnumFilter("Kind", kind, listOf(null)+IdentifierKind.entries, { kind=it }, Modifier.fillMaxWidth())
        CompactEnumFilter("Value type", type, listOf(null)+DiscoveredType.entries, { type=it }, Modifier.fillMaxWidth())
        CompactEnumFilter("Resolution", resolution, listOf(null)+MappingResolution.entries, { resolution=it }, Modifier.fillMaxWidth())
        CompactEnumFilter("Runtime consumer", runtime, listOf(null)+RuntimeConsumerStatus.entries, { runtime=it }, Modifier.fillMaxWidth())
        FilterToggle("Include unknown", "Show entries without a resolved value type", includeUnknown) { includeUnknown=it }
        FilterToggle("Editable", "Only mappings eligible for runtime override", editable) { editable=it }
        FilterToggle("Modified", "Only entries with an override", modified) { modified=it }
        FilterToggle("Reviewed semantics", "Only manually audited consumers", reviewed) { reviewed=it }
        FilterToggle("Kill switches", "Only identifiers named as kill switches", killSwitch) { killSwitch=it }
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.End) { TextButton({ includeUnknown=false; kind=null; type=null; resolution=null; runtime=null; editable=false; modified=false; reviewed=false; killSwitch=false }) { Text("Reset") }; Button({ filtersOpen=false }) { Text("Done") } }
        Spacer(Modifier.height(12.dp))
    } }
}

@Composable private fun <T : Enum<T>> CompactEnumFilter(label: String, value: T?, values: List<T?>, onChange: (T?) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, { open = !open }, modifier) {
        OutlinedTextField(value?.name ?: "All", {}, readOnly = true, label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth())
        ExposedDropdownMenu(open, { open = false }) { values.forEach { option -> DropdownMenuItem({ Text(option?.name ?: "All") }, { onChange(option); open = false }) } }
    }
}

@Composable private fun ExportDialog(onDismiss: () -> Unit, onExport: (ExportScope, ExportFormat, Boolean, Boolean, Boolean) -> Unit) {
    var scope by remember { mutableStateOf(ExportScope.EDITABLE) }; var format by remember { mutableStateOf(ExportFormat.JSON) }
    var methods by remember { mutableStateOf(false) }; var defaults by remember { mutableStateOf(true) }; var overrides by remember { mutableStateOf(true) }
    AlertDialog(onDismiss, confirmButton = { TextButton({ onExport(scope, format, methods, defaults, overrides); onDismiss() }) { Text("Create file") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }, title = { Text("Export catalog") }, text = { Column {
            Text("Scope"); SingleChoiceSegmentedButtonRow { ExportScope.entries.forEachIndexed { i, v -> SegmentedButton(scope == v, { scope = v }, shape = SegmentedButtonDefaults.itemShape(i, ExportScope.entries.size)) { Text(if (v == ExportScope.ALL_DISCOVERIES) "ALL" else v.name) } } }
            Spacer(Modifier.height(8.dp)); Text("Format"); Row { ExportFormat.entries.forEach { v -> FilterChip(format == v, { format = v }, { Text(v.name) }); Spacer(Modifier.width(6.dp)) } }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(defaults, { defaults = it }); Text("Defaults") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(overrides, { overrides = it }); Text("Overrides") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(methods, { methods = it }); Text("Methods and consumers (slower)") }
        } })
}

@Composable private fun CompactExperimentCard(identifier: DiscoveredIdentifier,
    override: TypedOverride?, advancedRecord: AdvancedOverrideRecord?,
    isAdvanced: Boolean, onOpen: () -> Unit) {
    val isCustom = (override != null && identifier.inferredType != DiscoveredType.BOOLEAN) ||
        (advancedRecord?.active == true)
    val stateLabel = when {
        isAdvanced && advancedRecord == null -> "DEFAULT"
        isAdvanced && advancedRecord?.active == false -> "PAUSED"
        isAdvanced && advancedRecord != null -> "ACTIVE"
        override == null -> "DEFAULT"
        identifier.inferredType == DiscoveredType.BOOLEAN -> override.encodedValue.uppercase()
        else -> "CUSTOM"
    }
    val stateColor = when {
        isCustom -> MaterialTheme.colorScheme.primary
        override?.encodedValue == "true" -> MaterialTheme.colorScheme.primary
        override?.encodedValue == "false" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val stateContent = if (override == null && advancedRecord == null)
        MaterialTheme.colorScheme.onSurfaceVariant else if (override?.encodedValue == "false")
        MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            if (isCustom) Box(Modifier.width(4.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(technicalDisplay(identifier.key), modifier = Modifier.semantics { contentDescription = identifier.key }, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = if (override != null) FontWeight.SemiBold else FontWeight.Medium, fontFamily = FontFamily.Monospace)
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(friendly(identifier.inferredType.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    if (isAdvanced) Surface(shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text("ADVANCED", Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (advancedRecord != null) "Experimental · ${advancedRecord.type}"
                         else if (override != null) "Modified" else "", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Surface(modifier = Modifier.width(92.dp), color = stateColor, contentColor = stateContent, shape = MaterialTheme.shapes.medium) {
                        Text(stateLabel, Modifier.fillMaxWidth().padding(vertical = 4.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Icon(Icons.Default.ChevronRight, "Open experiment", Modifier.size(22.dp))
                }
            }
        }
    }
}

private fun technicalDisplay(value: String): String = buildString(value.length + 8) {
    value.forEachIndexed { index, char ->
        if (index > 0 && char.isUpperCase() && value[index - 1].isLowerCase()) append('\u200B')
        append(char)
        if (char == '_') append('\u200B')
    }
}

@Composable private fun MetaChip(label: String, color: Color) { Surface(shape = MaterialTheme.shapes.small, color = color) { Text(label, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium) } }
private fun friendly(value: String) = value.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
private fun isNumericZero(value: String): Boolean = value.trim().matches(Regex("[+-]?0+(?:\\.0+)?"))

@Composable private fun ExperimentDetailScreen(
    identifier: DiscoveredIdentifier,
    loaded: DiscoveredIdentifier?,
    override: TypedOverride?,
    connected: Boolean,
    saveState: SaveState,
    saveTargetKey: String?,
    saveMessage: String?,
    onChange: (String?) -> Unit,
    onLoadEvidence: () -> Unit,
    onBack: () -> Unit,
    isAdvanced: Boolean,
    advancedRecord: AdvancedOverrideRecord?,
    advancedBuild: String?,
    advancedPublishedCount: Int,
    onAdvancedSave: (DiscoveredIdentifier, String) -> Unit,
    onAdvancedPause: (String) -> Unit,
    onAdvancedResume: (String) -> Unit,
    onAdvancedRestore: (String) -> Unit
) {
    var input by remember(identifier.key, override) { mutableStateOf(override?.encodedValue.orEmpty()) }
    var saveConfirmed by remember(identifier.key) { mutableStateOf(false) }
    val normalInputIssue = OverrideInputFeedback.issue(identifier.inferredType, input)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val savingCurrent = saveTargetKey == identifier.key && saveState == SaveState.SAVING
    val failedCurrent = saveTargetKey == identifier.key && saveState == SaveState.FAILED
    LaunchedEffect(identifier.key, saveTargetKey, saveState) {
        if (saveTargetKey == identifier.key && saveState == SaveState.SAVED) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            saveConfirmed = true
            delay(1700)
            saveConfirmed = false
        } else if (saveTargetKey == identifier.key && saveState == SaveState.SAVING) {
            saveConfirmed = false
        }
    }
    var evidenceExpanded by remember(identifier.key) { mutableStateOf(false) }
    var rawExpanded by remember(identifier.key) { mutableStateOf(false) }
    var advancedExpanded by remember(identifier.key) { mutableStateOf(false) }
    var rawResolverExpanded by remember(identifier.key) { mutableStateOf(false) }
    val details = loaded?.takeIf { it.key == identifier.key } ?: identifier
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 64.dp).padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to experiments", tint = MaterialTheme.colorScheme.onBackground) }
            Text("Experiment details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        }
        HorizontalDivider()
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(technicalDisplay(identifier.key), modifier = Modifier.semantics { contentDescription = identifier.key }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onBackground) }
            item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MetaChip(friendly(identifier.inferredType.name), MaterialTheme.colorScheme.secondaryContainer)
                MetaChip(friendly(identifier.resolution.name), MaterialTheme.colorScheme.tertiaryContainer)
                if (isAdvanced) MetaChip("ADVANCED", MaterialTheme.colorScheme.errorContainer)
                if (isAdvanced && advancedRecord != null)
                    MetaChip(if (advancedRecord.active) "Active" else "Paused", MaterialTheme.colorScheme.primaryContainer)
                if (!isAdvanced && override != null) MetaChip("Modified", MaterialTheme.colorScheme.primaryContainer)
                MetaChip(friendly(identifier.runtimeConsumerStatus.name), MaterialTheme.colorScheme.surfaceVariant)
            } }
            item { identifier.metadata["reviewedSemantics"]?.let { Text(it, style = MaterialTheme.typography.bodyMedium) } ?: Text(
                if (isAdvanced) "Experimental getter override. Value semantics and supported ranges are not guaranteed."
                else "Configure the runtime value returned by this experiment.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            ) }
            item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Values", fontWeight = FontWeight.Bold)
                ValueRow("Default value", identifier.compiledDefault ?: "Not inferred")
                ValueRow("Runtime verification", "Check LSPosed logs")
                if (isAdvanced) {
                    val belongs = advancedRecord?.buildFingerprint == advancedBuild
                    val activeRecord = advancedRecord?.takeIf { belongs && it.active }
                    ValueRow("Experimental value", advancedRecord?.value ?: "Not set", activeRecord != null)
                    ValueRow("State", when {
                        advancedRecord == null -> "Default / original"
                        !belongs -> "Suspended for another build"
                        advancedRecord.active -> "Active · restart AA"
                        else -> "Paused · original baseline"
                    })
                    ValueRow("Baseline", advancedRecord?.previousOverride ?: "Absent / system-managed")
                } else {
                    AnimatedContent(targetState = override?.encodedValue ?: identifier.compiledDefault ?: "Default",
                        label = "effective-value") { value ->
                        ValueRow("Configured value", value, override != null)
                    }
                }
                ValueRow("Occurrences", identifier.occurrences.size.toString())
                ValueRow("Runtime consumer", friendly(identifier.runtimeConsumerStatus.name))
            } } }
            if (isAdvanced) item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(14.dp)) {
                        AdvancedOverrideEditor(
                            identifier, advancedRecord, advancedBuild, advancedPublishedCount,
                            saveMessage, onAdvancedSave, onAdvancedPause,
                            onAdvancedResume, onAdvancedRestore, onBack,
                            inline = true, connected = connected
                        )
                    }
                }
            } else item { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Override value", fontWeight = FontWeight.Bold)
                if (identifier.inferredType == DiscoveredType.BOOLEAN) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { listOf<String?>(null, "false", "true").forEachIndexed { index, value ->
                        val selectedColor = when (value) { "true" -> MaterialTheme.colorScheme.primary; "false" -> MaterialTheme.colorScheme.secondary; else -> MaterialTheme.colorScheme.surfaceVariant }
                        val selectedContent = when (value) { "true" -> MaterialTheme.colorScheme.onPrimary; "false" -> MaterialTheme.colorScheme.onSecondary; else -> MaterialTheme.colorScheme.onSurfaceVariant }
                        SegmentedButton((override?.encodedValue) == value || (override == null && value == null), { onChange(value) }, enabled = connected, shape = SegmentedButtonDefaults.itemShape(index, 3), colors = SegmentedButtonDefaults.colors(activeContainerColor = selectedColor, activeContentColor = selectedContent)) { Text(when(value){null->"Default";"false"->"False";else->"True"}) }
                    } }
                    Text(when(override?.encodedValue) { "true" -> "Force true: always return true for this experiment."; "false" -> "Force false: always return false for this experiment."; else -> "Default: use the value selected by Android Auto." }, style = MaterialTheme.typography.bodySmall)
                } else {
                    OutlinedTextField(input, { input = it },
                        label = { Text("${friendly(identifier.inferredType.name)} value") },
                        singleLine = identifier.inferredType != DiscoveredType.STRING,
                        maxLines = if (identifier.inferredType == DiscoveredType.STRING) 4 else 1,
                        keyboardOptions = KeyboardOptions(keyboardType = OverrideInputFeedback.keyboardType(identifier.inferredType)),
                        isError = normalInputIssue != null,
                        supportingText = if (normalInputIssue != null) ({ Text(normalInputIssue) }) else null,
                        modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ onChange(input) }, enabled = connected && !savingCurrent && normalInputIssue == null) {
                            Text(when { savingCurrent -> "Saving…"; saveConfirmed -> "✓ Saved"; else -> "Apply" })
                        }
                        OutlinedButton({ onChange(null) }, enabled = connected && !savingCurrent && override != null) { Text("Default") }
                    }
                    if (failedCurrent) Text(saveMessage ?: "Unable to save override.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            } } }
            item { DetailDisclosure(Icons.Default.Search, "Evidence", "${details.occurrences.size} occurrence(s) · ${details.consumers.size} consumer path(s)", evidenceExpanded, {
                evidenceExpanded = !evidenceExpanded
                if (loaded?.key != identifier.key) onLoadEvidence()
            }) {
                if (loaded?.key != identifier.key) LinearProgressIndicator(Modifier.fillMaxWidth())
                else if (details.consumers.isEmpty()) Text("No runtime consumer was found inside the bounded static graph.", style = MaterialTheme.typography.bodySmall)
                else details.consumers.take(8).forEach { evidence -> EvidenceTechnicalBlock("${evidence.method.className}->${evidence.method.methodName}${evidence.method.descriptor}", "${evidence.linkKind} · depth ${evidence.depth} · ${evidence.method.dex}") }
            } }
            item { DetailDisclosure(Icons.Default.Code, "Raw identifiers", "Source and storage metadata", rawExpanded, { rawExpanded = !rawExpanded }) {
                TechnicalBlock("Kind", identifier.kind.name); TechnicalBlock("Namespace", identifier.namespace.name); TechnicalBlock("Storage type", identifier.storageType.name); TechnicalBlock("Semantic type", identifier.semanticType.name); TechnicalBlock("Preference key", DynamicOverrideCodec.preferenceKey(identifier.key))
            } }
            item { DetailDisclosure(Icons.Default.Tune, "Advanced information", identifier.reason ?: "Resolver evidence and activation", advancedExpanded, { advancedExpanded = !advancedExpanded }) {
                DiagnosticSectionTitle("Value flow")
                TechnicalBlock("Origin", readableMetadataValue(identifier.metadata["valueOrigin"] ?: "UNKNOWN"))
                TechnicalBlock("Fallback", identifier.metadata["compiledFallback"]?.let { if (it.isEmpty()) "Compiled default: empty string" else "Compiled default: $it" } ?: "Not inferred")
                TechnicalBlock("Downstream gates", readableMetadataValue(identifier.metadata["downstreamGates"] ?: "NOT_DETECTED"))
                TechnicalBlock("Transport", readableMetadataValue(identifier.metadata["transportDestinations"] ?: "NOT_DETECTED"))
                TechnicalBlock("Derived inputs", readableMetadataValue(identifier.metadata["derivedInputs"] ?: "NOT_PROVEN"))
                TechnicalBlock("Precedence", readableMetadataValue(identifier.metadata["sourcePrecedence"] ?: "NOT_PROVEN"))
                HorizontalDivider()
                DiagnosticSectionTitle("Lifecycle")
                TechnicalBlock("Read behavior", readableMetadataValue(identifier.metadata["consumptionPaths"] ?: "UNKNOWN"))
                TechnicalBlock("Reevaluation", readableMetadataValue(identifier.metadata["reevaluationTriggers"] ?: "NOT_PROVEN"))
                TechnicalBlock("Activation", readableMetadataValue(identifier.metadata["activationScopes"] ?: "UNKNOWN"))
                HorizontalDivider()
                DiagnosticSectionTitle("Resolver confidence")
                TechnicalBlock("Getter", readableMetadataValue(identifier.evidenceConfidence.getter.name))
                TechnicalBlock("Consumer", readableMetadataValue(identifier.evidenceConfidence.runtimeConsumer.name))
                TechnicalBlock("Semantics", readableMetadataValue(identifier.evidenceConfidence.semantics.name))
                TechnicalBlock("Analysis", readableMetadataValue(identifier.metadata["analysisStatus"] ?: "UNKNOWN"))
                TechnicalBlock("Cross-path confidence", readableMetadataValue(identifier.metadata["analysisConfidence"] ?: "UNKNOWN"))
                TextButton({ rawResolverExpanded = !rawResolverExpanded }) { Text(if (rawResolverExpanded) "Hide raw resolver metadata" else "Show raw resolver metadata") }
                if (rawResolverExpanded) identifier.metadata.toSortedMap().filterValues { !isNumericZero(it) }.forEach { (key, value) ->
                    TechnicalBlock(readableMetadataLabel(key), readableMetadataValue(value))
                }
            } }
            if (!connected) item { Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) { Text("Editing is unavailable until the LSPosed service and installed Android Auto target are ready.", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) } }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable private fun DetailDisclosure(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, expanded: Boolean, onToggle: () -> Unit, content: @Composable ColumnScope.() -> Unit) { Card(Modifier.fillMaxWidth()) { Column { Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 2) }; Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "Collapse" else "Expand") }; if (expanded) { HorizontalDivider(); Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) } } } }
@Composable private fun TechnicalBlock(label: String, value: String) { Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary); Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) } }
@Composable private fun DiagnosticSectionTitle(value: String) { Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) }
@Composable private fun EvidenceTechnicalBlock(label: String, value: String) { Column(verticalArrangement = Arrangement.spacedBy(1.dp)) { Text(label, style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp), color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace); Text(value, style = MaterialTheme.typography.labelSmall.copy(lineHeight = 15.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace) } }

private fun readableMetadataLabel(value: String): String = value
    .replace(Regex("([a-z0-9])([A-Z])"), "\$1 \$2")
    .replace('_', ' ')
    .lowercase()
    .replaceFirstChar { it.uppercase() }
    .replace(Regex("\\bSdk\\b"), "SDK")
    .replace(Regex("\\bDex\\b"), "DEX")
    .replace(Regex("\\bApk\\b"), "APK")
    .replace(Regex("\\bId\\b"), "ID")

private fun readableMetadataValue(value: String): String = value.split('|').joinToString("\n") { item ->
    item.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
        .replace(Regex("\\bSdk\\b"), "SDK").replace(Regex("\\bDex\\b"), "DEX")
        .replace(Regex("\\bApk\\b"), "APK").replace(Regex("\\bId\\b"), "ID")
}

@Composable private fun ValueRow(label: String, value: String, accent: Boolean = false) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) { Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); Text(value, Modifier.weight(1f), color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontWeight = if (accent) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis) } }

@Composable private fun IdentifierDetailsDialog(
    identifier: DiscoveredIdentifier,
    onDismiss: () -> Unit,
    armedProbeKey: String?,
    canProbeInstalledBuild: Boolean,
    onProbe: (DiscoveredIdentifier, Boolean) -> Unit,
    experiment: ExperimentalBooleanSnapshot?,
    experimentalProfileReady: Boolean,
    installedBuildFingerprint: String?,
    feedback: String?,
    onExperimentalSet: (DiscoveredIdentifier, Boolean) -> Unit,
    onExperimentalPause: () -> Unit,
    onExperimentalRestore: () -> Unit,
    advancedEnabled: Boolean,
    onOpenAdvanced: (DiscoveredIdentifier) -> Unit,
    onTraceConsumers: (String) -> Unit,
    installedBaseSha256: String?,
    currentNormalLong: String?,
    longHandoffRecord: AdvancedOverrideRecord?,
    onStartLongHandoff: () -> Unit,
    onPauseHandoff: (String) -> Unit,
    onResumeHandoff: (String) -> Unit,
    onRestoreHandoff: (String) -> Unit
) {
    var longHandoffStage by remember(identifier.key) { mutableIntStateOf(0) }
    var handoffAcknowledgement by remember(identifier.key) { mutableStateOf("") }
    var longHandoffRestoreWarning by remember(identifier.key) { mutableStateOf(false) }
    var experimentalConfirmation by remember(identifier.key) { mutableStateOf<Boolean?>(null) }
    var restoreExperimentConfirmation by remember(identifier.key) { mutableStateOf(false) }
    var probeConfirmation by remember(identifier.key) { mutableStateOf(false) }
    val probeIssue = if (identifier.key in GetterProbePolicy.supportedKeys)
        GetterProbePolicy.issue(identifier) else "Not a probe candidate."
    val isArmed = armedProbeKey == identifier.key
    val clipboard = LocalClipboardManager.current
    val export = remember(identifier) { buildString {
        appendLine(identifier.key)
        appendLine("${identifier.kind} / storage ${identifier.storageType} / semantic ${identifier.semanticType} / ${identifier.resolution}")
        appendLine("Runtime consumer: ${identifier.runtimeConsumerStatus}; reviewed: ${identifier.semanticsReviewed}")
        identifier.metadata["reviewedSemantics"]?.let { appendLine("Semantics: $it") }
        identifier.metadata["trueMeaning"]?.let { appendLine("true: $it") }
        identifier.metadata["falseMeaning"]?.let { appendLine("false: $it") }
        identifier.metadata["reviewEvidence"]?.let { appendLine("Evidence: $it") }
        identifier.metadata["protobufResolution"]?.let { appendLine("Protobuf: ${it.removePrefix("PROTOBUF_").lowercase().replace('_', ' ')}") }
        identifier.metadata["protobufSummary"]?.let { appendLine("Wire summary: $it") }
        identifier.metadata["protobufStrings"]?.let { appendLine("Decoded strings: ${it.replace('|', ',')}") }
        identifier.metadata["enumMapping"]?.let { appendLine("Enum mapping: ${it.lowercase()}; domain: ${identifier.metadata["enumDomain"]?.lowercase()}") }
        identifier.metadata["consumptionPaths"]?.let { appendLine("Consumption: ${it.replace('|', ',')}") }
        identifier.metadata["activationScopes"]?.let { appendLine("Activation: ${it.replace('|', ',')}") }
        identifier.metadata["valueOrigin"]?.let { appendLine("Origin: ${readableMetadataValue(it)}") }
        identifier.metadata["compiledFallback"]?.let { appendLine("Fallback: ${if (it.isEmpty()) "empty string" else it}") }
        identifier.metadata["downstreamGates"]?.let { appendLine("Downstream gates: ${readableMetadataValue(it)}") }
        identifier.metadata["transportDestinations"]?.let { appendLine("Transport: ${readableMetadataValue(it)}") }
        identifier.metadata["sourcePrecedence"]?.let { appendLine("Source precedence: ${it.replace(">", " → ")}") }
        identifier.metadata["reevaluationTriggers"]?.let { appendLine("Reevaluation: ${it.replace('|', ',')}") }
        identifier.metadata["cacheTargets"]?.let { appendLine("Cache targets: ${it.replace('|', ',')}") }
        identifier.metadata["fieldLineage"]?.let { appendLine("Field lineage: ${it.replace('|', '\n')}") }
        identifier.metadata["runtimeLatchTargets"]?.let { appendLine("Runtime latches: ${it.replace('|', ',')}") }
        identifier.consumers.forEachIndexed { index, evidence ->
            appendLine("Consumer ${index + 1}: ${evidence.method.className}->${evidence.method.methodName}${evidence.method.descriptor}")
            appendLine("  ${evidence.linkKind}, depth ${evidence.depth}, ${evidence.method.dex}")
            evidence.path.forEach { appendLine("  $it") }
        }
    } }
    Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(0.9f), shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Text(identifier.key, style = MaterialTheme.typography.titleLarge)
                Text("${identifier.kind} · ${identifier.storageType} / ${identifier.semanticType} · ${identifier.resolution}", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { StatusCard("Runtime consumer", "${identifier.runtimeConsumerStatus} · ${identifier.consumers.size} retained path(s)") }
                    if (advancedEnabled) item {
                        val issue = AdvancedOverridePolicy.issue(identifier)
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Advanced Overrides", fontWeight = FontWeight.Bold)
                                Text(issue?.let { "Read-only: $it" } ?:
                                    "Typed getter identified; effects and accepted value domain are not guaranteed.",
                                    style = MaterialTheme.typography.bodySmall)
                                Button({ onOpenAdvanced(identifier) }, enabled = issue == null) {
                                    Text("Open advanced editor")
                                }
                                OutlinedButton({ onTraceConsumers(identifier.key) },
                                    enabled = issue == null && canProbeInstalledBuild) {
                                    Text("Trace consumers · read-only")
                                }
                                Text("Trace only this getter in the installed APK. The bounded DEX analysis does not change overrides or prove runtime execution.",
                                    style = MaterialTheme.typography.labelSmall)
                                if (identifier.metadata["targetedResolveAttempted"] == "true") {
                                    Text("Last trace: ${identifier.metadata["targetedResolutionReason"] ?: "INCONCLUSIVE"} · ${identifier.consumers.size} path(s)",
                                        style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    if (advancedEnabled && identifier.key == LongHandoffPolicy.KEY) item {
                        val blocker = LongHandoffPolicy.issue(identifier, installedBaseSha256)
                        val snapshot = longHandoffRecord
                        val sameBuild = snapshot?.buildFingerprint == installedBuildFingerprint
                        val isHandoff = snapshot?.handoffMode == LongHandoffPolicy.MODE && sameBuild
                        val pristineNormal = currentNormalLong == LongHandoffPolicy.ORIGINAL
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Column(Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("LONG hook handoff · beta4 preflight10",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold)
                                Text("Single reviewed getter aczh.l()J. Experimental return is fixed to 3; the existing normal LONG:3 stays stored throughout the test.",
                                    style = MaterialTheme.typography.bodySmall)
                                ValueRow("Normal override", currentNormalLong ?: "Not present")
                                Text("Handoff: " + when {
                                    isHandoff -> if (snapshot?.active == true) "ACTIVE" else "PAUSED"
                                    snapshot == null -> "NOT ARMED"
                                    else -> "SUSPENDED OR UNRECOGNIZED"
                                }, style = MaterialTheme.typography.labelMedium)
                                if (blocker != null) Text("Unavailable: $blocker",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall)
                                if (!pristineNormal) Text(
                                    "Requires the existing LONG:3 normal override. Do not modify the normal editor while the handoff is active.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall)
                                if (snapshot == null) {
                                    Button({
                                        handoffAcknowledgement = ""
                                        longHandoffStage = 1
                                    }, enabled = blocker == null && pristineNormal &&
                                        canProbeInstalledBuild && armedProbeKey != identifier.key) {
                                        Text("Review LONG 3→3 handoff")
                                    }
                                } else if (isHandoff) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton({
                                            if (snapshot?.active == true) onPauseHandoff(LongHandoffPolicy.KEY)
                                            else onResumeHandoff(LongHandoffPolicy.KEY)
                                        }, enabled = pristineNormal) {
                                            Text(if (snapshot?.active == true) "Pause" else "Resume")
                                        }
                                        OutlinedButton({ longHandoffRestoreWarning = true }) {
                                            Text("Restore baseline")
                                        }
                                    }
                                }
                                feedback?.takeIf { it.contains("handoff", ignoreCase = true) }?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                                Text("Restart Android Auto after changes. Verify ADVANCED_GETTER_INVOKED and ADVANCED_OVERRIDE_RETURNED with type LONG, value 3. A normal OVERRIDE_RETURNED does not prove the handoff.",
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    if (identifier.key in GetterProbePolicy.supportedKeys) item {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Read-only getter probe", fontWeight = FontWeight.SemiBold)
                                Text("Calls the original getter without replacing its return value. Logs installation and first invocation after Android Auto restarts.",
                                    style = MaterialTheme.typography.bodySmall)
                                if (isArmed) Text("ARMED FOR THIS BUILD · restart Android Auto to install",
                                    color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                                else if (probeIssue != null) Text(probeIssue,
                                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                if (armedProbeKey != null && !isArmed) Text(
                                    "Another getter is armed: ${technicalDisplay(armedProbeKey)}",
                                    style = MaterialTheme.typography.labelSmall)
                                OutlinedButton(
                                    onClick = {
                                        if (isArmed) onProbe(identifier, false)
                                        else probeConfirmation = true
                                    },
                                    enabled = canProbeInstalledBuild && (isArmed || probeIssue == null),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (isArmed) "Disarm probe" else "Arm read-only probe")
                                }
                                Text("Disarming removes it from the next runtime profile. An already-installed hook remains until Android Auto restarts.",
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    if (identifier.key == ExperimentalBooleanPolicy.KEY) item {
                        val blocker = ExperimentalBooleanPolicy.issue(identifier)
                        val sameBuild = experiment?.buildFingerprint == installedBuildFingerprint
                        val canWrite = canProbeInstalledBuild && blocker == null &&
                            (experiment == null || sameBuild) && armedProbeKey != identifier.key
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                Text("Experimental BOOLEAN override · beta4 preflight",
                                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("Only this known getter is eligible. The new value may have no visible effect or cause Android Auto to malfunction.",
                                    style = MaterialTheme.typography.bodySmall)
                                ValueRow("Compiled default", identifier.compiledDefault ?: "Unknown")
                                ValueRow("Detected type", identifier.inferredType.name)
                                ValueRow("Runtime consumer", identifier.runtimeConsumerStatus.name)
                                ValueRow("Confidence", identifier.confidence.name)
                                ValueRow("Resolution", identifier.resolution.name)
                                if (experiment != null) {
                                    ValueRow("Experimental value", experiment.value.toString(), experiment.active && sameBuild)
                                    ValueRow("Before first edit",
                                        experiment.previousOverride ?: "No override (not a forced default)")
                                    Text(if (!sameBuild)
                                        "SUSPENDED: Android Auto changed builds. Nothing is reapplied automatically."
                                    else if (experiment.active)
                                        if (experimentalProfileReady) "PROFILE PUBLISHED · restart AA; check OVERRIDE_RETURNED."
                                        else "SAVED · hook not yet published."
                                    else "PAUSED · saved baseline retained.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (sameBuild && experiment.active && experimentalProfileReady)
                                            MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                } else {
                                    Text("No experimental snapshot exists. First edit saves your original override state.",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                feedback?.takeIf { it.contains("Experimental", true) ||
                                    it.contains("baseline", true) }?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall,
                                        color = if (it.contains("rejected", true) || it.contains("failed", true))
                                            MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                                }
                                if (blocker != null) Text("Not eligible: $blocker",
                                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                if (armedProbeKey == identifier.key) Text(
                                    "Disarm the read-only probe and restart Android Auto before using this override.",
                                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                if (experiment != null && !sameBuild) Text(
                                    "The saved experiment is preserved for its original build; no automatic reactivation is allowed.",
                                    style = MaterialTheme.typography.bodySmall)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button({ experimentalConfirmation = true }, Modifier.weight(1f), enabled = canWrite) {
                                        Text("Set TRUE")
                                    }
                                    Button({ experimentalConfirmation = false }, Modifier.weight(1f), enabled = canWrite) {
                                        Text("Set FALSE")
                                    }
                                }
                                if (experiment != null && sameBuild) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onExperimentalPause, Modifier.weight(1f), enabled = experiment.active) {
                                            Text("Pause")
                                        }
                                        OutlinedButton({ restoreExperimentConfirmation = true }, Modifier.weight(1f)) {
                                            Text("Restore baseline")
                                        }
                                    }
                                }
                                Text("After any edit, restart the Android Auto process while disconnected and parked. Do not infer effectiveness from a saved value alone.",
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    identifier.metadata["reviewedSemantics"]?.let { summary -> item {
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Reviewed semantics", fontWeight = FontWeight.Bold); Text(summary)
                            identifier.metadata["trueMeaning"]?.let { Text("true → $it") }
                            identifier.metadata["falseMeaning"]?.let { Text("false → $it") }
                            identifier.metadata["reviewEvidence"]?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            identifier.metadata["reviewedForBaseSha256"]?.let { Text("Build: ${it.take(16)}…", style = MaterialTheme.typography.labelSmall) }
                        } }
                    } }
                    identifier.metadata["protobufResolution"]?.let { resolution -> item {
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Protobuf inspection", fontWeight = FontWeight.Bold)
                            Text(resolution.removePrefix("PROTOBUF_").lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() })
                            identifier.metadata["protobufSummary"]?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            identifier.metadata["protobufStrings"]?.let { Text("Decoded strings: ${it.replace('|', ',')}", style = MaterialTheme.typography.bodySmall) }
                            Text("Read-only until the message schema and field semantics are resolved.", style = MaterialTheme.typography.labelSmall)
                        } }
                    } }
                    identifier.metadata["enumMapping"]?.let { mapping -> item {
                        StatusCard("Numeric enum", "Mapping ${mapping.lowercase()} · domain ${identifier.metadata["enumDomain"]?.lowercase()}\nStorage: ${identifier.metadata["enumStorage"]}\nFree numeric editing remains disabled until the accepted domain is proven.")
                    } }
                    identifier.metadata["consumptionPaths"]?.let { paths -> item {
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Runtime activation", fontWeight = FontWeight.Bold)
                            Text("Consumption: ${paths.replace('|', ',')}")
                            Text("Takes effect: ${identifier.metadata["activationScopes"].orEmpty().replace('|', ',')}")
                            identifier.metadata["downstreamGates"]?.let { Text("Downstream gates: ${readableMetadataValue(it)}", style = MaterialTheme.typography.bodySmall) }
                            identifier.metadata["transportDestinations"]?.let { Text("Transport: ${readableMetadataValue(it)}", style = MaterialTheme.typography.bodySmall) }
                            identifier.metadata["cacheTargets"]?.let { Text("Cached in: ${it.replace('|', ',')}", style = MaterialTheme.typography.bodySmall) }
                            identifier.metadata["cacheTransformations"]?.let { Text("Transformations: ${it.replace('|', ',')}", style = MaterialTheme.typography.bodySmall) }
                            identifier.metadata["fieldLineage"]?.let { Text("Field lineage: ${it.replace('|', '\n')}", style = MaterialTheme.typography.bodySmall) }
                            identifier.metadata["runtimeLatchTargets"]?.let { Text("Runtime latch: ${it.replace('|', ',')}", style = MaterialTheme.typography.bodySmall) }
                            identifier.metadata["fieldConsumerMethods"]?.let { Text("Final consumers: ${it.replace('|', '\n')}", style = MaterialTheme.typography.labelSmall) }
                        } }
                    } }
                    if (identifier.consumers.isEmpty()) item { Text("No consumer was found within the bounded static graph. This is not proof that the value is unused at runtime.") }
                    items(identifier.consumers) { evidence -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${evidence.method.className}->${evidence.method.methodName}${evidence.method.descriptor}", fontWeight = FontWeight.Bold)
                        Text("${evidence.linkKind} · depth ${evidence.depth} · ${evidence.method.dex}", style = MaterialTheme.typography.labelSmall)
                        evidence.path.forEachIndexed { index, step -> Text("${index + 1}. $step", style = MaterialTheme.typography.bodySmall) }
                    } } }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton({ clipboard.setText(AnnotatedString(export)) }) { Text("Copy") }
                    TextButton(onDismiss) { Text("Close") }
                }
            }
        }
    }
    if (longHandoffStage == 1) AlertDialog(
        onDismissRequest = { longHandoffStage = 0 },
        title = { Text("Review controlled LONG handoff") },
        text = { Text("This test is limited to Assistant__icon_experiment_variant on the inspected AA 18.0 build. Both hooks return 3. The saved normal LONG:3 remains intact, while only one mapping per getter is published. Pause and Restore return hook ownership to the normal mapping. Restart Android Auto after changes.") },
        confirmButton = { TextButton({ longHandoffStage = 2 }) { Text("Continue") } },
        dismissButton = { TextButton({ longHandoffStage = 0 }) { Text("Cancel") } }
    )
    if (longHandoffStage == 2) AlertDialog(
        onDismissRequest = { longHandoffStage = 0 },
        title = { Text("Final confirmation") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Type HANDOFF 3 to switch hook ownership without changing the effective LONG value.")
            OutlinedTextField(handoffAcknowledgement, { handoffAcknowledgement = it },
                label = { Text("HANDOFF 3") }, singleLine = true)
        } },
        confirmButton = { TextButton({
            longHandoffStage = 0
            onStartLongHandoff()
        }, enabled = handoffAcknowledgement == "HANDOFF 3") { Text("Start handoff") } },
        dismissButton = { TextButton({ longHandoffStage = 0 }) { Text("Cancel") } }
    )
    if (longHandoffRestoreWarning) AlertDialog(
        onDismissRequest = { longHandoffRestoreWarning = false },
        title = { Text("Restore the normal getter hook?") },
        text = { Text("Only the experimental handoff record is removed. The original normal LONG:3 preference remains stored byte-for-byte; restart Android Auto to install its original hook.") },
        confirmButton = { TextButton({
            longHandoffRestoreWarning = false
            onRestoreHandoff(LongHandoffPolicy.KEY)
        }) { Text("Restore normal hook") } },
        dismissButton = { TextButton({ longHandoffRestoreWarning = false }) { Text("Cancel") } }
    )
    experimentalConfirmation?.let { newValue ->
        AlertDialog(onDismissRequest = { experimentalConfirmation = null },
            title = { Text("Confirm experimental return value") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(technicalDisplay(identifier.key), fontFamily = FontFamily.Monospace)
                    Text("BOOLEAN · getter ${identifier.getterCandidates.singleOrNull()?.methodName}()Z")
                    Text("Compiled default: ${identifier.compiledDefault ?: "Unknown"}")
                    Text("Original override before experimentation: ${experiment?.previousOverride ?: "Absent, or not yet recorded"}")
                    Text("New experimental value: $newValue")
                    Text("The meaning of this kill switch is unverified. It can disable Android Auto features or require stopping its process. This change is for the current Android Auto build only.")
                }
            },
            confirmButton = { TextButton({
                experimentalConfirmation = null
                onExperimentalSet(identifier, newValue)
            }) { Text("Confirm $newValue") } },
            dismissButton = { TextButton({ experimentalConfirmation = null }) { Text("Cancel") } })
    }
    if (restoreExperimentConfirmation) AlertDialog(
        onDismissRequest = { restoreExperimentConfirmation = false },
        title = { Text("Restore original override state?") },
        text = { Text("The original saved override will be restored byte-for-byte, or removed if no override existed. Restart Android Auto afterward.") },
        confirmButton = { TextButton({
            restoreExperimentConfirmation = false
            onExperimentalRestore()
        }) { Text("Restore baseline") } },
        dismissButton = { TextButton({ restoreExperimentConfirmation = false }) { Text("Cancel") } })
    if (probeConfirmation) AlertDialog(onDismissRequest = { probeConfirmation = false },
        title = { Text("Arm read-only getter probe?") },
        text = { Text("Only the selected getter will be traced for this exact Android Auto build. No value will be changed. A new Android Auto process is required. Confirm only while parked and not projecting.") },
        confirmButton = {
            TextButton({
                probeConfirmation = false
                onProbe(identifier, true)
            }) { Text("Arm probe") }
        },
        dismissButton = { TextButton({ probeConfirmation = false }) { Text("Cancel") } })
}

@Composable private fun SettingsScreen(state: ManagerUiState, model: ManagerViewModel,
    theme: ThemeMode, onTheme: (ThemeMode) -> Unit,
    accent: AccentPalette, onAccent: (AccentPalette) -> Unit,
    page: SettingsPage, onPage: (SettingsPage) -> Unit,
    advancedAccess: Boolean, onAdvancedAccess: (Boolean) -> Unit) {
    var restoreConfirm by remember { mutableStateOf(false) }; val context=LocalContext.current
    // Read the installed package metadata: never pin a preflight label in UI source.
    val appVersionName = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "unknown"
    }
    val backupExport=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){ uri->uri?.let { context.contentResolver.openOutputStream(it)?.use { out->out.write(model.exportDynamicJson().toByteArray()) } } }
    val backupImport=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){ uri->uri?.let { context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader->model.importDynamicJson(reader.readText()) } } }
    when(page){
        SettingsPage.DIAGNOSTICS -> DiagnosticsScreen(state, {onPage(SettingsPage.ROOT)},
            {onPage(SettingsPage.CATALOG)}, { model.deepResolveInconclusive() },
            model::setVideoTimingProtocolTrace, advancedAccess, onAdvancedAccess,
            model::pauseAllAdvanced, model::restoreAllAdvanced,
            model::setDoubleGetterProbe)
        SettingsPage.CATALOG -> Column(Modifier.fillMaxSize()){ SubpageHeader("Full catalog", "Every retained discovery", {onPage(SettingsPage.DIAGNOSTICS)}); CatalogScreen(state,model,advancedAccess) }
        SettingsPage.ABOUT -> AboutScreen(appVersionName) { onPage(SettingsPage.ROOT) }
        else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)){
            item{ScreenTitle("Settings", "Appearance, data and engineering tools")}
            item{SectionLabel("Appearance")}
            item{Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)){Column{
                AnchoredPreferenceDropdown(Icons.Default.DarkMode, "Theme", "Choose the app theme mode", listOf("Follow system", "Light", "Dark"), theme.ordinal) { onTheme(ThemeMode.entries[it]) }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                AnchoredPreferenceDropdown(Icons.Default.Palette, "Accent color", "Choose the app accent palette", listOf("Lab Green", "Material You"), accent.ordinal) { onAccent(AccentPalette.entries[it]) }
            }}}
            item{SectionLabel("Data")}
            item{Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)){Column{SettingsRow(Icons.Default.Upload,"Export normal overrides","Active and suspended only; excludes Advanced and Experimental records"){backupExport.launch("aa-experiments-overrides.json")}; HorizontalDivider(); SettingsRow(Icons.Default.Download,"Import normal overrides","Matching build only; blocked while Advanced or Experimental records exist"){backupImport.launch(arrayOf("application/json","text/plain"))}; HorizontalDivider(); SettingsRow(Icons.Default.Restore,"Restore all overrides","Also deletes Advanced and Experimental records and saved baselines",enabled=state.dynamicOverrides.isNotEmpty() || state.suspendedOverrides.isNotEmpty() || state.advancedRecords.isNotEmpty() || state.experimentalSnapshot != null){restoreConfirm=true}}}}
            item{SectionLabel("Tools")}
            item{Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)){SettingsRow(Icons.Default.BugReport,"Diagnostics","Runtime, resolver and full catalog"){onPage(SettingsPage.DIAGNOSTICS)}}}
            item{SectionLabel("About")}
            item{Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)){Row(Modifier.fillMaxWidth().clickable{onPage(SettingsPage.ABOUT)}.padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){Image(painterResource(R.drawable.app_icon),"AA Experiments icon",Modifier.size(42.dp));Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text("AA Experiments",fontWeight=FontWeight.Medium);Text("Version $appVersionName · HighwindBR",style=MaterialTheme.typography.bodySmall)};Icon(Icons.Default.ChevronRight,null)}}}
        }
    }
    if(restoreConfirm) AlertDialog({restoreConfirm=false},confirmButton={TextButton({model.restoreAllDynamic();restoreConfirm=false}){Text("Restore")}},dismissButton={TextButton({restoreConfirm=false}){Text("Cancel")}},title={Text("Restore all overrides?")},text={Text("This permanently removes all active and suspended normal overrides, Experimental records and Advanced baseline snapshots. Export normal overrides does NOT back up Advanced or Experimental records, so those baselines cannot be recovered from that JSON. Continue only if you intend to discard them all.")})
}

@Composable private fun SectionLabel(text: String) = Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
@Composable private fun SubpageHeader(title: String, subtitle: String? = null, onBack: () -> Unit) { Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }; Column { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) } } } }
@Composable private fun AboutScreen(versionName: String, onBack: () -> Unit) { Column(Modifier.fillMaxSize()) { SubpageHeader("About", null, onBack); HorizontalDivider(); LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) { item { Image(painterResource(R.drawable.app_icon), "AA Experiments icon", Modifier.size(104.dp)) }; item { Text("AA Experiments", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }; item { Text("Version $versionName", style = MaterialTheme.typography.bodyMedium) }; item { Text("Independent Android Auto experiment inspector and LSPosed runtime manager. Developed by HighwindBR.", style = MaterialTheme.typography.bodyLarge) }; item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { TechnicalBlock("Package", "io.github.highwindbr.aaxp"); TechnicalBlock("Runtime API", "LSPosed API 101"); Text("This project is not affiliated with Google, Android Auto or LSPosed.", style = MaterialTheme.typography.bodySmall) } } } } } }

private class TouchMenuPositionProvider(
    private val pressOffset: IntOffset,
    private val selectedIndex: Int,
    private val marginPx: Int,
    private val itemHeightPx: Int
) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val touchX = anchorBounds.left + pressOffset.x
        val touchY = anchorBounds.top + pressOffset.y
        val desiredX = touchX - popupContentSize.width / 2
        val desiredY = touchY - selectedIndex * itemHeightPx - itemHeightPx / 2
        return IntOffset(
            desiredX.coerceIn(marginPx, (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx)),
            desiredY.coerceIn(marginPx, (windowSize.height - popupContentSize.height - marginPx).coerceAtLeast(marginPx))
        )
    }
}

@Composable private fun AnchoredPreferenceDropdown(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    summary: String,
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var pressOffset by remember { mutableStateOf(IntOffset.Zero) }
    val safeIndex = selectedIndex.coerceIn(items.indices)
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val margin = with(density) { 8.dp.roundToPx() }
    val itemHeight = with(density) { 52.dp.roundToPx() }
    fun open() { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); expanded = true }
    Box {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "$title, ${items[safeIndex]}. Double tap to select another option."
                    onClick { pressOffset = IntOffset.Zero; open(); true }
                }
                .pointerInput(items, safeIndex) { detectTapGestures { point -> pressOffset = IntOffset(point.x.toInt(), point.y.toInt()); open() } }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, title, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontWeight = FontWeight.Medium)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Text(items[safeIndex], modifier = Modifier.widthIn(min = 88.dp, max = 128.dp), textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }
        if (expanded) Popup(
            popupPositionProvider = TouchMenuPositionProvider(pressOffset, safeIndex, margin, itemHeight),
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = true)
        ) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 6.dp, shadowElevation = 6.dp) {
                Column(Modifier.width(if ((items.maxOfOrNull { it.length } ?: 0) > 11) 204.dp else 184.dp).padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items.forEachIndexed { index, text ->
                        val selected = index == safeIndex
                        Surface(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { stateDescription = if (selected) "Selected" else "Not selected" }.clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelected(index)
                                expanded = false
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                        ) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (selected) Icon(Icons.Default.Check, null, Modifier.size(22.dp)) else Spacer(Modifier.width(22.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(text, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text(subtitle, style = MaterialTheme.typography.bodySmall) }; Icon(Icons.Default.ChevronRight, null)
    }
}

@Composable private fun DiagnosticsScreen(
    state: ManagerUiState,
    onBack: () -> Unit,
    onCatalog: () -> Unit,
    onDeepResolve: () -> Unit,
    onVideoTimingTrace: (Boolean) -> Unit,
    advancedAccess: Boolean,
    onAdvancedAccess: (Boolean) -> Unit,
    onPauseAllAdvanced: () -> Unit,
    onRestoreAllAdvanced: () -> Unit,
    onDoubleGetterProbe: (String, Boolean) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var expanded by remember { mutableStateOf<String?>(null) }
    var developerToolsExpanded by rememberSaveable { mutableStateOf(false) }
    // An armed diagnostic is revealed on entry, but can still be collapsed.
    LaunchedEffect(state.videoTimingTraceEnabled, state.probeTargetKey) {
        if (state.videoTimingTraceEnabled || state.probeTargetKey != null)
            developerToolsExpanded = true
    }
    var restoreAdvancedConfirm by remember { mutableStateOf(false) }
    val runtime = runtimeReadiness(state.aa, state.framework, state.dynamicOverrides.size + if (state.experimentalSnapshot?.active == true && state.experimentalSnapshot.buildFingerprint == state.aa.buildFingerprintSha256) 1 else 0, state.profilePublished)
    fun copy(value: String) = clipboard.setText(AnnotatedString(value))
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SubpageHeader("Diagnostics", "Installation, resolver and runtime", onBack) }
        item { DiagnosticSection("Installation", "Android Auto ${state.aa.versionName ?: "not found"}", Icons.Default.Info, expanded == "installation", { expanded = if(expanded=="installation") null else "installation" }) {
            DiagnosticValue("Base APK", state.aa.basePath?.substringAfterLast('/')?.substringAfterLast('\\') ?: "Not available") { state.aa.basePath?.let(::copy) }
            DiagnosticValue("Base SHA-256", state.aa.baseSha256 ?: "Not observed") { state.aa.baseSha256?.let(::copy) }
        } }
        item { DiagnosticSection("DEX files", "${state.aa.dexSha256.size} files", Icons.Default.Storage, expanded == "dex", { expanded = if(expanded=="dex") null else "dex" }) { state.aa.dexSha256.forEach { (name, hash) -> DiagnosticValue(name, hash) { copy(hash) } } } }

        // Advanced access remains prominent. Historical probe tools only
        // appear when requested, or while an existing diagnostic is armed.
        item {
            val countActive = state.advancedRecords.count {
                it.active && it.buildFingerprint == state.aa.buildFingerprintSha256
            }
            DiagnosticSection("Advanced Overrides",
                if (advancedAccess) "Enabled · ${state.advancedCandidates.size} eligible" else "Disabled · tap to configure",
                Icons.Default.Security, expanded == "advanced",
                { expanded = if (expanded == "advanced") null else "advanced" }) {
                FilterToggle("Show Advanced in Experiments",
                    "Only eligible typed getters, marked ADVANCED. Enable once; edit from Experiments.",
                    advancedAccess, onAdvancedAccess)
                Text("Advanced mode changes editor access only. Disabling this switch does not pause an existing override.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                DiagnosticValue("Eligible this build", if (advancedAccess)
                    state.advancedCandidates.size.toString() else "Enable to load")
                DiagnosticValue("Stored Advanced records", state.advancedRecords.size.toString())
                DiagnosticValue("Active on current build", countActive.toString())
                DiagnosticValue("Published Advanced hooks", state.advancedPublishedCount.toString())
                if (state.advancedRecords.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onPauseAllAdvanced, enabled = countActive > 0) {
                            Text("Pause all")
                        }
                        OutlinedButton({ restoreAdvancedConfirm = true }) {
                            Text("Restore baselines")
                        }
                    }
                }
                Text("Unsupported symbols, safety-sensitive flags, unverified getter signatures and ambiguous mappings remain read-only. Upgrading Android Auto does not automatically reactivate old records.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            val traceArmed = state.videoTimingTraceEnabled || state.probeTargetKey != null
            OutlinedButton(
                onClick = { developerToolsExpanded = !developerToolsExpanded },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Icon(Icons.Default.BugReport, null)
                Spacer(Modifier.width(10.dp))
                Text(if (developerToolsExpanded) "Hide developer tools"
                    else if (traceArmed) "Developer tools · diagnostic active"
                    else "Show developer tools")
            }
        }
        if (developerToolsExpanded) {
        item {
            val supported = state.aa.runtimeTarget &&
                io.github.highwindbr.aaxp.runtime.VideoTimingProtocolTrace.isSupported(state.aa.baseSha256)
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Video Timing protocol trace", fontWeight = FontWeight.Bold)
                            Text("Read-only · inspected Android Auto 18.0 build",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(
                        when {
                            state.videoTimingTraceEnabled ->
                                "ARMED · restart Android Auto to install read-only hooks"
                            supported -> "OFF · no trace hooks requested"
                            else -> "Unavailable · requires the exact inspected Android Auto base APK"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.videoTimingTraceEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("Observe the HU protocol major/minor and the selected Video Timing Controller branch. This does not change the negotiated protocol, getter values, or vehicle state.",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(
                        onClick = { onVideoTimingTrace(!state.videoTimingTraceEnabled) },
                        enabled = state.framework.connected && (supported || state.videoTimingTraceEnabled),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (state.videoTimingTraceEnabled)
                            "Disable VTC trace" else "Enable VTC trace")
                    }
                    Text("LSPosed events: VTC_TRACE_INSTALLED, VTC_HU_PROTOCOL_OBSERVED, VTC_SELECTOR_INVOKED, VTC_BRANCH_SELECTED. Disable after testing; restart Android Auto to remove hooks.",
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        item {
            val supported = state.aa.runtimeTarget &&
                GetterProbePolicy.isInspectedDoubleBuild(state.aa.baseSha256)
            val armed = state.probeTargetKey
            val isDoubleArmed = armed in GetterProbePolicy.doubleCandidates
            DiagnosticSection("DOUBLE getter activity",
                when {
                    isDoubleArmed -> "Armed · restart Android Auto"
                    !supported -> "Unavailable for this AA build"
                    else -> "Read-only · 3 reviewed targets"
                },
                Icons.Default.Search, expanded == "doubleProbe",
                { expanded = if (expanded == "doubleProbe") null else "doubleProbe" }) {
                Text("Read-only diagnostic for three reviewed DOUBLE getters. This does not change returned values or normal preferences.",
                    style = MaterialTheme.typography.bodySmall)
                if (!supported) Text(
                    "This probe is restricted to the inspected Android Auto 18.0 base APK.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (armed != null && !isDoubleArmed) Text(
                    "Another read-only probe is armed. Disarm it before selecting a DOUBLE getter.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                GetterProbePolicy.doubleCandidates.keys.forEach { key ->
                    val selected = armed == key
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(key, fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall)
                        OutlinedButton({ onDoubleGetterProbe(key, !selected) },
                            enabled = supported && state.framework.connected &&
                                (armed == null || selected)) {
                            Text(if (selected) "Disarm read-only probe" else "Arm read-only probe")
                        }
                    }
                }
                Text("Only one getter per session. After arming, restart Android Auto and connect to Open Headunit. Inspect LSPosed module events PROBE_HOOK_INSTALLED and PROBE_INVOKED. If not invoked, disarm and try another. Do not set a DOUBLE override for this test.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        }
        item { DiagnosticSection("Resolver", "${state.aa.identifierCount} identifiers · ${state.aa.editableCount} editable", Icons.Default.Search, expanded == "resolver", { expanded = if(expanded=="resolver") null else "resolver" }) {
            DiagnosticCount("Modified", state.dynamicOverrides.size)
            DiagnosticValue("Discovery errors", state.aa.discoveryErrorCount.toString())
            state.resolverRun?.let { run ->
                DiagnosticSectionTitle("Compatibility")
                DiagnosticCount("Preserved mappings", run.preserved)
                DiagnosticCount("Structurally rediscovered", run.rediscovered)
                DiagnosticCount("Suspended as ambiguous", run.suspended)
                DiagnosticCount("New keys", run.newKeys)
                if (run.deepResolved + run.deepInconclusive + run.deepDeferred + state.deepResolvePending > 0) {
                    DiagnosticSectionTitle("Directed deep resolve")
                    DiagnosticCount("Resolved", run.deepResolved)
                    DiagnosticCount("Still inconclusive", run.deepInconclusive)
                    DiagnosticCount("Deferred to a later scan", run.deepDeferred)
                    DiagnosticCount("Pending", state.deepResolvePending)
                }
                if (run.targetedSemanticEndpoint + run.targetedBudgetExhausted + run.targetedSemanticCorridorExhausted + run.targetedFallbackBudgetExhausted + run.targetedSharedFanout + run.targetedTechnicalOnly + run.targetedNoConsumer + run.targetedGenericOnly + run.targetedMultipleEndpoints + run.targetedValueFlowNotProven > 0) {
                    DiagnosticSectionTitle("Targeted outcomes")
                    DiagnosticCount("Semantic endpoint found", run.targetedSemanticEndpoint)
                    DiagnosticCount("Budget exhausted", run.targetedBudgetExhausted)
                    DiagnosticCount("Semantic corridor exhausted", run.targetedSemanticCorridorExhausted)
                    DiagnosticCount("Fallback budget exhausted", run.targetedFallbackBudgetExhausted)
                    DiagnosticCount("Shared infrastructure fan-out", run.targetedSharedFanout)
                    DiagnosticCount("Only technical consumers", run.targetedTechnicalOnly)
                    DiagnosticCount("No consumer found", run.targetedNoConsumer)
                    DiagnosticCount("Generic runtime path only", run.targetedGenericOnly)
                    DiagnosticCount("Multiple semantic endpoints", run.targetedMultipleEndpoints)
                    DiagnosticCount("Value flow not proven", run.targetedValueFlowNotProven)
                    if (run.targetedValueFlowNotProven > 0) {
                        DiagnosticSectionTitle("Value-flow limitations")
                        DiagnosticCount("Result not captured", run.flowResultNotCaptured)
                        DiagnosticCount("Captured then overwritten", run.flowCapturedThenOverwritten)
                        DiagnosticCount("Captured but unused", run.flowCapturedButUnused)
                        DiagnosticCount("Unsupported opcode", run.flowUnsupportedOpcode)
                        DiagnosticCount("Origin ambiguous at CFG join", run.flowCfgOriginAmbiguous)
                        DiagnosticCount("Unclassified callsite", run.flowUnknown)
                    }
                }
            }
            OutlinedButton(onDeepResolve, enabled = state.aa.runtimeTarget && (state.deepResolveRunning || (!state.loading && state.deepResolvePending > 0))) {
                Text(if (state.deepResolveRunning) "Cancel after current" else "Deep resolve inconclusive")
            }
            TextButton(onCatalog) { Text("Open full catalog") }
        } }
        item { DiagnosticSection("Runtime", runtime.label, Icons.Default.PlayCircle, expanded == "runtime", { expanded = if(expanded=="runtime") null else "runtime" }) {
            DiagnosticValue("Readiness", runtime.kind.name)
            DiagnosticValue("Scope", state.framework.scope.joinToString().ifBlank { "Not observed" })
            DiagnosticValue("Active overrides", state.dynamicOverrides.size.toString())
            DiagnosticValue("Suspended overrides", state.suspendedOverrides.size.toString())
            Text(runtime.detail, style = MaterialTheme.typography.bodySmall)
            Text("Structured LSPosed events: PROFILE_ACCEPTED, HOOK_INSTALLED, OVERRIDE_RETURNED, HOOK_SUSPENDED. Return events are emitted once per value generation to avoid log spam.", style = MaterialTheme.typography.bodySmall)
        } }
        item { DiagnosticSection("Remote preferences", if(state.framework.connected) "Connected" else "Disconnected", Icons.Default.Security, expanded == "prefs", { expanded = if(expanded=="prefs") null else "prefs" }) { DiagnosticValue("Dynamic profile", if(state.profilePublished) "Published" else "Not published"); DiagnosticValue("Framework API", state.framework.api?.toString() ?: "Not observed") } }
        item { Text("Values read at startup may require the Android Auto process to restart. Never reconnect or restart it while driving.", style = MaterialTheme.typography.bodySmall) }
    }
    if (restoreAdvancedConfirm) AlertDialog(
        onDismissRequest = { restoreAdvancedConfirm = false },
        title = { Text("Restore current-build Advanced baselines?") },
        text = { Text("Restore the exact previous normal override, or its absence. Records from other Android Auto builds will remain suspended. Restart Android Auto afterward.") },
        confirmButton = { TextButton({
            restoreAdvancedConfirm = false
            onRestoreAllAdvanced()
        }) { Text("Restore baselines") } },
        dismissButton = { TextButton({ restoreAdvancedConfirm = false }) { Text("Cancel") } }
    )
}

@Composable private fun DiagnosticSection(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, expanded: Boolean, onToggle: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) { Column { Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodySmall) }; Icon(if(expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if(expanded) "Collapse $title" else "Expand $title") }; if(expanded) { HorizontalDivider(); Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) } } }
}
@Composable private fun DiagnosticValue(label: String, value: String, onCopy: (() -> Unit)? = null) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary); Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = if (value.length > 20 || label.contains("SHA") || label.contains("APK")) FontFamily.Monospace else FontFamily.Default) }; onCopy?.let { IconButton(it) { Icon(Icons.Default.ContentCopy, "Copy $label") } } } }
@Composable private fun DiagnosticCount(label: String, value: Int) { if (value != 0) DiagnosticValue(label, value.toString()) }
