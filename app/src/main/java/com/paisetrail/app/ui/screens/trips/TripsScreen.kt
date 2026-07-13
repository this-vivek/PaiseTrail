package com.paisetrail.app.ui.screens.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.paisetrail.app.data.db.TripEntity
import com.paisetrail.app.ui.components.AmountText
import com.paisetrail.app.ui.components.HairlineDivider
import com.paisetrail.app.ui.theme.PaisaSpacing
import com.paisetrail.app.ui.theme.PaisaTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val DATE_FORMAT = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())

/** Manual start/stop + past trip summaries (spec 7.4). Auto-detect prompt is a tag-popup-style
 * notification (spec 7.4/5), not built into this screen. Distance-ordered polyline map and
 * PDF/image export are deferred. */
@Composable
fun TripsScreen(onNavigateToTrip: (Long) -> Unit = {}, viewModel: TripsViewModel = hiltViewModel()) {
    val activeTrip by viewModel.activeTrip.collectAsState()
    val pastTrips by viewModel.pastTrips.collectAsState()
    val hasHomeLocation by viewModel.hasHomeLocation.collectAsState()
    var newTripName by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<TripEntity?>(null) }

    deleteTarget?.let { trip ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete \"${trip.name}\"?", style = PaisaTheme.typography.body) },
            text = {
                Text(
                    "Its transactions aren't deleted — they just stop being tagged to this trip. This cannot be undone.",
                    style = PaisaTheme.typography.bodySecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteTrip(trip); deleteTarget = null }) {
                    Text("Delete", color = PaisaTheme.colors.negative)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Trips",
            style = PaisaTheme.typography.amountListHeader,
            color = PaisaTheme.colors.ink,
            modifier = Modifier.padding(PaisaSpacing.gutter),
        )
        HairlineDivider()

        if (activeTrip != null) {
            Column(modifier = Modifier.fillMaxWidth().padding(PaisaSpacing.gutter)) {
                Text(
                    text = activeTrip!!.name,
                    style = PaisaTheme.typography.body,
                    color = PaisaTheme.colors.ink,
                )
                Text(
                    text = "Started ${DATE_FORMAT.format(Instant.ofEpochMilli(activeTrip!!.startAt))}",
                    style = PaisaTheme.typography.bodySecondary,
                    color = PaisaTheme.colors.inkMuted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(PaisaSpacing.loose)) {
                    TextButton(text = "End trip", onClick = { viewModel.endTrip() })
                    TextButton(
                        text = "Delete trip",
                        onClick = { deleteTarget = activeTrip },
                        color = PaisaTheme.colors.negative,
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth().padding(PaisaSpacing.gutter)) {
                BasicTextField(
                    value = newTripName,
                    onValueChange = { newTripName = it },
                    textStyle = TextStyle(color = PaisaTheme.colors.ink, fontSize = PaisaTheme.typography.body.fontSize),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PaisaTheme.colors.surface, RoundedCornerShape(12.dp))
                        .padding(PaisaSpacing.tight),
                    decorationBox = { inner ->
                        if (newTripName.isEmpty()) {
                            Text(
                                text = "Trip name",
                                style = PaisaTheme.typography.body,
                                color = PaisaTheme.colors.inkMuted,
                            )
                        }
                        inner()
                    },
                )
                TextButton(
                    text = "Start trip",
                    onClick = {
                        viewModel.startTrip(newTripName)
                        newTripName = ""
                    },
                )
            }
        }
        HairlineDivider()

        if (!hasHomeLocation) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setHomeLocationFromCurrentFix() }
                    .padding(PaisaSpacing.gutter),
            ) {
                Text(
                    text = "Set home location",
                    style = PaisaTheme.typography.body,
                    color = PaisaTheme.colors.accent,
                )
                Text(
                    text = "Needed for trip auto-detect (spec 7.4) — tap to use your current location",
                    style = PaisaTheme.typography.bodySecondary,
                    color = PaisaTheme.colors.inkMuted,
                )
            }
            HairlineDivider()
        }

        if (pastTrips.isEmpty()) {
            Text(
                text = "No past trips yet",
                style = PaisaTheme.typography.bodySecondary,
                color = PaisaTheme.colors.inkMuted,
                modifier = Modifier.padding(PaisaSpacing.gutter),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(pastTrips, key = { it.trip.id }) { summary ->
                    TripRow(
                        summary,
                        onClick = { onNavigateToTrip(summary.trip.id) },
                        onLongClick = { deleteTarget = summary.trip },
                    )
                    HairlineDivider()
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TripRow(summary: TripSummary, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = PaisaSpacing.gutter, vertical = PaisaSpacing.tight),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = summary.trip.name, style = PaisaTheme.typography.body, color = PaisaTheme.colors.ink)
            AmountText(amountPaise = summary.totalPaise, style = PaisaTheme.typography.amountRow)
        }
        Text(
            text = "${summary.transactionCount} transactions · ${DATE_FORMAT.format(Instant.ofEpochMilli(summary.trip.startAt))} " +
                "· hold to delete",
            style = PaisaTheme.typography.bodySecondary,
            color = PaisaTheme.colors.inkMuted,
        )
    }
}

@Composable
private fun TextButton(text: String, onClick: () -> Unit, color: androidx.compose.ui.graphics.Color = PaisaTheme.colors.accent) {
    Text(
        text = text,
        style = PaisaTheme.typography.body,
        color = color,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(top = PaisaSpacing.tight),
    )
}
