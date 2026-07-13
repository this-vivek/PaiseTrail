package com.paisetrail.app.ui.screens.debug

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.paisetrail.app.data.db.RawEventEntity
import com.paisetrail.app.data.db.RawEventSource
import com.paisetrail.app.ui.theme.PaisaSpacing
import com.paisetrail.app.ui.theme.PaisaTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_FORMAT = DateTimeFormatter.ofPattern("dd MMM HH:mm:ss").withZone(ZoneId.systemDefault())

/** Shows recent raw_events with their parse outcome — spec 7.6's "parser debug screen", the tool
 * for turning a bad regex into a two-minute edit instead of a release (spec 11). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RawEventsDebugScreen(
    onBack: () -> Unit,
    viewModel: RawEventsDebugViewModel = hiltViewModel(),
) {
    val events by viewModel.events.collectAsState()
    val redactedCount = viewModel.redactedNotificationCount

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Parser debug", style = PaisaTheme.typography.body) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PaisaTheme.colors.bg,
                    titleContentColor = PaisaTheme.colors.ink,
                    navigationIconContentColor = PaisaTheme.colors.ink,
                ),
            )
        },
        containerColor = PaisaTheme.colors.bg,
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (redactedCount > 0) {
                Text(
                    text = "$redactedCount notification${if (redactedCount == 1) "" else "s"} arrived with " +
                        "content hidden (Android 15+ redaction, spec 8 #8) — the SMS twin covered these",
                    style = PaisaTheme.typography.bodySecondary,
                    color = PaisaTheme.colors.inkMuted,
                    modifier = Modifier.padding(horizontal = PaisaSpacing.gutter, vertical = PaisaSpacing.tight),
                )
                HorizontalDivider(color = PaisaTheme.colors.hairline, thickness = 1.dp)
            }

            if (events.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No raw events captured yet",
                        style = PaisaTheme.typography.bodySecondary,
                        color = PaisaTheme.colors.inkMuted,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(events, key = { it.id }) { event ->
                        RawEventRow(event)
                        HorizontalDivider(color = PaisaTheme.colors.hairline, thickness = 1.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun RawEventRow(event: RawEventEntity) {
    var expanded by remember(event.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = PaisaSpacing.gutter, vertical = PaisaSpacing.tight),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${event.source.label()} · ${event.packageOrSender}",
                style = PaisaTheme.typography.body,
                color = PaisaTheme.colors.ink,
            )
            Text(
                text = if (event.parsedOk) "parsed" else "unparsed",
                style = PaisaTheme.typography.bodySecondary,
                color = if (event.parsedOk) PaisaTheme.colors.inkMuted else PaisaTheme.colors.negative,
            )
        }
        Text(
            text = TIME_FORMAT.format(Instant.ofEpochMilli(event.postedAt)) +
                (event.txnId?.let { " · linked to txn #$it" } ?: " · unlinked"),
            style = PaisaTheme.typography.overline,
            color = PaisaTheme.colors.inkMuted,
        )
        Text(
            text = event.fullText,
            style = PaisaTheme.typography.bodySecondary,
            color = PaisaTheme.colors.ink,
            maxLines = if (expanded) Int.MAX_VALUE else 1,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun RawEventSource.label(): String = when (this) {
    RawEventSource.NOTIFICATION -> "NOTIF"
    RawEventSource.SMS -> "SMS"
    RawEventSource.BACKFILL -> "BACKFILL"
}
