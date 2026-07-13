package com.paisetrail.app.ui.screens.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.paisetrail.app.data.db.MerchantSpendRow
import com.paisetrail.app.ui.components.AmountText
import com.paisetrail.app.ui.components.CategoryDot
import com.paisetrail.app.ui.components.DonutChart
import com.paisetrail.app.ui.components.DonutSlice
import com.paisetrail.app.ui.components.FancyChip
import com.paisetrail.app.ui.components.LineChart
import com.paisetrail.app.ui.components.StackedBarChart
import com.paisetrail.app.ui.components.StatBlock
import com.paisetrail.app.ui.components.formatIndianRupees
import com.paisetrail.app.ui.components.parseCategoryColor
import com.paisetrail.app.ui.theme.PaisaSpacing
import com.paisetrail.app.ui.theme.PaisaTheme

private enum class SpendScope { MONTH, WEEK }

/** Single scrolling column: total -> donut -> weekly stacked bars -> monthly trend -> top
 * merchants (spec 7.1). The 30-day chart and budget/forecast live on the separate Insights screen
 * now — this stays the fast "how am I doing today" glance. Empty states are one line of inkMuted
 * text (spec 7.7) — a fresh install with no transactions yet should never look broken. */
@Composable
fun DashboardScreen(
    onNavigateToReviewQueue: () -> Unit,
    onNavigateToInsights: () -> Unit,
    onNavigateToTransactionsFiltered: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val monthTotal by viewModel.monthTotal.collectAsState()
    val previousMonthTotal by viewModel.previousMonthTotal.collectAsState()
    val categorySlices by viewModel.categorySlices.collectAsState()
    val weeklyStackedSpend by viewModel.weeklyStackedSpend.collectAsState()
    val weeklyCategorySlices by viewModel.weeklyCategorySlices.collectAsState()
    val monthlyTrend by viewModel.monthlyTrend.collectAsState()
    val topMerchants by viewModel.topMerchants.collectAsState()
    val reviewCount by viewModel.reviewCount.collectAsState()
    val budgetAlertCount by viewModel.budgetAlertCount.collectAsState()
    var spendScope by remember { mutableStateOf(SpendScope.MONTH) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaisaSpacing.gutter),
    ) {
        StatBlock(label = "This month") {
            AmountText(amountPaise = monthTotal, style = PaisaTheme.typography.dashboardTotal)
        }
        Text(
            text = deltaText(monthTotal, previousMonthTotal),
            style = PaisaTheme.typography.bodySecondary,
            color = PaisaTheme.colors.inkMuted,
            modifier = Modifier.padding(top = 4.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = PaisaSpacing.normal),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (reviewCount > 0) {
                Text(
                    text = "$reviewCount need${if (reviewCount == 1) "s" else ""} review",
                    style = PaisaTheme.typography.body,
                    color = PaisaTheme.colors.accent,
                    modifier = Modifier.clickable(onClick = onNavigateToReviewQueue),
                )
            } else {
                Box(modifier = Modifier)
            }
            Text(
                text = "Insights →",
                style = PaisaTheme.typography.body,
                color = PaisaTheme.colors.accent,
                modifier = Modifier.clickable(onClick = onNavigateToInsights),
            )
        }

        if (budgetAlertCount > 0) {
            Text(
                text = "$budgetAlertCount budget${if (budgetAlertCount == 1) "" else "s"} need${if (budgetAlertCount == 1) "s" else ""} attention",
                style = PaisaTheme.typography.body,
                color = PaisaTheme.colors.negative,
                modifier = Modifier
                    .clickable(onClick = onNavigateToInsights)
                    .padding(top = PaisaSpacing.tight),
            )
        }

        SectionLabel("Spending breakdown", topPadding = PaisaSpacing.loose)
        Row(
            modifier = Modifier.padding(top = PaisaSpacing.tight),
            horizontalArrangement = Arrangement.spacedBy(PaisaSpacing.tight),
        ) {
            FancyChip(
                label = "This month",
                emoji = null,
                colorHex = null,
                selected = spendScope == SpendScope.MONTH,
                onClick = { spendScope = SpendScope.MONTH },
            )
            FancyChip(
                label = "Last 7 days",
                emoji = null,
                colorHex = null,
                selected = spendScope == SpendScope.WEEK,
                onClick = { spendScope = SpendScope.WEEK },
            )
        }

        when (spendScope) {
            SpendScope.MONTH -> {
                if (categorySlices.isEmpty()) {
                    EmptyLine("No spending yet this month")
                } else {
                    DonutChart(
                        slices = categorySlices.map {
                            DonutSlice(parseCategoryColor(it.colorHex), it.amountPaise.toFloat(), it.emoji, it.name)
                        },
                        modifier = Modifier.padding(top = PaisaSpacing.normal),
                        centerLabel = formatIndianRupees(monthTotal),
                        onSliceClick = { slice -> onNavigateToTransactionsFiltered(slice.key) },
                    )
                    Column(modifier = Modifier.padding(top = PaisaSpacing.normal)) {
                        categorySlices.forEach { slice ->
                            CategorySliceRow(slice, onClick = { onNavigateToTransactionsFiltered(slice.name) })
                        }
                    }
                }
            }
            SpendScope.WEEK -> {
                if (weeklyCategorySlices.isEmpty()) {
                    EmptyLine("No spending yet")
                } else {
                    StackedBarChart(
                        days = weeklyStackedSpend.map { day ->
                            day.segments.map { (colorHex, amount) -> parseCategoryColor(colorHex) to amount.toFloat() }
                        },
                        baselineColor = PaisaTheme.colors.hairline,
                        modifier = Modifier.padding(top = PaisaSpacing.normal),
                        dayLabels = weeklyStackedSpend.map { it.dayLabel },
                        labelColor = PaisaTheme.colors.inkMuted,
                        showAllLabels = true,
                    )
                    Column(modifier = Modifier.padding(top = PaisaSpacing.normal)) {
                        weeklyCategorySlices.forEach { slice ->
                            CategorySliceRow(slice, onClick = { onNavigateToTransactionsFiltered(slice.name) })
                        }
                    }
                }
            }
        }

        SectionLabel("6-month trend", topPadding = PaisaSpacing.loose)
        if (monthlyTrend.size < 2) {
            EmptyLine("Not enough history yet")
        } else {
            LineChart(
                values = monthlyTrend.map { it.amountPaise.toFloat() },
                lineColor = PaisaTheme.colors.ink,
                baselineColor = PaisaTheme.colors.hairline,
                modifier = Modifier.padding(top = PaisaSpacing.normal),
            )
        }

        SectionLabel("Top merchants", topPadding = PaisaSpacing.loose)
        if (topMerchants.isEmpty()) {
            EmptyLine("Not enough data yet — add a few more transactions to see your top merchants")
        } else {
            Column(modifier = Modifier.padding(top = PaisaSpacing.normal)) {
                topMerchants.forEach { merchant -> TopMerchantRow(merchant) }
            }
        }
    }
}

@Composable
private fun CategorySliceRow(slice: CategorySlice, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryDot(slice.colorHex, slice.emoji)
            Text(
                text = slice.name,
                style = PaisaTheme.typography.body,
                color = PaisaTheme.colors.ink,
                modifier = Modifier.padding(start = PaisaSpacing.tight),
            )
        }
        Text(
            text = formatIndianRupees(slice.amountPaise),
            style = PaisaTheme.typography.bodySecondary,
            color = PaisaTheme.colors.inkMuted,
        )
    }
}

@Composable
private fun SectionLabel(text: String, topPadding: androidx.compose.ui.unit.Dp) {
    Text(
        text = text,
        style = PaisaTheme.typography.overline,
        color = PaisaTheme.colors.inkMuted,
        modifier = Modifier.padding(top = topPadding),
    )
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        style = PaisaTheme.typography.bodySecondary,
        color = PaisaTheme.colors.inkMuted,
        modifier = Modifier.padding(top = PaisaSpacing.tight),
    )
}

@Composable
private fun TopMerchantRow(merchant: MerchantSpendRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = merchant.merchantName, style = PaisaTheme.typography.body, color = PaisaTheme.colors.ink)
        Text(
            text = formatIndianRupees(merchant.amountPaise),
            style = PaisaTheme.typography.bodySecondary,
            color = PaisaTheme.colors.inkMuted,
        )
    }
}

private fun deltaText(current: Long, previous: Long): String {
    if (previous <= 0L) return "No spend last month to compare"
    val deltaPaise = current - previous
    val percent = (deltaPaise * 100) / previous
    val direction = if (deltaPaise >= 0) "up" else "down"
    return "$direction ${kotlin.math.abs(percent)}% vs last month"
}
