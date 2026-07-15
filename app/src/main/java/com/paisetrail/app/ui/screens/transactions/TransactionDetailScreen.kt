package com.paisetrail.app.ui.screens.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.paisetrail.app.BuildConfig
import com.paisetrail.app.data.db.TxnStatus
import com.paisetrail.app.ui.components.CategoryPickerSheet
import com.paisetrail.app.ui.components.HairlineDivider
import com.paisetrail.app.ui.components.PaisaCard
import com.paisetrail.app.ui.components.TickerAmount
import com.paisetrail.app.ui.components.parseCategoryColor
import com.paisetrail.app.ui.theme.CardShape
import com.paisetrail.app.ui.theme.PaisaSpacing
import com.paisetrail.app.ui.theme.PaisaTheme
import com.paisetrail.app.ui.theme.PillShape
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val DATETIME_FORMAT = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    .withZone(ZoneId.systemDefault())

/** One transaction, in full (spec §4.5): a 56dp category tile, merchant name, amount, a trip chip
 * if tagged, a small static map when located, and a clean key-value ledger in one [PaisaCard].
 * Category is tappable and re-tags through the same
 * [com.paisetrail.app.enrich.TagConfirmationUseCase] as everywhere else. */
@Composable
fun TransactionDetailScreen(onBack: () -> Unit, viewModel: TransactionDetailViewModel = hiltViewModel()) {
    val txn by viewModel.txn.collectAsState()
    val category by viewModel.category.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val tripName by viewModel.tripName.collectAsState()
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaisaSpacing.gutter),
    ) {
        Text(
            text = "← Back",
            style = PaisaTheme.typography.bodyBold,
            color = PaisaTheme.colors.accent,
            modifier = Modifier.clickable(onClick = onBack),
        )

        txn?.let { t ->
            val tileColor = parseCategoryColor(category?.colorHex)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = PaisaSpacing.normal),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(tileColor.copy(alpha = 0.14f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = category?.emoji ?: (t.payeeNameRaw ?: t.vpa ?: "?").take(1).uppercase(),
                        style = PaisaTheme.typography.title,
                        color = tileColor,
                    )
                }
                Column(modifier = Modifier.padding(start = PaisaSpacing.tight)) {
                    Text(
                        text = t.payeeNameRaw ?: t.vpa ?: "Unknown",
                        style = PaisaTheme.typography.title,
                        color = PaisaTheme.colors.ink,
                    )
                    TickerAmount(amountPaise = t.amountPaise, style = PaisaTheme.typography.amountL)
                }
            }

            Row(modifier = Modifier.padding(top = PaisaSpacing.tight), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                statusNote(t.status)?.let { note ->
                    Chip(text = note, color = PaisaTheme.colors.warning)
                }
                tripName?.let { name ->
                    Chip(text = name, color = PaisaTheme.colors.accent)
                }
            }

            if (BuildConfig.HAS_MAPS_API_KEY && t.lat != null && t.lng != null) {
                SinglePinMap(lat = t.lat, lng = t.lng, modifier = Modifier.padding(top = PaisaSpacing.loose))
            }

            val ledgerRows = buildList<@Composable () -> Unit> {
                add {
                    DetailRow(label = "Category", onClick = { showCategoryPicker = true }) {
                        Text(
                            text = category?.name ?: "Uncategorized",
                            style = PaisaTheme.typography.bodyBold,
                            color = PaisaTheme.colors.ink,
                        )
                    }
                }
                add {
                    DetailRow(label = "Date") {
                        Text(
                            text = DATETIME_FORMAT.format(Instant.ofEpochMilli(t.occurredAt)),
                            style = PaisaTheme.typography.body,
                            color = PaisaTheme.colors.ink,
                        )
                    }
                }
                (t.placeName ?: t.locality)?.let { place ->
                    add {
                        DetailRow(label = "Place") {
                            Text(text = place, style = PaisaTheme.typography.body, color = PaisaTheme.colors.ink)
                        }
                    }
                }
                t.upiRef?.let { ref ->
                    add {
                        DetailRow(label = "UPI ref") {
                            Text(text = ref, style = PaisaTheme.typography.body, color = PaisaTheme.colors.ink)
                        }
                    }
                }
                t.bankAcctLast4?.let { last4 ->
                    add {
                        DetailRow(label = "Account") {
                            Text(text = "•••• $last4", style = PaisaTheme.typography.body, color = PaisaTheme.colors.ink)
                        }
                    }
                }
            }
            PaisaCard(modifier = Modifier.fillMaxWidth().padding(top = PaisaSpacing.loose)) {
                ledgerRows.forEachIndexed { index, row ->
                    row()
                    if (index != ledgerRows.lastIndex) HairlineDivider()
                }
            }
            Text(
                text = "Delete transaction",
                style = PaisaTheme.typography.bodyBold,
                color = PaisaTheme.colors.negative,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDeleteConfirm = true }
                    .padding(top = PaisaSpacing.loose),
            )
        }
    }

    if (showCategoryPicker) {
        CategoryPickerSheet(
            categories = categories,
            selectedCategoryName = category?.name,
            onDismiss = { showCategoryPicker = false },
            onPick = { name ->
                viewModel.retag(name)
                showCategoryPicker = false
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this transaction?", style = PaisaTheme.typography.bodyBold) },
            text = {
                Text(
                    "This removes it entirely — use this for a duplicate entry from the same real payment. This cannot be undone.",
                    style = PaisaTheme.typography.caption,
                )
            },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; viewModel.delete(onDeleted = onBack) }) {
                    Text("Delete", color = PaisaTheme.colors.negative)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun Chip(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(modifier = Modifier.background(color.copy(alpha = 0.14f), PillShape).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(text = text, style = PaisaTheme.typography.caption, color = color)
    }
}

private fun statusNote(status: TxnStatus): String? = when (status) {
    TxnStatus.REFUNDED -> "Refunded — not counted as spend"
    TxnStatus.SELF_TRANSFER -> "Payment to yourself — not counted as spend"
    TxnStatus.SUSPECT_DUP -> "Flagged as a possible duplicate"
    TxnStatus.CONFIRMED -> null
}

@Composable
private fun DetailRow(label: String, onClick: (() -> Unit)? = null, value: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = PaisaSpacing.tight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = PaisaTheme.typography.caption, color = PaisaTheme.colors.inkMuted)
        Row(verticalAlignment = Alignment.CenterVertically) { value() }
    }
}

@Composable
private fun SinglePinMap(lat: Double, lng: Double, modifier: Modifier = Modifier) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(lat, lng), 15f)
    }
    GoogleMap(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(CardShape),
        cameraPositionState = cameraPositionState,
        properties = com.google.maps.android.compose.MapProperties(
            mapStyleOptions = com.paisetrail.app.ui.components.paisaMapStyle(),
        ),
        uiSettings = MapUiSettings(zoomControlsEnabled = false),
    ) {
        Marker(state = MarkerState(position = LatLng(lat, lng)))
    }
}
