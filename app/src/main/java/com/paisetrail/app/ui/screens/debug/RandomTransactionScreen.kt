package com.paisetrail.app.ui.screens.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.paisetrail.app.ui.components.AmountText
import com.paisetrail.app.ui.components.CategoryDot
import com.paisetrail.app.ui.theme.PaisaShape
import com.paisetrail.app.ui.theme.PaisaSpacing
import com.paisetrail.app.ui.theme.PaisaTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val DATETIME_FORMAT = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    .withZone(ZoneId.systemDefault())

/** Debug tool (spec 7.6): a randomly generated transaction you can re-roll before adding, so
 * exercising the app doesn't require waiting on real UPI activity. */
@Composable
fun RandomTransactionScreen(onDone: () -> Unit, viewModel: RandomTransactionViewModel = hiltViewModel()) {
    val preview by viewModel.preview.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(PaisaSpacing.gutter)) {
        Text(
            text = "← Cancel",
            style = PaisaTheme.typography.body,
            color = PaisaTheme.colors.accent,
            modifier = Modifier.clickable(onClick = onDone),
        )
        Text(
            text = "Random transaction",
            style = PaisaTheme.typography.amountListHeader,
            color = PaisaTheme.colors.ink,
            modifier = Modifier.padding(top = PaisaSpacing.normal),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = PaisaSpacing.loose)
                .background(PaisaTheme.colors.surface, PaisaShape)
                .padding(PaisaSpacing.gutter),
        ) {
            Text(
                text = preview.draft.payeeName,
                style = PaisaTheme.typography.body,
                color = PaisaTheme.colors.ink,
            )
            AmountText(
                amountPaise = preview.draft.amountPaise,
                style = PaisaTheme.typography.dashboardTotal,
                modifier = Modifier.padding(top = PaisaSpacing.tight),
            )
            Row(
                modifier = Modifier.padding(top = PaisaSpacing.normal),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryDot(preview.categoryColorHex, preview.categoryEmoji)
                Text(
                    text = preview.draft.categoryName,
                    style = PaisaTheme.typography.body,
                    color = PaisaTheme.colors.ink,
                    modifier = Modifier.padding(start = PaisaSpacing.tight),
                )
            }
            Text(
                text = DATETIME_FORMAT.format(Instant.ofEpochMilli(preview.draft.occurredAt)),
                style = PaisaTheme.typography.bodySecondary,
                color = PaisaTheme.colors.inkMuted,
                modifier = Modifier.padding(top = PaisaSpacing.tight),
            )
        }

        Text(
            text = "🔀  Try another",
            style = PaisaTheme.typography.body,
            color = PaisaTheme.colors.accent,
            modifier = Modifier
                .clickable { viewModel.regenerate() }
                .padding(top = PaisaSpacing.loose),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = PaisaSpacing.normal)
                .background(PaisaTheme.colors.accent, RoundedCornerShape(50))
                .clickable { viewModel.confirm(onDone) }
                .padding(vertical = PaisaSpacing.tight),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "Add transaction", style = PaisaTheme.typography.body, color = PaisaTheme.colors.bg)
        }
    }
}
