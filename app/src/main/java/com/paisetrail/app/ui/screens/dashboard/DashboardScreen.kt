package com.paisetrail.app.ui.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.paisetrail.app.data.db.MerchantSpendRow
import com.paisetrail.app.ui.components.AuroraHealth
import com.paisetrail.app.ui.components.AuroraHeroCard
import com.paisetrail.app.ui.components.AuroraProgressBar
import com.paisetrail.app.ui.components.CategoryDot
import com.paisetrail.app.ui.components.DonutChart
import com.paisetrail.app.ui.components.DonutSlice
import com.paisetrail.app.ui.components.PaisaCard
import com.paisetrail.app.ui.components.SparkLine
import com.paisetrail.app.ui.components.TickerAmount
import com.paisetrail.app.ui.components.formatIndianRupees
import com.paisetrail.app.ui.components.maskedRupees
import com.paisetrail.app.ui.components.parseCategoryColor
import com.paisetrail.app.ui.theme.PaisaSpacing
import com.paisetrail.app.ui.theme.PaisaTheme

private val COMPACT_BAR_THRESHOLD = 220.dp
private const val SWIPE_MONTH_THRESHOLD_PX = 120f

/** Single scrolling column, hero-first (spec §4.2): greeting + review badge, the Aurora hero
 * (month total, forecast, budget progress, 7-day sparkline — hue driven by budget health), this
 * month's category donut, 30-day/6-month trend cards, top merchants. Empty states are one line of
 * inkMuted text — a fresh install with no transactions yet should never look broken. */
@Composable
fun DashboardScreen(
    onNavigateToReviewQueue: () -> Unit,
    onNavigateToInsights: () -> Unit,
    onNavigateToBudget: () -> Unit,
    onNavigateToTransactionsFiltered: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val monthTotal by viewModel.monthTotal.collectAsState()
    val monthLabel by viewModel.selectedMonthLabel.collectAsState()
    val isCurrentMonth by viewModel.isCurrentMonth.collectAsState()
    val categorySlices by viewModel.categorySlices.collectAsState()
    val topMerchants by viewModel.topMerchants.collectAsState()
    val reviewCount by viewModel.reviewCount.collectAsState()
    val budgetAlertCount by viewModel.budgetAlertCount.collectAsState()
    val forecastPaise by viewModel.forecastPaise.collectAsState()
    val budgetHealthRatio by viewModel.budgetHealthRatio.collectAsState()
    val weeklySparkline by viewModel.weeklySparkline.collectAsState()
    val safeToSpendPerDay by viewModel.safeToSpendPerDay.collectAsState()
    val amountsHidden by viewModel.amountsHidden.collectAsState()
    val scrollState = rememberScrollState()
    val compactBarThresholdPx = with(LocalDensity.current) { COMPACT_BAR_THRESHOLD.toPx() }
    val showCompactBar = scrollState.value > compactBarThresholdPx
    var monthDragAccum by remember { mutableStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(PaisaSpacing.gutter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = { monthDragAccum = 0f },
                            onHorizontalDrag = { _, dragAmount ->
                                monthDragAccum += dragAmount
                                if (monthDragAccum > SWIPE_MONTH_THRESHOLD_PX) {
                                    viewModel.previousMonth()
                                    monthDragAccum = 0f
                                } else if (monthDragAccum < -SWIPE_MONTH_THRESHOLD_PX) {
                                    viewModel.nextMonth()
                                    monthDragAccum = 0f
                                }
                            },
                        )
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "‹",
                        style = PaisaTheme.typography.bodyBold,
                        color = PaisaTheme.colors.accent,
                        modifier = Modifier.clickable(onClick = viewModel::previousMonth).padding(end = PaisaSpacing.tight),
                    )
                    Text(text = monthLabel, style = PaisaTheme.typography.label, color = PaisaTheme.colors.inkMuted)
                    Text(
                        text = "›",
                        style = PaisaTheme.typography.bodyBold,
                        color = if (isCurrentMonth) PaisaTheme.colors.inkMuted.copy(alpha = 0.4f) else PaisaTheme.colors.accent,
                        modifier = Modifier
                            .clickable(enabled = !isCurrentMonth, onClick = viewModel::nextMonth)
                            .padding(start = PaisaSpacing.tight),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (amountsHidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (amountsHidden) "Show amounts" else "Hide amounts",
                        tint = PaisaTheme.colors.inkMuted,
                        modifier = Modifier
                            .clickable(onClick = viewModel::toggleAmountsHidden)
                            .padding(8.dp)
                            .size(20.dp),
                    )
                    if (reviewCount > 0) {
                        Box(
                            modifier = Modifier
                                .background(PaisaTheme.colors.warning.copy(alpha = 0.16f), RoundedCornerShape(50))
                                .clickable(onClick = onNavigateToReviewQueue)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = "$reviewCount need${if (reviewCount == 1) "s" else ""} review",
                                style = PaisaTheme.typography.caption,
                                color = PaisaTheme.colors.warning,
                            )
                        }
                    }
                }
            }

            AuroraHeroCard(
                health = AuroraHealth.fromRatio(budgetHealthRatio),
                modifier = Modifier.padding(top = PaisaSpacing.tight),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(
                            text = if (isCurrentMonth) "This month" else monthLabel,
                            style = PaisaTheme.typography.label,
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f),
                        )
                        TickerAmount(
                            amountPaise = monthTotal,
                            style = PaisaTheme.typography.heroAmount,
                            color = androidx.compose.ui.graphics.Color.White,
                            masked = amountsHidden,
                        )
                    }
                    if (isCurrentMonth && weeklySparkline.any { it > 0f }) {
                        SparkLine(
                            values = weeklySparkline,
                            lineColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.padding(top = PaisaSpacing.loose),
                        )
                    }
                }
                if (isCurrentMonth) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Bottom) {
                        Text(
                            text = forecastText(monthTotal, forecastPaise, amountsHidden),
                            style = PaisaTheme.typography.caption,
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.82f),
                        )
                        safeToSpendPerDay?.let { perDay ->
                            Text(
                                text = safeToSpendText(perDay, amountsHidden),
                                style = PaisaTheme.typography.caption,
                                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.82f),
                            )
                        }
                        if (budgetHealthRatio != null) {
                            AuroraProgressBar(
                                fraction = budgetHealthRatio!!,
                                modifier = Modifier.padding(top = PaisaSpacing.tight),
                            )
                        }
                    }
                }
            }

        if (budgetAlertCount > 0) {
            Text(
                text = "$budgetAlertCount budget${if (budgetAlertCount == 1) "" else "s"} need${if (budgetAlertCount == 1) "s" else ""} attention",
                style = PaisaTheme.typography.bodyBold,
                color = PaisaTheme.colors.negative,
                modifier = Modifier
                    .clickable(onClick = onNavigateToBudget)
                    .padding(top = PaisaSpacing.normal),
            )
        }
        Text(
            text = "Insights →",
            style = PaisaTheme.typography.bodyBold,
            color = PaisaTheme.colors.accent,
            modifier = Modifier.clickable(onClick = onNavigateToInsights).padding(top = PaisaSpacing.tight),
        )

        SectionLabel("This month", topPadding = PaisaSpacing.loose)
        if (categorySlices.isEmpty()) {
            EmptyLine("No spending yet this month")
        } else {
            DonutChart(
                slices = categorySlices.map {
                    DonutSlice(parseCategoryColor(it.colorHex), it.amountPaise.toFloat(), it.emoji, it.name)
                },
                modifier = Modifier.padding(top = PaisaSpacing.normal),
                centerLabel = if (amountsHidden) maskedRupees() else formatIndianRupees(monthTotal),
                onSliceClick = { slice -> onNavigateToTransactionsFiltered(slice.key) },
            )
            Column(modifier = Modifier.padding(top = PaisaSpacing.normal)) {
                categorySlices.take(4).forEach { slice ->
                    CategorySliceRow(slice, hidden = amountsHidden, onClick = { onNavigateToTransactionsFiltered(slice.name) })
                }
            }
            if (categorySlices.size > 4) {
                Text(
                    text = "All categories →",
                    style = PaisaTheme.typography.bodyBold,
                    color = PaisaTheme.colors.accent,
                    modifier = Modifier.clickable(onClick = onNavigateToInsights).padding(top = PaisaSpacing.tight),
                )
            }
        }

            SectionLabel("Top merchants", topPadding = PaisaSpacing.loose)
            if (topMerchants.isEmpty()) {
                EmptyLine("Not enough data yet — add a few more transactions to see your top merchants")
            } else {
                Column(modifier = Modifier.padding(top = PaisaSpacing.normal)) {
                    topMerchants.forEach { merchant -> TopMerchantRow(merchant, hidden = amountsHidden) }
                }
            }
        }

        // Scrolling past the hero surfaces a slim persistent bar (month always visible) so
        // context isn't lost — the amount specifically slides in from the right rather than just
        // fading with the rest of the bar, per the requested "swift" arrival.
        AnimatedVisibility(
            visible = showCompactBar,
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { -it / 2 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 2 },
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PaisaTheme.colors.bg.copy(alpha = 0.96f))
                    .statusBarsPadding()
                    .padding(horizontal = PaisaSpacing.gutter, vertical = PaisaSpacing.tight),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = monthLabel, style = PaisaTheme.typography.bodyBold, color = PaisaTheme.colors.ink)
                AnimatedVisibility(
                    visible = showCompactBar,
                    enter = slideInHorizontally(tween(280)) { it } + fadeIn(tween(280)),
                    exit = slideOutHorizontally(tween(180)) { it } + fadeOut(tween(140)),
                ) {
                    Text(
                        text = if (amountsHidden) maskedRupees() else formatIndianRupees(monthTotal),
                        style = PaisaTheme.typography.amountM,
                        color = PaisaTheme.colors.ink,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategorySliceRow(slice: CategorySlice, hidden: Boolean, onClick: () -> Unit) {
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
                style = PaisaTheme.typography.bodyBold,
                color = PaisaTheme.colors.ink,
                modifier = Modifier.padding(start = PaisaSpacing.tight),
            )
        }
        Text(
            text = if (hidden) maskedRupees() else formatIndianRupees(slice.amountPaise),
            style = PaisaTheme.typography.caption,
            color = PaisaTheme.colors.inkMuted,
        )
    }
}

@Composable
private fun SectionLabel(text: String, topPadding: Dp) {
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

@Composable
private fun TopMerchantRow(merchant: MerchantSpendRow, hidden: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .background(PaisaTheme.colors.accent.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = merchant.merchantName.take(1).uppercase(),
                style = PaisaTheme.typography.bodyBold,
                color = PaisaTheme.colors.accent,
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = PaisaSpacing.tight)) {
            Text(text = merchant.merchantName, style = PaisaTheme.typography.bodyBold, color = PaisaTheme.colors.ink)
            Text(
                text = "${merchant.count} transaction${if (merchant.count == 1) "" else "s"}",
                style = PaisaTheme.typography.caption,
                color = PaisaTheme.colors.inkMuted,
            )
        }
        Text(
            text = if (hidden) maskedRupees() else formatIndianRupees(merchant.amountPaise),
            style = PaisaTheme.typography.amountM,
            color = PaisaTheme.colors.ink,
        )
    }
}

private fun forecastText(monthTotal: Long, forecastPaise: Long, hidden: Boolean): String {
    if (forecastPaise <= monthTotal) return "On track"
    val amount = if (hidden) maskedRupees() else formatIndianRupees(forecastPaise)
    return "Trending to $amount by month end"
}

private fun safeToSpendText(safeToSpendPerDayPaise: Long, hidden: Boolean): String {
    val daysRemaining = (java.time.YearMonth.now().lengthOfMonth() - java.time.LocalDate.now().dayOfMonth + 1)
        .coerceAtLeast(1)
    val amount = if (hidden) maskedRupees() else formatIndianRupees(safeToSpendPerDayPaise)
    return "$amount/day for the next $daysRemaining day${if (daysRemaining == 1) "" else "s"}"
}
