package com.paisetrail.app.ui.screens.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.paisetrail.app.data.db.TripEntity
import com.paisetrail.app.data.db.TxnDirection
import com.paisetrail.app.ui.components.EmptyState
import com.paisetrail.app.ui.components.FancyChip
import com.paisetrail.app.ui.components.IconPillButton
import com.paisetrail.app.ui.components.StaggeredEntry
import com.paisetrail.app.ui.components.TxnRow
import com.paisetrail.app.ui.theme.ChipShape
import com.paisetrail.app.ui.theme.PaisaSpacing
import com.paisetrail.app.ui.theme.PaisaTheme
import com.paisetrail.app.ui.theme.SheetShape

/** Grouped-by-day list with search + a flexible filter panel (category and trip, spec 7.3).
 * Tapping a row opens the transaction detail screen, which is also where re-tagging happens now.
 * Rows are spaced 4dp apart with sticky-style date eyebrows instead of hairlines (spec §4.4). */
@Composable
fun TransactionsScreen(onNavigateToTransaction: (Long) -> Unit = {}, viewModel: TransactionsViewModel = hiltViewModel()) {
    val groups by viewModel.groupedTransactions.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val trips by viewModel.trips.collectAsState()
    val selectedCategoryName by viewModel.selectedCategoryNameState.collectAsState()
    val selectedTripId by viewModel.selectedTripIdState.collectAsState()
    val monthLabel by viewModel.selectedMonthLabel.collectAsState()
    val query by viewModel.searchQueryState.collectAsState()
    var showFilterPanel by remember { mutableStateOf(false) }
    val isFiltering = query.isNotBlank() || selectedCategoryName != null || selectedTripId != null

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PaisaSpacing.gutter, vertical = PaisaSpacing.tight),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "‹",
                style = PaisaTheme.typography.amountL,
                color = PaisaTheme.colors.accent,
                modifier = Modifier.clickable { viewModel.previousMonth() }.padding(horizontal = PaisaSpacing.tight),
            )
            Text(
                text = monthLabel,
                style = PaisaTheme.typography.bodyBold,
                color = PaisaTheme.colors.ink,
            )
            Text(
                text = "›",
                style = PaisaTheme.typography.amountL,
                color = PaisaTheme.colors.accent,
                modifier = Modifier.clickable { viewModel.nextMonth() }.padding(horizontal = PaisaSpacing.tight),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PaisaSpacing.gutter, vertical = PaisaSpacing.tight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PaisaSpacing.tight),
        ) {
            BasicTextField(
                value = query,
                onValueChange = { viewModel.setSearchQuery(it) },
                textStyle = TextStyle(color = PaisaTheme.colors.ink, fontSize = PaisaTheme.typography.body.fontSize),
                modifier = Modifier
                    .weight(1f)
                    .background(PaisaTheme.colors.surface2, ChipShape)
                    .padding(horizontal = PaisaSpacing.tight, vertical = PaisaSpacing.tight),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            text = "Search by name",
                            style = PaisaTheme.typography.body,
                            color = PaisaTheme.colors.inkMuted,
                        )
                    }
                    inner()
                },
            )
            IconPillButton(
                icon = Icons.Outlined.FilterList,
                active = selectedCategoryName != null || selectedTripId != null,
                onClick = { showFilterPanel = true },
            )
        }

        if (groups.isEmpty()) {
            EmptyState(
                title = if (isFiltering) "No matching transactions" else "No transactions",
                body = if (isFiltering) "Try a different search or filter" else "Nothing recorded in $monthLabel yet",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val entryIndexByTxnId = remember(groups) {
                val map = mutableMapOf<Long, Int>()
                var i = 0
                groups.forEach { group -> group.rows.forEach { row -> map[row.txn.id] = i++ } }
                map
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                groups.forEach { group ->
                    item(key = "header_${group.dayLabel}") {
                        Text(
                            text = group.dayLabel,
                            style = PaisaTheme.typography.label,
                            color = PaisaTheme.colors.inkMuted,
                            modifier = Modifier.padding(
                                horizontal = PaisaSpacing.gutter,
                                vertical = PaisaSpacing.tight,
                            ),
                        )
                    }
                    items(group.rows, key = { it.txn.id }) { row ->
                        StaggeredEntry(index = entryIndexByTxnId[row.txn.id] ?: 0) {
                            TxnRow(
                                amountPaise = row.txn.amountPaise,
                                merchantName = row.txn.payeeNameRaw ?: row.txn.vpa ?: "Unknown",
                                placeText = row.txn.placeName ?: row.txn.locality,
                                categoryColorHex = row.categoryColorHex,
                                categoryEmoji = row.categoryEmoji,
                                isCredit = row.txn.direction == TxnDirection.CREDIT,
                                onClick = { onNavigateToTransaction(row.txn.id) },
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }

    if (showFilterPanel) {
        TransactionFilterPanel(
            categories = categories,
            trips = trips,
            selectedCategoryName = selectedCategoryName,
            selectedTripId = selectedTripId,
            onDismiss = { showFilterPanel = false },
            onPickCategory = { viewModel.setSelectedCategoryName(it) },
            onPickTrip = { viewModel.setSelectedTripId(it) },
        )
    }
}

@Composable
private fun TransactionFilterPanel(
    categories: List<com.paisetrail.app.data.db.CategoryEntity>,
    trips: List<TripEntity>,
    selectedCategoryName: String?,
    selectedTripId: Long?,
    onDismiss: () -> Unit,
    onPickCategory: (String?) -> Unit,
    onPickTrip: (Long?) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .clickable(onClick = onDismiss),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(PaisaTheme.colors.surface1, SheetShape),
        ) {
            Column(modifier = Modifier.padding(PaisaSpacing.gutter)) {
                Text(
                    text = "Category",
                    style = PaisaTheme.typography.label,
                    color = PaisaTheme.colors.inkMuted,
                    modifier = Modifier.padding(bottom = PaisaSpacing.tight),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(PaisaSpacing.tight),
                ) {
                    FancyChip(
                        label = "All",
                        emoji = null,
                        colorHex = null,
                        selected = selectedCategoryName == null,
                        onClick = { onPickCategory(null) },
                    )
                    categories.forEach { category ->
                        FancyChip(
                            label = category.name,
                            emoji = category.emoji,
                            colorHex = category.colorHex,
                            selected = selectedCategoryName == category.name,
                            onClick = { onPickCategory(category.name) },
                        )
                    }
                }

                Text(
                    text = "Trip",
                    style = PaisaTheme.typography.label,
                    color = PaisaTheme.colors.inkMuted,
                    modifier = Modifier.padding(top = PaisaSpacing.normal, bottom = PaisaSpacing.tight),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(PaisaSpacing.tight),
                ) {
                    FancyChip(
                        label = "All",
                        emoji = null,
                        colorHex = null,
                        selected = selectedTripId == null,
                        onClick = { onPickTrip(null) },
                    )
                    FancyChip(
                        label = "Not on a trip",
                        emoji = null,
                        colorHex = null,
                        selected = selectedTripId == UNTRIPPED_FILTER_ID,
                        onClick = { onPickTrip(UNTRIPPED_FILTER_ID) },
                    )
                    trips.forEach { trip ->
                        FancyChip(
                            label = trip.name,
                            emoji = null,
                            colorHex = null,
                            selected = selectedTripId == trip.id,
                            onClick = { onPickTrip(trip.id) },
                        )
                    }
                }
            }
        }
    }
}
