package io.github.highwindbr.aaxp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import io.github.highwindbr.aaxp.core.AdvancedOverridePolicy
import io.github.highwindbr.aaxp.core.AdvancedOverrideRecord
import io.github.highwindbr.aaxp.discovery.DiscoveredIdentifier
import io.github.highwindbr.aaxp.discovery.DiscoveredType
import io.github.highwindbr.aaxp.discovery.SafetyPolicy

/**
 * Independent editor: the normal Experiments screen is never made unsafe.
 * A second confirmation explicitly requires typing APPLY before saving.
 */
@Composable
fun AdvancedOverrideEditor(
    identifier: DiscoveredIdentifier,
    record: AdvancedOverrideRecord?,
    build: String?,
    publishedCount: Int,
    feedback: String?,
    onSave: (DiscoveredIdentifier, String) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRestore: (String) -> Unit,
    onClose: () -> Unit
) {
    val issue = AdvancedOverridePolicy.issue(identifier)
    val elevatedRisk = SafetyPolicy.elevatedRiskReason(identifier.key)
    val requiredConfirmation = if (elevatedRisk != null) "PARKED APPLY" else "APPLY"
    val belongsToBuild = record == null || record.buildFingerprint == build
    var raw by remember(identifier.key, record?.value) {
        mutableStateOf(record?.value ?: identifier.compiledDefault.orEmpty())
    }
    var phase by remember(identifier.key) { mutableIntStateOf(0) }
    var acknowledgement by remember(identifier.key) { mutableStateOf("") }
    var restoreWarning by remember(identifier.key) { mutableStateOf(false) }
    val expected = identifier.inferredType
    val selected = if (expected == DiscoveredType.BOOLEAN) {
        if (raw == "true" || raw == "false") raw else "false"
    } else raw

    Dialog(onDismissRequest = onClose) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.89f)) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("Advanced Overrides", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                Text("Unsafe experimental getter interception", color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(identifier.key, fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider()
                    Text("Type: ${expected.name} · ${identifier.resolution}", fontWeight = FontWeight.Medium)
                    Text("Compiled default: ${identifier.compiledDefault ?: "not inferred"}")
                    Text("Consumer: ${identifier.runtimeConsumerStatus} · Confidence: ${identifier.confidence}")
                    Text("Normal editor: ${if (identifier.editable) "editable" else "read-only in the stored catalog"}")
                    if (!identifier.editable) Text(
                        "The normal editor's catalog verdict may predate the Advanced policy. Advanced eligibility is checked separately.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("Advanced eligibility: ${if (issue == null) "getter and safety checks passed" else "read-only"}")
                    Text("Getter: ${identifier.getterCandidates.singleOrNull()?.let {
                        "${it.className}->${it.methodName}${it.descriptor}"
                    } ?: "not uniquely resolved"}", fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall)
                    Text("Previous stored override: ${record?.previousOverride ?: "Absent (or not recorded yet)"}")
                    if (record != null) {
                        Text("Experimental value: ${record.value} · ${if (record.active) "ACTIVE" else "PAUSED"}")
                        if (!belongsToBuild) Text(
                            "SUSPENDED — saved for a different Android Auto build. Never reapplied automatically.",
                            color = MaterialTheme.colorScheme.error)
                    }
                    if (issue != null) Text("READ ONLY — $issue", color = MaterialTheme.colorScheme.error)
                    if (elevatedRisk != null && issue == null) {
                        HorizontalDivider()
                        Text("ELEVATED RISK · PARKED-MODE TIMING",
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Text(elevatedRisk, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                        Text("This flag configures a feature in parked mode; it does NOT change or spoof parking detection. Test only with a local head-unit emulator or while parked.")
                    }
                    if (expected == DiscoveredType.BOOLEAN) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = selected == "false", onClick = { raw = "false" },
                                label = { Text("False") })
                            FilterChip(selected = selected == "true", onClick = { raw = "true" },
                                label = { Text("True") })
                        }
                    } else {
                        OutlinedTextField(raw, { raw = it }, label = {
                            Text(if (expected == DiscoveredType.LONG) "Raw signed 64-bit integer"
                                else if (expected == DiscoveredType.DOUBLE) "Finite decimal number"
                                else "Raw string")
                        }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
                    }
                    if (issue == null && belongsToBuild)
                        Text("Unverified value: may be ignored, crash projection, break media, or require clearing Android Auto data.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    Text("State: ${if (publishedCount > 0) "runtime profile published; restart AA" else "no advanced hook published"}",
                        style = MaterialTheme.typography.labelSmall)
                    feedback?.takeIf { it.contains("Advanced", true) }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    if (record != null && belongsToBuild) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = {
                                if (record.active) onPause(record.key) else onResume(record.key)
                            }) { Text(if (record.active) "Pause" else "Resume") }
                            OutlinedButton({ restoreWarning = true }) { Text("Restore baseline") }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClose) { Text("Close") }
                    Button(onClick = { phase = 1 }, enabled = issue == null && belongsToBuild && build != null) {
                        Text("Review raw override")
                    }
                }
            }
        }
    }
    if (phase == 1) AlertDialog(
        onDismissRequest = { phase = 0 },
        title = { Text(if (elevatedRisk == null) "Confirm Advanced override" else "Elevated-risk override — review") },
        text = { Text("Key: ${identifier.key}\nType: ${expected.name}\nCompiled default: ${identifier.compiledDefault ?: "unknown"}\nNew value: ${if (expected == DiscoveredType.STRING) "<string: ${raw.length} chars>" else selected}\nThe accepted domain and runtime behavior are not proven. Wrong values may break Android Auto." + (if (elevatedRisk != null) "\n\nELEVATED PARKED-MODE RISK: $elevatedRisk" else "")) },
        confirmButton = { TextButton({
            if (elevatedRisk == null) {
                phase = 0
                onSave(identifier, selected)
            } else {
                phase = 2
                acknowledgement = ""
            }
        }) { Text(if (elevatedRisk == null) "Apply Advanced value" else "Continue") } },
        dismissButton = { TextButton({ phase = 0 }) { Text("Cancel") } }
    )
    if (phase == 2) AlertDialog(
        onDismissRequest = { phase = 0 },
        title = { Text("Final confirmation") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This applies only to the identified getter and current AA build. A baseline will be preserved for restoration. Type $requiredConfirmation to proceed.")
                OutlinedTextField(acknowledgement, { acknowledgement = it },
                    label = { Text("Type $requiredConfirmation") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton({
                phase = 0
                onSave(identifier, selected)
            }, enabled = acknowledgement == requiredConfirmation) { Text("Apply advanced value") }
        },
        dismissButton = { TextButton({ phase = 0 }) { Text("Cancel") } }
    )
    if (restoreWarning) AlertDialog(
        onDismissRequest = { restoreWarning = false },
        title = { Text("Restore saved baseline?") },
        text = { Text("This restores the original normal override exactly, or its absence. It does not force the compiled default. Restart Android Auto afterward.") },
        confirmButton = { TextButton({
            restoreWarning = false
            onRestore(identifier.key)
        }) { Text("Restore original") } },
        dismissButton = { TextButton({ restoreWarning = false }) { Text("Cancel") } }
    )
}
