package com.paisetrail.app.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.work.WorkInfo
import com.paisetrail.app.ui.components.PaisaCard
import com.paisetrail.app.ui.theme.ChipShape
import com.paisetrail.app.ui.theme.PaisaSpacing
import com.paisetrail.app.ui.theme.PaisaTheme
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlinx.coroutines.launch

/** Sectioned PaisaCards with eyebrow labels (spec §4.9): Capture, Data, Manage, and a collapsed
 * Developer section for debug tools. Each row is a 36dp icon tile + title/caption. */
@Composable
fun SettingsScreen(
    onNavigateToRawEventsDebug: () -> Unit,
    onNavigateToReviewQueue: () -> Unit,
    onNavigateToCategoryManagement: () -> Unit,
    onNavigateToRandomTransaction: () -> Unit,
    onNavigateToManualTransaction: () -> Unit,
    onNavigateToBankPatternManagement: () -> Unit,
    onNavigateToMerchantManagement: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val backfillState by viewModel.backfillState.collectAsState()

    var smsGranted by remember { mutableStateOf(hasSmsPermissions(context)) }
    var listenerEnabled by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var batteryExempted by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                smsGranted = hasSmsPermissions(context)
                listenerEnabled = isNotificationListenerEnabled(context)
                overlayGranted = Settings.canDrawOverlays(context)
                batteryExempted = isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { smsGranted = hasSmsPermissions(context) }

    val coroutineScope = rememberCoroutineScope()
    var exportStatus by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            exportStatus = "Exporting…"
            try {
                val json = viewModel.buildExportJson()
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    OutputStreamWriter(stream).use { it.write(json) }
                }
                exportStatus = "Export complete"
            } catch (e: Exception) {
                exportStatus = "Export failed: ${e.message}"
            }
        }
    }

    var importStatus by remember { mutableStateOf<String?>(null) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            importStatus = "Importing…"
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream)).readText()
                } ?: throw IllegalStateException("Could not open file")
                val result = viewModel.importFromJson(jsonString)
                importStatus = "Imported ${result.importedCount}, skipped ${result.skippedCount} already-imported"
            } catch (e: Exception) {
                importStatus = "Import failed: ${e.message}"
            }
        }
    }

    var patternExportStatus by remember { mutableStateOf<String?>(null) }
    val patternExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            patternExportStatus = "Exporting…"
            try {
                val json = viewModel.buildBankPatternExportJson()
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    OutputStreamWriter(stream).use { it.write(json) }
                }
                patternExportStatus = "Export complete"
            } catch (e: Exception) {
                patternExportStatus = "Export failed: ${e.message}"
            }
        }
    }

    var patternImportStatus by remember { mutableStateOf<String?>(null) }
    val patternImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            patternImportStatus = "Importing…"
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream)).readText()
                } ?: throw IllegalStateException("Could not open file")
                val result = viewModel.importBankPatternsFromJson(jsonString)
                patternImportStatus = "Imported ${result.importedCount}, skipped ${result.skippedCount} already present"
            } catch (e: Exception) {
                patternImportStatus = "Import failed: ${e.message}"
            }
        }
    }

    var autoTagStatus by remember { mutableStateOf<String?>(null) }
    val autoTagLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            autoTagStatus = "Learning…"
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream)).readText()
                } ?: throw IllegalStateException("Could not open file")
                val result = viewModel.autoTagFromJson(jsonString)
                autoTagStatus = "Learned ${result.rulesLearned} merchant categories · " +
                    "tagged ${result.taggedCount} of ${result.candidateCount} needing review"
            } catch (e: Exception) {
                autoTagStatus = "Auto-tag failed: ${e.message}"
            }
        }
    }

    var aiTagStatus by remember { mutableStateOf<String?>(null) }
    var aiTagRunning by remember { mutableStateOf(false) }

    var showClearConfirm by remember { mutableStateOf(false) }
    var clearStatus by remember { mutableStateOf<String?>(null) }
    var developerExpanded by remember { mutableStateOf(false) }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear all transactions?", style = PaisaTheme.typography.bodyBold) },
            text = {
                Text(
                    "This permanently deletes every transaction on this device. This cannot be undone.",
                    style = PaisaTheme.typography.caption,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        coroutineScope.launch {
                            viewModel.clearAllTransactions()
                            clearStatus = "Cleared"
                        }
                    },
                ) {
                    Text("Clear", color = PaisaTheme.colors.negative)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(PaisaSpacing.gutter),
    ) {
        Text(
            text = "Settings",
            style = PaisaTheme.typography.title,
            color = PaisaTheme.colors.ink,
        )

        SettingsSection("Capture") {
            StatusRow(
                icon = Icons.Outlined.Notifications,
                title = "Notification access",
                subtitle = if (listenerEnabled) {
                    "GPay, PhonePe, CRED and Paytm notifications are captured"
                } else {
                    "Required to capture UPI app notifications. Tap to open system settings"
                },
                active = listenerEnabled,
                onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            )
            StatusRow(
                icon = Icons.Outlined.Sms,
                title = "SMS permission",
                subtitle = if (smsGranted) "Bank debit SMS are captured" else "Required to capture bank debit SMS. Tap to grant",
                active = smsGranted,
                onClick = {
                    smsPermissionLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS))
                },
            )
            StatusRow(
                icon = Icons.Outlined.Layers,
                title = "Draw over other apps",
                subtitle = if (overlayGranted) {
                    "The tag popup shows as an on-screen overlay instead of a notification"
                } else {
                    "Optional — grant for a forced on-screen popup instead of a notification"
                },
                active = overlayGranted,
                optional = true,
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
                    )
                },
            )
            StatusRow(
                icon = Icons.Outlined.BatteryChargingFull,
                title = "Ignore battery optimization",
                subtitle = if (batteryExempted) {
                    "Capture keeps running when the OS would otherwise kill it in the background"
                } else {
                    "Required on most phones — some OEMs kill the capture service without this. Tap to grant"
                },
                active = batteryExempted,
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
                    )
                },
            )
            SettingsRow(
                icon = Icons.Outlined.History,
                title = "Backfill SMS history",
                subtitle = when (backfillState) {
                    WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> "Running — reconstructing past months from bank SMS…"
                    WorkInfo.State.SUCCEEDED -> "Done — tap to run again over the last 180 days"
                    WorkInfo.State.FAILED -> "Failed — tap to retry"
                    else -> "Reconstruct past transactions from bank SMS already on this phone"
                },
                isProblem = backfillState == WorkInfo.State.FAILED,
                onClick = { viewModel.startBackfill() },
                isLast = true,
            )
        }

        SettingsSection("Data") {
            SettingsRow(
                icon = Icons.Outlined.Upload,
                title = "Export data",
                subtitle = exportStatus ?: "Save all transactions as JSON",
                isProblem = exportStatus?.startsWith("Export failed") == true,
                onClick = { exportLauncher.launch("paisetrail-export-${System.currentTimeMillis()}.json") },
            )
            SettingsRow(
                icon = Icons.Outlined.Download,
                title = "Import data",
                subtitle = importStatus ?: "Restore transactions from a PaisaTrail JSON export",
                isProblem = importStatus?.startsWith("Import failed") == true,
                onClick = { importLauncher.launch(arrayOf("application/json")) },
            )
            SettingsRow(
                icon = Icons.Outlined.AutoAwesome,
                title = "Auto-tag from JSON",
                subtitle = autoTagStatus ?: "Learn categories from a labeled export, apply them to what needs review",
                isProblem = autoTagStatus?.startsWith("Auto-tag failed") == true,
                onClick = { autoTagLauncher.launch(arrayOf("application/json")) },
            )
            SettingsRow(
                icon = Icons.Outlined.AutoAwesome,
                title = "Auto-tag with AI",
                subtitle = when {
                    aiTagRunning -> "Working…"
                    aiTagStatus != null -> aiTagStatus!!
                    else -> "Tries on-device Gemini Nano first, falls back to an offline similarity match"
                },
                isProblem = false,
                onClick = {
                    if (aiTagRunning) return@SettingsRow
                    aiTagRunning = true
                    aiTagStatus = null
                    coroutineScope.launch {
                        try {
                            val result = viewModel.autoTagWithAi()
                            aiTagStatus = "Tagged ${result.taggedByAi + result.taggedByLocal} of ${result.candidateCount} " +
                                "(${result.taggedByAi} by AI, ${result.taggedByLocal} by local match) · ${result.aiStatus}"
                        } catch (e: Exception) {
                            aiTagStatus = "Auto-tag failed: ${e.message}"
                        } finally {
                            aiTagRunning = false
                        }
                    }
                },
            )
            SettingsRow(
                icon = Icons.Outlined.Upload,
                title = "Export bank patterns",
                subtitle = patternExportStatus ?: "Save every bank SMS pattern (including disabled ones) as JSON",
                isProblem = patternExportStatus?.startsWith("Export failed") == true,
                onClick = { patternExportLauncher.launch("paisetrail-bank-patterns-${System.currentTimeMillis()}.json") },
            )
            SettingsRow(
                icon = Icons.Outlined.Download,
                title = "Import bank patterns",
                subtitle = patternImportStatus ?: "Restore bank patterns from a PaisaTrail export",
                isProblem = patternImportStatus?.startsWith("Import failed") == true,
                onClick = { patternImportLauncher.launch(arrayOf("application/json")) },
                isLast = true,
            )
        }

        SettingsSection("Manage") {
            SettingsRow(
                icon = Icons.Outlined.Category,
                title = "Categories",
                subtitle = "Rename, recolor, re-icon, add, or delete categories",
                isProblem = false,
                onClick = onNavigateToCategoryManagement,
            )
            SettingsRow(
                icon = Icons.Outlined.Sms,
                title = "Bank SMS patterns",
                subtitle = "Add or edit a bank's SMS regex without a code change",
                isProblem = false,
                onClick = onNavigateToBankPatternManagement,
            )
            SettingsRow(
                icon = Icons.Outlined.Storefront,
                title = "Merchants",
                subtitle = "Add or edit a merchant and its default category/VPA",
                isProblem = false,
                onClick = onNavigateToMerchantManagement,
            )
            SettingsRow(
                icon = Icons.Outlined.Inbox,
                title = "Review queue",
                subtitle = "Transactions still needing a category",
                isProblem = false,
                onClick = onNavigateToReviewQueue,
                isLast = true,
            )
        }

        SettingsSection(
            title = "Developer",
            collapsible = true,
            expanded = developerExpanded,
            onToggle = { developerExpanded = !developerExpanded },
        ) {
            SettingsRow(
                icon = Icons.Outlined.BugReport,
                title = "Parser debug",
                subtitle = "Inspect recent raw events and their parse results",
                isProblem = false,
                onClick = onNavigateToRawEventsDebug,
            )
            SettingsRow(
                icon = Icons.Outlined.Add,
                title = "Add random transaction",
                subtitle = "Preview a randomized transaction, re-roll it, then add it",
                isProblem = false,
                onClick = onNavigateToRandomTransaction,
            )
            SettingsRow(
                icon = Icons.Outlined.Edit,
                title = "Add custom transaction",
                subtitle = "Manually set the amount, payee, category, and date",
                isProblem = false,
                onClick = onNavigateToManualTransaction,
            )
            SettingsRow(
                icon = Icons.Outlined.DeleteForever,
                title = "Clear all transactions",
                subtitle = clearStatus ?: "Permanently deletes every transaction on this device",
                isProblem = true,
                onClick = { showClearConfirm = true },
                isLast = true,
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    collapsible: Boolean = false,
    expanded: Boolean = true,
    onToggle: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(top = PaisaSpacing.loose, bottom = PaisaSpacing.tight)
            .let { if (collapsible) it.clickable { onToggle?.invoke() } else it }
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = PaisaTheme.typography.label, color = PaisaTheme.colors.inkMuted)
        if (collapsible) {
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = PaisaTheme.colors.inkMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
    if (!collapsible || expanded) {
        PaisaCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp) {
            content()
        }
    }
}

@Composable
private fun StatusRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    active: Boolean,
    onClick: () -> Unit,
    optional: Boolean = false,
    isLast: Boolean = false,
) {
    val pillColor = if (active) PaisaTheme.colors.positive else if (optional) PaisaTheme.colors.inkMuted else PaisaTheme.colors.warning
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = PaisaSpacing.gutter, vertical = PaisaSpacing.normal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon)
        Column(modifier = Modifier.weight(1f).padding(start = PaisaSpacing.tight)) {
            Text(text = title, style = PaisaTheme.typography.bodyBold, color = PaisaTheme.colors.ink)
            Text(text = subtitle, style = PaisaTheme.typography.caption, color = PaisaTheme.colors.inkMuted)
        }
        Box(
            modifier = Modifier
                .background(pillColor.copy(alpha = 0.16f), ChipShape)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = if (active) "Active" else "Grant",
                style = PaisaTheme.typography.caption,
                color = pillColor,
            )
        }
    }
    if (!isLast) HairlineInset()
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isProblem: Boolean,
    onClick: () -> Unit,
    isLast: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = PaisaSpacing.gutter, vertical = PaisaSpacing.normal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tint = if (isProblem) PaisaTheme.colors.negative else PaisaTheme.colors.ink)
        Column(modifier = Modifier.weight(1f).padding(start = PaisaSpacing.tight)) {
            Text(
                text = title,
                style = PaisaTheme.typography.bodyBold,
                color = if (isProblem) PaisaTheme.colors.negative else PaisaTheme.colors.ink,
            )
            Text(text = subtitle, style = PaisaTheme.typography.caption, color = PaisaTheme.colors.inkMuted)
        }
        Text(text = "›", style = PaisaTheme.typography.title, color = PaisaTheme.colors.inkFaint)
    }
    if (!isLast) HairlineInset()
}

@Composable
private fun IconTile(icon: ImageVector, tint: Color = PaisaTheme.colors.ink) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(PaisaTheme.colors.surface2, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun HairlineInset() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PaisaSpacing.gutter + 36.dp + PaisaSpacing.tight)
            .height(1.dp)
            .background(PaisaTheme.colors.hairline),
    )
}

private fun hasSmsPermissions(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
        PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
        PackageManager.PERMISSION_GRANTED

private fun isNotificationListenerEnabled(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

private fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
