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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.work.WorkInfo
import com.paisetrail.app.ui.theme.PaisaSpacing
import com.paisetrail.app.ui.theme.PaisaTheme
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlinx.coroutines.launch

/** Full settings screen (source toggles, allowlist, bank pattern editor, ...) is Phase 4 work
 * (spec 7.6) — for now this exposes what Phase 1/3 actually need: granting capture permissions,
 * SMS backfill, and the parser debug screen. Without the permission grants the capture layer is
 * registered but never invoked — notification access can only be toggled from system settings,
 * and SMS is a dangerous runtime permission that the manifest alone doesn't grant. */
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

    // All these grants happen outside the app (system settings screens or a runtime dialog that
    // can return without a callback), so re-check on every resume rather than relying on a
    // one-shot launcher result.
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

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear all transactions?", style = PaisaTheme.typography.body) },
            text = {
                Text(
                    "This permanently deletes every transaction on this device. This cannot be undone.",
                    style = PaisaTheme.typography.bodySecondary,
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
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "Settings",
            style = PaisaTheme.typography.amountListHeader,
            color = PaisaTheme.colors.ink,
            modifier = Modifier.padding(PaisaSpacing.gutter),
        )
        HorizontalDivider(color = PaisaTheme.colors.hairline, thickness = 1.dp)
        SettingsRow(
            title = "Notification access",
            subtitle = if (listenerEnabled) {
                "Granted — GPay, PhonePe, CRED and Paytm notifications are captured"
            } else {
                "Required to capture UPI app notifications. Tap to open system settings"
            },
            isProblem = !listenerEnabled,
            onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
        )
        HorizontalDivider(color = PaisaTheme.colors.hairline, thickness = 1.dp)
        SettingsRow(
            title = "SMS permission",
            subtitle = if (smsGranted) {
                "Granted — bank debit SMS are captured"
            } else {
                "Required to capture bank debit SMS. Tap to grant"
            },
            isProblem = !smsGranted,
            onClick = {
                smsPermissionLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS))
            },
        )
        HorizontalDivider(color = PaisaTheme.colors.hairline, thickness = 1.dp)
        SettingsRow(
            title = "Draw over other apps",
            subtitle = if (overlayGranted) {
                "Granted — the tag popup shows as an on-screen overlay instead of a notification"
            } else {
                "Optional (spec 5) — grant for a forced on-screen popup instead of a notification"
            },
            isProblem = false,
            onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            },
        )
        HorizontalDivider(color = PaisaTheme.colors.hairline, thickness = 1.dp)
        SettingsRow(
            title = "Ignore battery optimization",
            subtitle = if (batteryExempted) {
                "Exempted — capture keeps running when the OS would otherwise kill it in the background"
            } else {
                "Required on most phones (spec 10/11) — some OEMs (Xiaomi/Oppo/Vivo/Nothing) kill " +
                    "the capture service without this. Tap to grant"
            },
            isProblem = !batteryExempted,
            onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            },
        )
        HorizontalDivider(color = PaisaTheme.colors.hairline, thickness = 1.dp)
        SettingsRow(
            title = "Backfill SMS history",
            subtitle = when (backfillState) {
                WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> "Running — reconstructing past months from bank SMS…"
                WorkInfo.State.SUCCEEDED -> "Done — tap to run again over the last 180 days"
                WorkInfo.State.FAILED -> "Failed — tap to retry"
                else -> "Reconstruct past transactions from bank SMS already on this phone (spec 3.4)"
            },
            isProblem = backfillState == WorkInfo.State.FAILED,
            onClick = { viewModel.startBackfill() },
        )
        HorizontalDivider(color = PaisaTheme.colors.hairline, thickness = 1.dp)
        SettingsRow(
            title = "Export data",
            subtitle = exportStatus ?: "Save all transactions as JSON (spec 7.6)",
            isProblem = exportStatus?.startsWith("Export failed") == true,
            onClick = {
                val fileName = "paisetrail-export-${System.currentTimeMillis()}.json"
                exportLauncher.launch(fileName)
            },
        )
        HorizontalDivider(color = PaisaTheme.colors.hairline, thickness = 1.dp)
        SettingsRow(
            title = "Import data",
            subtitle = importStatus ?: "Restore transactions from a PaisaTrail JSON export (spec 7.6)",
            isProblem = importStatus?.startsWith("Import failed") == true,
            onClick = { importLauncher.launch(arrayOf("application/json")) },
        )
        HorizontalDivider(color = PaisaTheme.colors.hairline, thickness = 1.dp)
        SettingsRow(
            title = "Auto-tag from JSON",
            subtitle = autoTagStatus
                ?: "Learn categories from a labeled PaisaTrail export and apply them to " +
                    "everything still needing review",
            isProblem = autoTagStatus?.startsWith("Auto-tag failed") == true,
            onClick = { autoTagLauncher.launch(arrayOf("application/json")) },
        )
        HorizontalDivider(color = PaisaTheme.colors.hairline, thickness = 1.dp)
        SettingsRow(
            title = "Auto-tag with AI",
            subtitle = when {
                aiTagRunning -> "Working…"
                aiTagStatus != null -> aiTagStatus!!
                else -> "Tries on-device Gemini Nano first, falls back to an offline similarity " +
                    "match against your own tagged history"
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
        HorizontalDivider(color = PaisaTheme.colors.hairline, thickness = 1.dp)
        SettingsRow(
            title = "Categories",
            subtitle = "Rename, recolor, re-icon, add, or delete categories",
            isProblem = false,
            onClick = onNavigateToCategoryManagement,
        )
        HorizontalDivider(color = PaisaTheme.colors.hairline, thickness = 1.dp)
        SettingsRow(
            title = "Bank SMS patterns",
            subtitle = "Add or edit a bank's SMS regex without a code change (spec 3.2)",
            isProblem = false,
            onClick = onNavigateToBankPatternManagement,
        )
        HorizontalDivider(color = PaisaTheme.colors.hairline, thickness = 1.dp)
        SettingsRow(
            title = "Export bank patterns",
            subtitle = patternExportStatus ?: "Save every bank SMS pattern (including disabled ones) as JSON",
            isProblem = patternExportStatus?.startsWith("Export failed") == true,
            onClick = {
                val fileName = "paisetrail-bank-patterns-${System.currentTimeMillis()}.json"
                patternExportLauncher.launch(fileName)
            },
        )
        HorizontalDivider(color = PaisaTheme.colors.hairline, thickness = 1.dp)
        SettingsRow(
            title = "Import bank patterns",
            subtitle = patternImportStatus ?: "Restore bank patterns from a PaisaTrail bank-pattern JSON export",
            isProblem = patternImportStatus?.startsWith("Import failed") == true,
            onClick = { patternImportLauncher.launch(arrayOf("application/json")) },
        )
        HorizontalDivider(color = PaisaTheme.colors.hairline, thickness = 1.dp)
        SettingsRow(
            title = "Merchants",
            subtitle = "Add or edit a merchant and its default category/VPA (spec 4.2)",
            isProblem = false,
            onClick = onNavigateToMerchantManagement,
        )
        HorizontalDivider(color = PaisaTheme.colors.hairline, thickness = 1.dp)
        SettingsRow(
            title = "Review queue",
            subtitle = "Transactions still needing a category (spec 7.5) — reachable here until " +
                "the Dashboard's badge exists (Phase 4)",
            isProblem = false,
            onClick = onNavigateToReviewQueue,
        )
        HorizontalDivider(color = PaisaTheme.colors.hairline, thickness = 1.dp)
        SettingsRow(
            title = "Parser debug",
            subtitle = "Inspect recent raw events and their parse results",
            isProblem = false,
            onClick = onNavigateToRawEventsDebug,
        )
        HorizontalDivider(color = PaisaTheme.colors.hairline, thickness = 1.dp)
        SettingsRow(
            title = "Add random transaction",
            subtitle = "Debug tool — preview a randomized transaction, re-roll it, then add it",
            isProblem = false,
            onClick = onNavigateToRandomTransaction,
        )
        HorizontalDivider(color = PaisaTheme.colors.hairline, thickness = 1.dp)
        SettingsRow(
            title = "Add custom transaction",
            subtitle = "Debug tool — manually set the amount, payee, category, and date",
            isProblem = false,
            onClick = onNavigateToManualTransaction,
        )
        HorizontalDivider(color = PaisaTheme.colors.hairline, thickness = 1.dp)
        SettingsRow(
            title = "Clear all transactions",
            subtitle = clearStatus ?: "Debug tool — permanently deletes every transaction on this device",
            isProblem = true,
            onClick = { showClearConfirm = true },
        )
        HorizontalDivider(color = PaisaTheme.colors.hairline, thickness = 1.dp)
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, isProblem: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = PaisaSpacing.gutter, vertical = PaisaSpacing.normal),
    ) {
        Text(
            text = title,
            style = PaisaTheme.typography.body,
            color = if (isProblem) PaisaTheme.colors.negative else PaisaTheme.colors.ink,
        )
        Text(
            text = subtitle,
            style = PaisaTheme.typography.bodySecondary,
            color = PaisaTheme.colors.inkMuted,
        )
    }
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
