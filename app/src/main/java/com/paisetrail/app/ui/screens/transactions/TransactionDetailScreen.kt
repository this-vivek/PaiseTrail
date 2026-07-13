package com.paisetrail.app.ui.screens.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import com.paisetrail.app.ui.components.AmountText
import com.paisetrail.app.ui.components.CategoryDot
import com.paisetrail.app.ui.components.CategoryPickerSheet
import com.paisetrail.app.ui.theme.PaisaShape
import com.paisetrail.app.ui.theme.PaisaSpacing
import com.paisetrail.app.ui.theme.PaisaTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val DATETIME_FORMAT = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    .withZone(ZoneId.systemDefault())

/** One transaction, in full (spec 7.3): where it went, when, its UPI ref/trip if any, and its
 * location on a small map. Category is tappable and re-tags through the same
 * [com.paisetrail.app.enrich.TagConfirmationUseCase] as everywhere else. */
@Composable
fun TransactionDetailScreen(onBack: () -> Unit, viewModel: TransactionDetailViewModel = hiltViewModel()) {
    val txn by viewModel.txn.collectAsState()
    val category by viewModel.category.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val tripName by viewModel.tripName.collectAsState()
    var showCategoryPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaisaSpacing.gutter),
    ) {
        Text(
            text = "← Back",
            style = PaisaTheme.typography.body,
            color = PaisaTheme.colors.accent,
            modifier = Modifier.clickable(onClick = onBack),
        )

        txn?.let { t ->
            Text(
                text = t.payeeNameRaw ?: t.vpa ?: "Unknown",
                style = PaisaTheme.typography.amountListHeader,
                color = PaisaTheme.colors.ink,
                modifier = Modifier.padding(top = PaisaSpacing.normal),
            )
            AmountText(
                amountPaise = t.amountPaise,
                style = PaisaTheme.typography.dashboardTotal,
                modifier = Modifier.padding(top = PaisaSpacing.tight),
            )
            statusNote(t.status)?.let { note ->
                Text(
                    text = note,
                    style = PaisaTheme.typography.bodySecondary,
                    color = PaisaTheme.colors.inkMuted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Column(modifier = Modifier.padding(top = PaisaSpacing.loose)) {
                DetailRow(label = "Category", onClick = { showCategoryPicker = true }) {
                    CategoryDot(category?.colorHex, category?.emoji)
                    Text(
                        text = category?.name ?: "Uncategorized",
                        style = PaisaTheme.typography.body,
                        color = PaisaTheme.colors.ink,
                        modifier = Modifier.padding(start = PaisaSpacing.tight),
                    )
                }
                DetailRow(label = "Date") {
                    Text(
                        text = DATETIME_FORMAT.format(Instant.ofEpochMilli(t.occurredAt)),
                        style = PaisaTheme.typography.body,
                        color = PaisaTheme.colors.ink,
                    )
                }
                val place = t.placeName ?: t.locality
                if (place != null) {
                    DetailRow(label = "Place") {
                        Text(text = place, style = PaisaTheme.typography.body, color = PaisaTheme.colors.ink)
                    }
                }
                if (t.upiRef != null) {
                    DetailRow(label = "UPI ref") {
                        Text(text = t.upiRef, style = PaisaTheme.typography.body, color = PaisaTheme.colors.ink)
                    }
                }
                if (t.bankAcctLast4 != null) {
                    DetailRow(label = "Account") {
                        Text(text = "•••• ${t.bankAcctLast4}", style = PaisaTheme.typography.body, color = PaisaTheme.colors.ink)
                    }
                }
                tripName?.let { name ->
                    DetailRow(label = "Trip") {
                        Text(text = name, style = PaisaTheme.typography.body, color = PaisaTheme.colors.ink)
                    }
                }
            }

            if (BuildConfig.HAS_MAPS_API_KEY && t.lat != null && t.lng != null) {
                Text(
                    text = "Location",
                    style = PaisaTheme.typography.overline,
                    color = PaisaTheme.colors.inkMuted,
                    modifier = Modifier.padding(top = PaisaSpacing.loose, bottom = PaisaSpacing.tight),
                )
                SinglePinMap(lat = t.lat, lng = t.lng)
            }
        }
    }

    if (showCategoryPicker) {
        CategoryPickerSheet(
            categories = categories,
            onDismiss = { showCategoryPicker = false },
            onPick = { name ->
                viewModel.retag(name)
                showCategoryPicker = false
            },
        )
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
        Text(text = label, style = PaisaTheme.typography.bodySecondary, color = PaisaTheme.colors.inkMuted)
        Row(verticalAlignment = Alignment.CenterVertically) { value() }
    }
}

@Composable
private fun SinglePinMap(lat: Double, lng: Double) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(lat, lng), 15f)
    }
    GoogleMap(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(PaisaShape),
        cameraPositionState = cameraPositionState,
        properties = com.google.maps.android.compose.MapProperties(
            mapStyleOptions = com.paisetrail.app.ui.components.paisaMapStyle(),
        ),
        uiSettings = MapUiSettings(zoomControlsEnabled = false),
    ) {
        Marker(state = MarkerState(position = LatLng(lat, lng)))
    }
}
