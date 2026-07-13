package com.paisetrail.app.ui.screens.reviewqueue

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.paisetrail.app.ui.theme.PaisaSpacing
import com.paisetrail.app.ui.theme.PaisaTheme
import com.paisetrail.app.util.formatRupees

/** Real list (spec 7.5) — LOW-confidence/uncategorized transactions with one-tap confirm via
 * [CategoryGuesser]'s same top-3 predictions the tag popup offers. */
@Composable
fun ReviewQueueScreen(viewModel: ReviewQueueViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsState()

    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Nothing needs review",
                style = PaisaTheme.typography.bodySecondary,
                color = PaisaTheme.colors.inkMuted,
            )
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items, key = { it.txn.id }) { item ->
            ReviewRow(item = item, onConfirm = { categoryName -> viewModel.confirmCategory(item.txn.id, categoryName) })
            HorizontalDivider(color = PaisaTheme.colors.hairline, thickness = 1.dp)
        }
    }
}

@Composable
private fun ReviewRow(item: ReviewItem, onConfirm: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PaisaSpacing.gutter, vertical = PaisaSpacing.normal),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(
                    text = formatRupees(item.txn.amountPaise),
                    style = PaisaTheme.typography.amountRow,
                    color = PaisaTheme.colors.ink,
                )
                item.txn.payeeNameRaw?.let {
                    Text(text = it, style = PaisaTheme.typography.bodySecondary, color = PaisaTheme.colors.inkMuted)
                }
            }
            Text(
                text = item.categoryName ?: "Uncategorized",
                style = PaisaTheme.typography.overline,
                color = PaisaTheme.colors.inkMuted,
            )
        }
        Row(
            modifier = Modifier.padding(top = PaisaSpacing.tight),
            horizontalArrangement = Arrangement.spacedBy(PaisaSpacing.tight),
        ) {
            item.suggestions.forEach { categoryName ->
                CategoryChip(label = categoryName, onClick = { onConfirm(categoryName) })
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(color = PaisaTheme.colors.surface, shape = RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = PaisaSpacing.tight, vertical = 6.dp),
    ) {
        Text(text = label, style = PaisaTheme.typography.bodySecondary, color = PaisaTheme.colors.ink)
    }
}
