package com.paisetrail.app.ui.screens.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.rememberCameraPositionState
import com.paisetrail.app.BuildConfig
import com.paisetrail.app.ui.components.BarChart
import com.paisetrail.app.ui.components.CategoryDot
import com.paisetrail.app.ui.components.DonutChart
import com.paisetrail.app.ui.components.DonutSlice
import com.paisetrail.app.ui.components.MapPin
import com.paisetrail.app.ui.components.PaisaCard
import com.paisetrail.app.ui.components.TickerAmount
import com.paisetrail.app.ui.components.formatIndianRupees
import com.paisetrail.app.ui.components.parseCategoryColor
import com.paisetrail.app.ui.screens.map.TxnClusterItem
import com.paisetrail.app.ui.theme.CardShape
import com.paisetrail.app.ui.theme.PaisaSpacing
import com.paisetrail.app.ui.theme.PaisaTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val DATE_FORMAT = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())

/** Trip detail drill-down (spec 7.4): total, per-category donut, per-day bars. Distance-ordered
 * polyline map and PDF/image export are deferred. */
@Composable
fun TripDetailScreen(onBack: () -> Unit, viewModel: TripDetailViewModel = hiltViewModel()) {
    val trip by viewModel.trip.collectAsState()
    val totalPaise by viewModel.totalPaise.collectAsState()
    val categorySlices by viewModel.categorySlices.collectAsState()
    val dailySpend by viewModel.dailySpend.collectAsState()
    val mapItems by viewModel.mapItems.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete \"${trip?.name}\"?", style = PaisaTheme.typography.bodyBold) },
            text = {
                Text(
                    "Its transactions aren't deleted — they just stop being tagged to this trip. This cannot be undone.",
                    style = PaisaTheme.typography.caption,
                )
            },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; viewModel.deleteTrip(onDeleted = onBack) }) {
                    Text("Delete", color = PaisaTheme.colors.negative)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaisaSpacing.gutter),
    ) {
        Text(
            text = "← Trips",
            style = PaisaTheme.typography.bodyBold,
            color = PaisaTheme.colors.accent,
            modifier = Modifier.clickable(onClick = onBack),
        )

        Text(
            text = trip?.name ?: "Trip",
            style = PaisaTheme.typography.title,
            color = PaisaTheme.colors.ink,
            modifier = Modifier.padding(top = PaisaSpacing.normal),
        )
        trip?.let {
            val range = if (it.endAt != null) {
                "${DATE_FORMAT.format(Instant.ofEpochMilli(it.startAt))} – ${DATE_FORMAT.format(Instant.ofEpochMilli(it.endAt))}"
            } else {
                "Started ${DATE_FORMAT.format(Instant.ofEpochMilli(it.startAt))}"
            }
            Text(
                text = range,
                style = PaisaTheme.typography.caption,
                color = PaisaTheme.colors.inkMuted,
            )
        }

        TickerAmount(
            amountPaise = totalPaise,
            style = PaisaTheme.typography.heroAmount,
            modifier = Modifier.padding(top = PaisaSpacing.loose),
        )

        SectionLabel("Where it went", topPadding = PaisaSpacing.loose)
        if (categorySlices.isEmpty()) {
            EmptyLine("No spending yet")
        } else {
            PaisaCard(modifier = Modifier.fillMaxWidth().padding(top = PaisaSpacing.tight)) {
                DonutChart(
                    slices = categorySlices.map {
                        DonutSlice(parseCategoryColor(it.colorHex), it.amountPaise.toFloat(), it.emoji, it.name)
                    },
                )
                Column(modifier = Modifier.padding(top = PaisaSpacing.normal)) {
                    categorySlices.forEach { slice ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CategoryDot(slice.colorHex, slice.emoji)
                                Text(
                                    text = slice.name,
                                    style = PaisaTheme.typography.bodyBold,
                                    color = PaisaTheme.colors.ink,
                                    modifier = Modifier.padding(start = PaisaSpacing.tight),
                                )
                            }
                            Text(
                                text = formatIndianRupees(slice.amountPaise),
                                style = PaisaTheme.typography.caption,
                                color = PaisaTheme.colors.inkMuted,
                            )
                        }
                    }
                }
            }
        }

        if (BuildConfig.HAS_MAPS_API_KEY && mapItems.isNotEmpty()) {
            SectionLabel("Where you were", topPadding = PaisaSpacing.loose)
            TripMiniMap(mapItems, modifier = Modifier.padding(top = PaisaSpacing.tight))
        }

        SectionLabel("By day", topPadding = PaisaSpacing.loose)
        if (dailySpend.isEmpty()) {
            EmptyLine("No spending yet")
        } else {
            PaisaCard(modifier = Modifier.fillMaxWidth().padding(top = PaisaSpacing.tight)) {
                BarChart(
                    values = dailySpend.map { it.amountPaise.toFloat() },
                    barColor = PaisaTheme.colors.accent,
                    baselineColor = PaisaTheme.colors.hairline,
                    dayLabels = dailySpend.map { it.day.takeLast(2) },
                    labelColor = PaisaTheme.colors.inkMuted,
                )
            }
        }

        Text(
            text = "Delete trip",
            style = PaisaTheme.typography.bodyBold,
            color = PaisaTheme.colors.negative,
            modifier = Modifier
                .clickable { showDeleteConfirm = true }
                .padding(top = PaisaSpacing.loose),
        )
    }
}

/** A small but fully interactive map of the trip's pins (spec 7.4 "view the map in each trip")
 * — centered on their centroid at city-level zoom, since a trip's payments cluster in one area
 * far more often than not. Pinch-zoom/pan/rotate all work like the main Map screen; only the
 * on-screen +/- zoom control buttons are hidden to keep the card compact. */
@OptIn(MapsComposeExperimentalApi::class)
@Composable
private fun TripMiniMap(items: List<TxnClusterItem>, modifier: Modifier = Modifier) {
    val centroidLat = items.map { it.txn.lat!! }.average()
    val centroidLng = items.map { it.txn.lng!! }.average()
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(centroidLat, centroidLng), 12f)
    }
    GoogleMap(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(CardShape),
        cameraPositionState = cameraPositionState,
        properties = com.google.maps.android.compose.MapProperties(mapStyleOptions = com.paisetrail.app.ui.components.paisaMapStyle()),
        uiSettings = com.google.maps.android.compose.MapUiSettings(zoomControlsEnabled = false),
    ) {
        Clustering(
            items = items,
            clusterContent = { cluster ->
                val slices = cluster.items
                    .groupBy { it.categoryColorHex }
                    .map { (colorHex, grouped) -> com.paisetrail.app.ui.components.parseCategoryColor(colorHex) to grouped.size.toFloat() }
                com.paisetrail.app.ui.components.MapClusterBubble(cluster.size, slices)
            },
            clusterItemContent = { item -> MapPin(item.txn.amountPaise, item.categoryColorHex, item.categoryEmoji) },
        )
    }
}

@Composable
private fun SectionLabel(text: String, topPadding: androidx.compose.ui.unit.Dp) {
    Text(
        text = text,
        style = PaisaTheme.typography.label,
        color = PaisaTheme.colors.inkMuted,
        modifier = Modifier.padding(top = topPadding),
    )
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        style = PaisaTheme.typography.caption,
        color = PaisaTheme.colors.inkMuted,
        modifier = Modifier.padding(top = PaisaSpacing.tight),
    )
}
