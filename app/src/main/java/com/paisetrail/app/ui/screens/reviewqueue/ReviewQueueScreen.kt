package com.paisetrail.app.ui.screens.reviewqueue

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.paisetrail.app.ui.components.CategoryPickerSheet
import com.paisetrail.app.ui.components.EmptyState
import com.paisetrail.app.ui.components.PaisaCard
import com.paisetrail.app.ui.theme.PaisaSpacing
import com.paisetrail.app.ui.theme.PaisaTheme
import com.paisetrail.app.ui.theme.PillShape
import com.paisetrail.app.util.formatRupees
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val REVIEW_DATETIME_FORMAT = DateTimeFormatter.ofPattern("d MMM, h:mm a").withZone(ZoneId.systemDefault())

/** Card-stack triage (spec §4.8) — one transaction at a time, tap a category and it flies off
 * right while the next one springs in. This is the app's most-repeated interaction, so it gets
 * the most motion budget. A "List" toggle switches to the old bulk-review list. */
@Composable
fun ReviewQueueScreen(viewModel: ReviewQueueViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsState()
    val categories by viewModel.categories.collectAsState()
    var showList by remember { mutableStateOf(false) }
    var pickerForTxnId by remember { mutableStateOf<Long?>(null) }
    val haptics = LocalHapticFeedback.current

    Column(modifier = Modifier.fillMaxSize().padding(PaisaSpacing.gutter)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (items.isEmpty()) "Review queue" else "${items.size} left to review",
                style = PaisaTheme.typography.title,
                color = PaisaTheme.colors.ink,
            )
            Text(
                text = if (showList) "Cards" else "List",
                style = PaisaTheme.typography.bodyBold,
                color = PaisaTheme.colors.accent,
                modifier = Modifier.clickable { showList = !showList },
            )
        }

        if (items.isEmpty()) {
            EmptyState(
                title = "Nothing needs review",
                body = "Every transaction is tagged",
                modifier = Modifier.fillMaxSize(),
            )
        } else if (showList) {
            ReviewList(
                items,
                onConfirm = { txnId, name -> viewModel.confirmCategory(txnId, name) },
                onMoreCategories = { txnId -> pickerForTxnId = txnId },
            )
        } else {
            val current = items.first()
            AnimatedContent(
                targetState = current,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    (scaleIn(tween(260), initialScale = 0.95f) + fadeIn(tween(260))) togetherWith
                        (slideOutHorizontally(tween(220)) { it } + fadeOut(tween(180)))
                },
                contentKey = { it.txn.id },
                label = "reviewCard",
            ) { item ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ReviewCard(
                        item = item,
                        onConfirm = { categoryName ->
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.confirmCategory(item.txn.id, categoryName)
                        },
                        onMoreCategories = { pickerForTxnId = item.txn.id },
                    )
                }
            }
        }
    }

    // Composed last (after the Column above) so it paints on top — NavHost wraps route content in
    // an implicit Box, and siblings there draw in call order, not by fillMaxSize/alignment intent.
    pickerForTxnId?.let { txnId ->
        CategoryPickerSheet(
            categories = categories,
            onDismiss = { pickerForTxnId = null },
            onPick = { name ->
                viewModel.confirmCategory(txnId, name)
                pickerForTxnId = null
            },
        )
    }
}

@Composable
private fun ReviewCard(item: ReviewItem, onConfirm: (String) -> Unit, onMoreCategories: () -> Unit) {
    PaisaCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = formatRupees(item.txn.amountPaise),
            style = PaisaTheme.typography.amountL,
            color = PaisaTheme.colors.ink,
        )
        item.txn.payeeNameRaw?.let {
            Text(
                text = it,
                style = PaisaTheme.typography.bodyBold,
                color = PaisaTheme.colors.ink,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        (item.txn.placeName ?: item.txn.locality)?.let {
            Text(text = it, style = PaisaTheme.typography.caption, color = PaisaTheme.colors.inkMuted)
        }
        Text(
            text = REVIEW_DATETIME_FORMAT.format(Instant.ofEpochMilli(item.txn.occurredAt)),
            style = PaisaTheme.typography.caption,
            color = PaisaTheme.colors.inkMuted,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.size(PaisaSpacing.loose))
        Text(
            text = "Suggested category",
            style = PaisaTheme.typography.label,
            color = PaisaTheme.colors.inkMuted,
        )
        Column(modifier = Modifier.padding(top = PaisaSpacing.tight)) {
            item.suggestions.forEachIndexed { index, categoryName ->
                CategoryOption(
                    label = categoryName,
                    highlighted = index == 0,
                    onClick = { onConfirm(categoryName) },
                )
                if (index != item.suggestions.lastIndex) Spacer(Modifier.size(PaisaSpacing.tight))
            }
        }
        Text(
            text = "Choose another category ›",
            style = PaisaTheme.typography.bodyBold,
            color = PaisaTheme.colors.accent,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onMoreCategories)
                .padding(top = PaisaSpacing.normal),
        )
    }
}

@Composable
private fun CategoryOption(label: String, highlighted: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (highlighted) PaisaTheme.colors.accent else PaisaTheme.colors.surface2,
                PillShape,
            )
            .clickable(onClick = onClick)
            .padding(vertical = PaisaSpacing.tight),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = PaisaTheme.typography.bodyBold,
            color = if (highlighted) PaisaTheme.colors.bg else PaisaTheme.colors.ink,
        )
    }
}

@Composable
private fun ReviewList(items: List<ReviewItem>, onConfirm: (Long, String) -> Unit, onMoreCategories: (Long) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items, key = { it.txn.id }) { item ->
            PaisaCard(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(
                            text = formatRupees(item.txn.amountPaise),
                            style = PaisaTheme.typography.amountM,
                            color = PaisaTheme.colors.ink,
                        )
                        item.txn.payeeNameRaw?.let {
                            Text(text = it, style = PaisaTheme.typography.caption, color = PaisaTheme.colors.inkMuted)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = item.categoryName ?: "Uncategorized",
                            style = PaisaTheme.typography.label,
                            color = PaisaTheme.colors.inkMuted,
                        )
                        Text(
                            text = REVIEW_DATETIME_FORMAT.format(Instant.ofEpochMilli(item.txn.occurredAt)),
                            style = PaisaTheme.typography.caption,
                            color = PaisaTheme.colors.inkMuted,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Row(
                    modifier = Modifier.padding(top = PaisaSpacing.tight),
                    horizontalArrangement = Arrangement.spacedBy(PaisaSpacing.tight),
                ) {
                    item.suggestions.forEach { categoryName ->
                        Box(
                            modifier = Modifier
                                .background(PaisaTheme.colors.surface2, PillShape)
                                .clickable { onConfirm(item.txn.id, categoryName) }
                                .padding(horizontal = PaisaSpacing.tight, vertical = 6.dp),
                        ) {
                            Text(text = categoryName, style = PaisaTheme.typography.caption, color = PaisaTheme.colors.ink)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .background(PaisaTheme.colors.accent.copy(alpha = 0.14f), PillShape)
                            .clickable { onMoreCategories(item.txn.id) }
                            .padding(horizontal = PaisaSpacing.tight, vertical = 6.dp),
                    ) {
                        Text(text = "More…", style = PaisaTheme.typography.caption, color = PaisaTheme.colors.accent)
                    }
                }
            }
        }
    }
}
