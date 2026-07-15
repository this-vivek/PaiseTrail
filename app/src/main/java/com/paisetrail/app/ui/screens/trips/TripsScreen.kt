package com.paisetrail.app.ui.screens.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.paisetrail.app.data.db.TripEntity
import com.paisetrail.app.ui.components.AmountText
import com.paisetrail.app.ui.components.EmptyState
import com.paisetrail.app.ui.components.PaisaCard
import com.paisetrail.app.ui.theme.ChipShape
import com.paisetrail.app.ui.theme.PaisaSpacing
import com.paisetrail.app.ui.theme.PaisaTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

private val DATE_FORMAT = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())

/** Manual start/stop + past trip summaries (spec 7.4, §4.7). Each past trip is a large PaisaCard:
 * a gradient header (place/dates + total), then a three-stat row (days, transactions, total).
 * Auto-detect prompt is a tag-popup-style notification, not built into this screen. */
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
            title = { Text("Delete \"${trip.name}\"?", style = PaisaTheme.typography.bodyBold) },
            text = {
                Text(
                    "Its transactions aren't deleted — they just stop being tagged to this trip. This cannot be undone.",
                    style = PaisaTheme.typography.caption,
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

    Column(modifier = Modifier.fillMaxSize().padding(PaisaSpacing.gutter)) {
        Text(
            text = "Trips",
            style = PaisaTheme.typography.title,
            color = PaisaTheme.colors.ink,
        )

        if (activeTrip != null) {
            PaisaCard(modifier = Modifier.fillMaxWidth().padding(top = PaisaSpacing.normal)) {
                Text(
                    text = activeTrip!!.name,
                    style = PaisaTheme.typography.bodyBold,
                    color = PaisaTheme.colors.ink,
                )
                Text(
                    text = "Started ${DATE_FORMAT.format(Instant.ofEpochMilli(activeTrip!!.startAt))}",
                    style = PaisaTheme.typography.caption,
                    color = PaisaTheme.colors.inkMuted,
                )
                Row(modifier = Modifier.padding(top = PaisaSpacing.tight), horizontalArrangement = Arrangement.spacedBy(PaisaSpacing.loose)) {
                    ActionText(text = "End trip", onClick = { viewModel.endTrip() })
                    ActionText(text = "Delete trip", onClick = { deleteTarget = activeTrip }, color = PaisaTheme.colors.negative)
                }
            }
        } else {
            PaisaCard(modifier = Modifier.fillMaxWidth().padding(top = PaisaSpacing.normal)) {
                BasicTextField(
                    value = newTripName,
                    onValueChange = { newTripName = it },
                    textStyle = TextStyle(color = PaisaTheme.colors.ink, fontSize = PaisaTheme.typography.body.fontSize),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PaisaTheme.colors.surface2, ChipShape)
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
                ActionText(
                    text = "Start trip",
                    onClick = { viewModel.startTrip(newTripName); newTripName = "" },
                    modifier = Modifier.padding(top = PaisaSpacing.tight),
                )
            }
        }

        if (!hasHomeLocation) {
            PaisaCard(
                modifier = Modifier.fillMaxWidth().padding(top = PaisaSpacing.tight),
                onClick = { viewModel.setHomeLocationFromCurrentFix() },
            ) {
                Text(
                    text = "Set home location",
                    style = PaisaTheme.typography.bodyBold,
                    color = PaisaTheme.colors.accent,
                )
                Text(
                    text = "Needed for trip auto-detect — tap to use your current location",
                    style = PaisaTheme.typography.caption,
                    color = PaisaTheme.colors.inkMuted,
                )
            }
        }

        if (pastTrips.isEmpty()) {
            EmptyState(
                title = "No past trips yet",
                body = "Start a trip above, or let auto-detect find one",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(top = PaisaSpacing.tight)) {
                items(pastTrips, key = { it.trip.id }) { summary ->
                    TripCard(
                        summary,
                        onClick = { onNavigateToTrip(summary.trip.id) },
                        onLongClick = { deleteTarget = summary.trip },
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TripCard(summary: TripSummary, onClick: () -> Unit, onLongClick: () -> Unit) {
    val days = tripDays(summary.trip.startAt, summary.trip.endAt)

    PaisaCard(
        padding = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(PaisaTheme.colors.accent.copy(alpha = 0.5f), PaisaTheme.colors.surface2),
                    ),
                ),
        ) {
            Column(modifier = Modifier.padding(PaisaSpacing.normal), verticalArrangement = Arrangement.Bottom) {
                Text(
                    text = DATE_FORMAT.format(Instant.ofEpochMilli(summary.trip.startAt)),
                    style = PaisaTheme.typography.label,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                )
                Text(
                    text = summary.trip.name,
                    style = PaisaTheme.typography.title,
                    color = androidx.compose.ui.graphics.Color.White,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(PaisaSpacing.normal),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MiniStat(label = "Days", value = days.toString())
            MiniStat(label = "Transactions", value = summary.transactionCount.toString())
            AmountText(amountPaise = summary.totalPaise, style = PaisaTheme.typography.amountM)
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column {
        Text(text = value, style = PaisaTheme.typography.bodyBold, color = PaisaTheme.colors.ink)
        Text(text = label, style = PaisaTheme.typography.caption, color = PaisaTheme.colors.inkMuted)
    }
}

private fun tripDays(startAt: Long, endAt: Long?): Int {
    val durationMs = (endAt ?: System.currentTimeMillis()) - startAt
    return (durationMs / (24 * 60 * 60 * 1000.0)).roundToInt().coerceAtLeast(1)
}

@Composable
private fun ActionText(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = PaisaTheme.colors.accent,
) {
    Text(
        text = text,
        style = PaisaTheme.typography.bodyBold,
        color = color,
        modifier = modifier.clickable(onClick = onClick),
    )
}
