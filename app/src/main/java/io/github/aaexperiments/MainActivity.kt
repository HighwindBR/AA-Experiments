@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.aaexperiments

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
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
import io.github.aaexperiments.core.*
import io.github.aaexperiments.discovery.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private val model by viewModels<ManagerViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { ManagerRoot(model) } }
}

private enum class ThemeMode { SYSTEM, LIGHT, DARK }
private enum class AccentPalette { LAB_GREEN, MATERIAL_YOU }
private val AaGreen = Color(0xFF006D3F)
private val AaGreenDark = Color(0xFF72D99A)
private val ForceFalse = Color(0xFF415F91)
private val WarningAmber = Color(0xFF8A5300)

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
    var experimentQuery by rememberSaveable { mutableStateOf("") }
    var experimentModifiedOnly by rememberSaveable { mutableStateOf(false) }
    var experimentReviewedOnly by rememberSaveable { mutableStateOf(false) }
    var experimentTypeName by rememberSaveable { mutableStateOf<String?>(null) }
    val experimentListState = rememberLazyListState()
    val experiment = experimentKey?.let { key -> state.discovered.firstOrNull { it.key == key } }
    val context = LocalContext.current
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val payload = state.exportPayload
        if (uri != null && payload != null) context.contentResolver.openOutputStream(uri)?.use { it.write(payload.content.toByteArray()) }
        model.consumeExport()
    }
    LaunchedEffect(state.exportPayload) { state.exportPayload?.let { exporter.launch(it.fileName) } }
    experiment?.let { identifier ->
        BackHandler { model.clearDetails(); experimentKey = null }
        ExperimentDetailScreen(identifier, state.selectedDetails, state.dynamicOverrides[identifier.key], state.framework.connected && state.aa.runtimeTarget,
            { value -> model.setDynamicOverride(identifier, value) }, { model.loadDetails(identifier.key) }, { model.clearDetails(); experimentKey = null })
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
    Scaffold(
        topBar = { TopAppBar(title = { Row(verticalAlignment = Alignment.CenterVertically) { Image(painterResource(R.drawable.app_icon), null, Modifier.size(32.dp), contentScale = ContentScale.Fit); Spacer(Modifier.width(10.dp)); Text("AA Experiments", fontWeight = FontWeight.SemiBold) } }, actions = { if (tab == 0) IconButton(model::refreshInstallation) { Icon(Icons.Default.Refresh, "Refresh status") } }) },
        bottomBar = { NavigationBar { destinations.forEach { (label, icon, index) -> NavigationBarItem(tab == index, { tab = index }, { Icon(icon, label) }, label = { Text(label) }) } } }
    ) { padding -> Box(Modifier.padding(padding).fillMaxSize()) { when (tab) {
        0 -> StatusScreen(state, model)
        1 -> AdvancedScreen(state, model, experimentQuery, { experimentQuery = it }, experimentModifiedOnly, { experimentModifiedOnly = it }, experimentReviewedOnly, { experimentReviewedOnly = it }, experimentTypeName, { experimentTypeName = it }, experimentListState) { experimentKey = it.key }
        else -> SettingsScreen(state, model, theme, onTheme, accent, onAccent, settingsPage) { settingsPageName = it.name }
    } } }
    state.scanProgress?.let { progress -> ScanProgressDialog(progress, if (state.deepResolveRunning) ({ model.deepResolveInconclusive() }) else null) }
}

@Composable private fun StatusScreen(state: ManagerUiState, model: ManagerViewModel) {
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::importArchive) }
    val runtime = runtimeReadiness(state.aa, state.framework, state.dynamicOverrides.size, state.profilePublished)
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
        state.changes?.let { change -> Text("Since previous scan: ${change.added} added · ${change.removed} removed · ${change.changed} changed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    } } }
    item { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("Analysis", fontWeight = FontWeight.Bold); Text(if (state.profilePublished) "Dynamic profile published" else "Profile not published"); Text("Overrides apply only when the complete installed build fingerprint matches.", style = MaterialTheme.typography.bodySmall); if(state.suspendedOverrides.isNotEmpty()) Text("${state.suspendedOverrides.size} incompatible override(s) preserved but suspended.", color=MaterialTheme.colorScheme.error, style=MaterialTheme.typography.bodySmall) } } }
    item { OutlinedButton({ importer.launch(arrayOf("application/vnd.android.package-archive", "application/zip", "application/octet-stream")) }, Modifier.fillMaxWidth()) { Text("Import APK, APKM or APKS") } }
    }
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
                Text(title, style = MaterialTheme.typography.bodyLarge)
                progress.fraction?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                    ?: LinearProgressIndicator(Modifier.fillMaxWidth())
                progress.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (progress.current != null && progress.total != null) Text("${progress.current} of ${progress.total}", style = MaterialTheme.typography.labelMedium)
                Text("Keep AA Experiments open while the catalog is being prepared.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                onCancel?.let { TextButton(it, Modifier.align(Alignment.End)) { Text("Cancel after current") } }
            }
        }
    }
}

@Composable private fun ScreenTitle(title: String, subtitle: String? = null) { Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold); subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
@Composable private fun MiniStatus(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, ok: Boolean) { Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(22.dp), tint = if (ok) MaterialTheme.colorScheme.primary else WarningAmber); Spacer(Modifier.width(8.dp)); Column { Text(title, style = MaterialTheme.typography.labelMedium); Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium) } } } }
@Composable private fun RuntimeBanner(runtime: RuntimeReadiness) {
    val concise = when (runtime.kind) {
        RuntimeReadinessKind.READY_AWAITING_PROCESS -> "Ready — waiting for Android Auto"
        RuntimeReadinessKind.NO_OVERRIDES -> "Idle — no active overrides"
        else -> runtime.label
    }
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.PlayCircle, null, tint = if (runtime.warning) WarningAmber else MaterialTheme.colorScheme.primary); Spacer(Modifier.width(10.dp)); Column { Text("Runtime", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(concise, fontWeight = FontWeight.SemiBold); Text("Details in Settings → Diagnostics", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
}

@Composable private fun StatusDot(ok: Boolean) = Surface(Modifier.size(12.dp), shape = MaterialTheme.shapes.extraLarge, color = if (ok) AaGreen else WarningAmber) {}
@Composable private fun CompactStatusRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, tint: Color = MaterialTheme.colorScheme.primary) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(12.dp)); Text(title, Modifier.weight(1f), fontWeight = FontWeight.Medium); Text(value, style = MaterialTheme.typography.bodySmall) }
}

@Composable private fun StatusCard(title: String, value: String) = Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(title, fontWeight = FontWeight.Bold); Text(value) } }

@Composable private fun AdvancedScreen(
    state: ManagerUiState,
    model: ManagerViewModel,
    query: String,
    onQuery: (String) -> Unit,
    modifiedOnly: Boolean,
    onModifiedOnly: (Boolean) -> Unit,
    reviewedOnly: Boolean,
    onReviewedOnly: (Boolean) -> Unit,
    typeName: String?,
    onTypeName: (String?) -> Unit,
    listState: LazyListState,
    onOpen: (DiscoveredIdentifier) -> Unit
) {
    var filtersOpen by remember { mutableStateOf(false) }
    val type = typeName?.let { runCatching { DiscoveredType.valueOf(it) }.getOrNull() }
    val availableTypes = remember(state.discovered) { state.discovered.map { it.inferredType }.toSet() }
    val hasReviewed = remember(state.discovered) { state.discovered.any { it.semanticsReviewed } }
    LaunchedEffect(type, availableTypes) { if (type != null && type !in availableTypes) onTypeName(null) }
    LaunchedEffect(reviewedOnly, hasReviewed) { if (reviewedOnly && !hasReviewed) onReviewedOnly(false) }
    val filtered = state.discovered.filter { identifier ->
        (!modifiedOnly || identifier.key in state.dynamicOverrides) && (!reviewedOnly || identifier.semanticsReviewed) && (type == null || identifier.inferredType == type) &&
            (query.isBlank() || listOf(identifier.key, identifier.namespace.name, identifier.kind.name, identifier.inferredType.name,
                identifier.confidence.name, identifier.resolution.name, identifier.runtimeConsumerStatus.name,
                identifier.editabilityReason?.name.orEmpty(), identifier.reason.orEmpty(), identifier.metadata["reviewedSemantics"].orEmpty()).any { it.contains(query, true) })
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(query, onQuery, placeholder = { Text("Search experiments") }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { IconButton({ filtersOpen = true }) { Icon(Icons.Default.FilterList, "Filters") } }, singleLine = true, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 54.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Experiments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("${filtered.size} of ${state.aa.editableCount}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (it.startsWith("Import failed")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
        Text(when (state.saveState) { SaveState.SAVING -> "Saving…"; SaveState.SAVED -> "Saved"; SaveState.FAILED -> "Save failed"; else -> "" }, style = MaterialTheme.typography.labelSmall)
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(filtered, key = { it.key }) { identifier -> CompactExperimentCard(identifier, state.dynamicOverrides[identifier.key]) { onOpen(identifier) } }
        }
    }
    if (filtersOpen) ModalBottomSheet(onDismissRequest = { filtersOpen = false }) { Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Filter experiments", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Value type", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val choices = listOf<DiscoveredType?>(null) + listOf(DiscoveredType.BOOLEAN, DiscoveredType.INT, DiscoveredType.LONG, DiscoveredType.FLOAT, DiscoveredType.DOUBLE, DiscoveredType.STRING).filter { it in availableTypes }
            choices.forEach { choice -> FilterChip(selected = type == choice, onClick = { onTypeName(choice?.name) }, label = { Text(choice?.let { friendly(it.name) } ?: "All") }) }
        }
        FilterToggle("Modified", "Has a saved override", modifiedOnly, onModifiedOnly)
        if (hasReviewed) FilterToggle("Reviewed semantics", "Consumer meaning was manually audited", reviewedOnly, onReviewedOnly)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton({ onModifiedOnly(false); onReviewedOnly(false); onTypeName(null) }) { Text("Reset") }; Button({ filtersOpen=false }) { Text("Done") } }
        Spacer(Modifier.height(12.dp))
    } }
    if (state.detailsLoading) AlertDialog({}, confirmButton = {}, title = { Text("Loading evidence…") }, text = { LinearProgressIndicator(Modifier.fillMaxWidth()) })
}

@Composable private fun FilterToggle(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().clickable { onChecked(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text(subtitle, style = MaterialTheme.typography.bodySmall) }; Switch(checked, onChecked) } }

@Composable private fun CatalogScreen(state: ManagerUiState, model: ManagerViewModel) {
    var query by remember { mutableStateOf("") }; var includeUnknown by remember { mutableStateOf(false) }
    var kind by remember { mutableStateOf<IdentifierKind?>(null) }; var type by remember { mutableStateOf<DiscoveredType?>(null) }
    var resolution by remember { mutableStateOf<MappingResolution?>(null) }; var reviewed by remember { mutableStateOf(false) }
    var runtime by remember { mutableStateOf<RuntimeConsumerStatus?>(null) }; var editable by remember { mutableStateOf(false) }
    var modified by remember { mutableStateOf(false) }; var killSwitch by remember { mutableStateOf(false) }
    var exportOpen by remember { mutableStateOf(false) }; var filtersOpen by remember { mutableStateOf(false) }
    val activeQuery = CatalogQuery(query, editableOnly = editable, includeUnknown = includeUnknown, kind = kind, type = type, resolution = resolution,
        runtimeStatus = runtime, reviewedOnly = reviewed, killSwitchOnly = killSwitch, overrideKeys = if (modified) state.dynamicOverrides.keys else null)
    LaunchedEffect(state.aa.buildFingerprintSha256, query, includeUnknown, kind, type, resolution, reviewed, runtime, editable, modified, killSwitch, state.dynamicOverrides.keys) {
        delay(300); model.searchCatalog(activeQuery)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(query, { query = it }, label = { Text("Search the complete catalog") }, trailingIcon = { IconButton({ filtersOpen=true }) { Icon(Icons.Default.FilterList, "Filters") } }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton({ exportOpen = true }) { Text("Export discoveries") } }
        Text("Showing ${state.catalog.size} of ${state.catalogTotal}. Search and filters run against the complete database.", style = MaterialTheme.typography.labelSmall)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
            items(state.catalog, key = { it.key }) { item -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text(item.key, fontWeight = FontWeight.Medium); Text("${item.kind} · ${item.storageType} / ${item.semanticType} · ${item.resolution}${if (item.editable) " · Editable" else ""}", style = MaterialTheme.typography.labelSmall)
                TextButton({ model.loadDetails(item.key) }) { Text("View evidence") }
            } } }
            if (state.catalog.size < state.catalogTotal) item { OutlinedButton({ model.searchCatalog(activeQuery.copy(offset = state.catalog.size), append = true) }, Modifier.fillMaxWidth()) { Text("Load 500 more") } }
        }
    }
    if (state.detailsLoading) AlertDialog({}, confirmButton = {}, title = { Text("Loading evidence…") }, text = { LinearProgressIndicator(Modifier.fillMaxWidth()) })
    state.selectedDetails?.let { IdentifierDetailsDialog(it, model::clearDetails) }
    if (exportOpen) ExportDialog({ exportOpen = false }, model::requestCatalogExport)
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

@Composable private fun CompactExperimentCard(identifier: DiscoveredIdentifier, override: TypedOverride?, onOpen: () -> Unit) {
    val stateLabel = when { override == null -> "DEFAULT"; identifier.inferredType == DiscoveredType.BOOLEAN -> override.encodedValue.uppercase(); else -> "CUSTOM" }
    val stateColor = when (override?.encodedValue) { "true" -> AaGreen; "false" -> ForceFalse; else -> MaterialTheme.colorScheme.surfaceVariant }
    val stateContent = if (override == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(technicalDisplay(identifier.key), modifier = Modifier.semantics { contentDescription = identifier.key }, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = if (override != null) FontWeight.SemiBold else FontWeight.Medium, fontFamily = FontFamily.Monospace)
            Text(friendly(identifier.inferredType.name), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (override != null) "Modified" else "", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Surface(modifier = Modifier.width(92.dp), color = stateColor, contentColor = stateContent, shape = MaterialTheme.shapes.medium) { Text(stateLabel, Modifier.fillMaxWidth().padding(vertical = 4.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                Icon(Icons.Default.ChevronRight, "Open experiment", Modifier.size(22.dp))
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

@Composable private fun ExperimentDetailScreen(identifier: DiscoveredIdentifier, loaded: DiscoveredIdentifier?, override: TypedOverride?, connected: Boolean, onChange: (String?) -> Unit, onLoadEvidence: () -> Unit, onBack: () -> Unit) {
    var input by remember(identifier.key, override) { mutableStateOf(override?.encodedValue.orEmpty()) }
    var evidenceExpanded by remember(identifier.key) { mutableStateOf(false) }
    var rawExpanded by remember(identifier.key) { mutableStateOf(false) }
    var advancedExpanded by remember(identifier.key) { mutableStateOf(false) }
    var rawResolverExpanded by remember(identifier.key) { mutableStateOf(false) }
    val details = loaded?.takeIf { it.key == identifier.key } ?: identifier
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 64.dp).padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to experiments") }
            Text("Experiment details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        }
        HorizontalDivider()
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(technicalDisplay(identifier.key), modifier = Modifier.semantics { contentDescription = identifier.key }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onBackground) }
            item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { MetaChip(friendly(identifier.inferredType.name), MaterialTheme.colorScheme.secondaryContainer); MetaChip(friendly(identifier.resolution.name), MaterialTheme.colorScheme.tertiaryContainer); if (override != null) MetaChip("Modified", MaterialTheme.colorScheme.primaryContainer); MetaChip(friendly(identifier.runtimeConsumerStatus.name), MaterialTheme.colorScheme.surfaceVariant) } }
            item { identifier.metadata["reviewedSemantics"]?.let { Text(it, style = MaterialTheme.typography.bodyMedium) } ?: Text("Configure the runtime value returned by this experiment.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Values", fontWeight = FontWeight.Bold)
                ValueRow("Default value", identifier.compiledDefault ?: "Not inferred")
                ValueRow("Runtime observed", "See LSPosed events")
                ValueRow("Effective value", override?.encodedValue ?: identifier.compiledDefault ?: "Default", override != null)
                ValueRow("Occurrences", identifier.occurrences.size.toString())
                ValueRow("Runtime consumer", friendly(identifier.runtimeConsumerStatus.name))
            } } }
            item { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Override value", fontWeight = FontWeight.Bold)
                if (identifier.inferredType == DiscoveredType.BOOLEAN) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { listOf<String?>(null, "false", "true").forEachIndexed { index, value ->
                        val selectedColor = when (value) { "true" -> AaGreen; "false" -> ForceFalse; else -> MaterialTheme.colorScheme.surfaceVariant }
                        val selectedContent = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                        SegmentedButton((override?.encodedValue) == value || (override == null && value == null), { onChange(value) }, enabled = connected, shape = SegmentedButtonDefaults.itemShape(index, 3), colors = SegmentedButtonDefaults.colors(activeContainerColor = selectedColor, activeContentColor = selectedContent)) { Text(when(value){null->"Default";"false"->"False";else->"True"}) }
                    } }
                    Text(when(override?.encodedValue) { "true" -> "Force true: always return true for this experiment."; "false" -> "Force false: always return false for this experiment."; else -> "Default: use the value selected by Android Auto." }, style = MaterialTheme.typography.bodySmall)
                } else {
                    OutlinedTextField(input, { input = it }, label = { Text("${friendly(identifier.inferredType.name)} value") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ onChange(input) }, enabled = connected && (identifier.inferredType == DiscoveredType.STRING || input.isNotBlank())) { Text("Apply") }; OutlinedButton({ onChange(null) }, enabled = connected && override != null) { Text("Default") } }
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

@Composable private fun ValueRow(label: String, value: String, accent: Boolean = false) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) { Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); Text(value, Modifier.weight(1f), color = if (accent) AaGreen else MaterialTheme.colorScheme.onSurface, fontWeight = if (accent) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis) } }

@Composable private fun IdentifierDetailsDialog(identifier: DiscoveredIdentifier, onDismiss: () -> Unit) {
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
}

@Composable private fun SettingsScreen(state: ManagerUiState, model: ManagerViewModel, theme: ThemeMode, onTheme: (ThemeMode) -> Unit, accent: AccentPalette, onAccent: (AccentPalette) -> Unit, page: SettingsPage, onPage: (SettingsPage) -> Unit) {
    var restoreConfirm by remember { mutableStateOf(false) }; val context=LocalContext.current
    val backupExport=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){ uri->uri?.let { context.contentResolver.openOutputStream(it)?.use { out->out.write(model.exportDynamicJson().toByteArray()) } } }
    val backupImport=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){ uri->uri?.let { context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader->model.importDynamicJson(reader.readText()) } } }
    when(page){
        SettingsPage.DIAGNOSTICS -> DiagnosticsScreen(state, {onPage(SettingsPage.ROOT)}, {onPage(SettingsPage.CATALOG)}, { model.deepResolveInconclusive() })
        SettingsPage.CATALOG -> Column(Modifier.fillMaxSize()){ SubpageHeader("Full catalog", "Every retained discovery", {onPage(SettingsPage.DIAGNOSTICS)}); CatalogScreen(state,model) }
        SettingsPage.ABOUT -> AboutScreen { onPage(SettingsPage.ROOT) }
        else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)){
            item{ScreenTitle("Settings", "Appearance, data and engineering tools")}
            item{SectionLabel("Appearance")}
            item{Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)){Column{
                AnchoredPreferenceDropdown(Icons.Default.DarkMode, "Theme", "Choose the app theme mode", listOf("Follow system", "Light", "Dark"), theme.ordinal) { onTheme(ThemeMode.entries[it]) }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                AnchoredPreferenceDropdown(Icons.Default.Palette, "Accent color", "Choose the app accent palette", listOf("Lab Green", "Material You"), accent.ordinal) { onAccent(AccentPalette.entries[it]) }
            }}}
            item{SectionLabel("Data")}
            item{Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)){Column{SettingsRow(Icons.Default.Upload,"Export overrides","Save current overrides as JSON"){backupExport.launch("aa-experiments-overrides.json")}; HorizontalDivider(); SettingsRow(Icons.Default.Download,"Import overrides","Load a matching JSON backup"){backupImport.launch(arrayOf("application/json","text/plain"))}; HorizontalDivider(); SettingsRow(Icons.Default.Restore,"Restore all overrides","Return experiments to their defaults",enabled=state.dynamicOverrides.isNotEmpty()){restoreConfirm=true}}}}
            item{SectionLabel("Tools")}
            item{Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)){SettingsRow(Icons.Default.BugReport,"Diagnostics","Runtime, resolver and full catalog"){onPage(SettingsPage.DIAGNOSTICS)}}}
            item{SectionLabel("About")}
            item{Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)){Row(Modifier.fillMaxWidth().clickable{onPage(SettingsPage.ABOUT)}.padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){Image(painterResource(R.drawable.app_icon),"AA Experiments icon",Modifier.size(42.dp));Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text("AA Experiments",fontWeight=FontWeight.Medium);Text("Version 1.0.0-beta1 · HighwindBR",style=MaterialTheme.typography.bodySmall)};Icon(Icons.Default.ChevronRight,null)}}}
        }
    }
    if(restoreConfirm) AlertDialog({restoreConfirm=false},confirmButton={TextButton({model.restoreAllDynamic();restoreConfirm=false}){Text("Restore")}},dismissButton={TextButton({restoreConfirm=false}){Text("Cancel")}},title={Text("Restore all overrides?")},text={Text("This removes every dynamic override and returns Android Auto to system defaults.")})
}

@Composable private fun SectionLabel(text: String) = Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
@Composable private fun SubpageHeader(title: String, subtitle: String? = null, onBack: () -> Unit) { Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }; Column { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) } } } }
@Composable private fun AboutScreen(onBack: () -> Unit) { Column(Modifier.fillMaxSize()) { SubpageHeader("About", null, onBack); HorizontalDivider(); LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) { item { Image(painterResource(R.drawable.app_icon), "AA Experiments icon", Modifier.size(104.dp)) }; item { Text("AA Experiments", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }; item { Text("Version 1.0.0-beta1", style = MaterialTheme.typography.bodyMedium) }; item { Text("Independent Android Auto experiment inspector and LSPosed runtime manager. Developed by HighwindBR.", style = MaterialTheme.typography.bodyLarge) }; item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { TechnicalBlock("Package", "io.github.highwindbr.aaxp"); TechnicalBlock("Runtime API", "LSPosed API 101"); Text("This project is not affiliated with Google, Android Auto or LSPosed.", style = MaterialTheme.typography.bodySmall) } } } } } }

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

@Composable private fun DiagnosticsScreen(state: ManagerUiState, onBack: () -> Unit, onCatalog: () -> Unit, onDeepResolve: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var expanded by remember { mutableStateOf<String?>(null) }
    val runtime = runtimeReadiness(state.aa, state.framework, state.dynamicOverrides.size, state.profilePublished)
    fun copy(value: String) = clipboard.setText(AnnotatedString(value))
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SubpageHeader("Diagnostics", "Installation, resolver and runtime", onBack) }
        item { DiagnosticSection("Installation", "Android Auto ${state.aa.versionName ?: "not found"}", Icons.Default.Info, expanded == "installation", { expanded = if(expanded=="installation") null else "installation" }) {
            DiagnosticValue("Base APK", state.aa.basePath?.substringAfterLast('/')?.substringAfterLast('\\') ?: "Not available") { state.aa.basePath?.let(::copy) }
            DiagnosticValue("Base SHA-256", state.aa.baseSha256 ?: "Not observed") { state.aa.baseSha256?.let(::copy) }
        } }
        item { DiagnosticSection("DEX files", "${state.aa.dexSha256.size} files", Icons.Default.Storage, expanded == "dex", { expanded = if(expanded=="dex") null else "dex" }) { state.aa.dexSha256.forEach { (name, hash) -> DiagnosticValue(name, hash) { copy(hash) } } } }
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
}

@Composable private fun DiagnosticSection(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, expanded: Boolean, onToggle: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) { Column { Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodySmall) }; Icon(if(expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if(expanded) "Collapse $title" else "Expand $title") }; if(expanded) { HorizontalDivider(); Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) } } }
}
@Composable private fun DiagnosticValue(label: String, value: String, onCopy: (() -> Unit)? = null) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary); Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = if (value.length > 20 || label.contains("SHA") || label.contains("APK")) FontFamily.Monospace else FontFamily.Default) }; onCopy?.let { IconButton(it) { Icon(Icons.Default.ContentCopy, "Copy $label") } } } }
@Composable private fun DiagnosticCount(label: String, value: Int) { if (value != 0) DiagnosticValue(label, value.toString()) }
