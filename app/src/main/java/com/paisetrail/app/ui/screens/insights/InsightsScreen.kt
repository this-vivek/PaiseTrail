package com.paisetrail.app.ui.screens.insights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.paisetrail.app.ui.components.AmountText
import com.paisetrail.app.ui.components.AuroraAreaChart
import com.paisetrail.app.ui.components.BarChart
import com.paisetrail.app.ui.components.PaisaCard
import com.paisetrail.app.ui.components.StatBlock
import com.paisetrail.app.ui.components.formatIndianRupees
import com.paisetrail.app.ui.components.maskedRupees
import com.paisetrail.app.ui.theme.PaisaSpacing
import com.paisetrail.app.ui.theme.PaisaTheme

/** The comprehensive-charts-and-forecasting home away from the fast Dashboard glance (spec §4.3):
 * a vertical feed of [PaisaCard]s — headline stats, the 30-day and 6-month trends (moved here from
 * Dashboard so Home stays a quick glance), a weekday-vs-weekend split, and largest single spend.
 * Budgets have their own screen (spec 5 TODO) — reached via the "Budget →" link below. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(onBack: () -> Unit, onNavigateToBudget: () -> Unit, viewModel: InsightsViewModel = hiltViewModel()) {
    val dailySpend by viewModel.dailySpend.collectAsState()
    val monthlyTrend by viewModel.monthlyTrend.collectAsState()
    val monthSpend by viewModel.monthSpend.collectAsState()
    val forecastPaise by viewModel.forecastPaise.collectAsState()
    val largestSpend by viewModel.largestSpend.collectAsState()
    val weekdayWeekendSplit by viewModel.weekdayWeekendSplit.collectAsState()
    val amountsHidden by viewModel.amountsHidden.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Insights", style = PaisaTheme.typography.title) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(PaisaSpacing.gutter),
        ) {
            PaisaCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatBlock(label = "So far this month") {
                        AmountText(amountPaise = monthSpend, style = PaisaTheme.typography.amountL, masked = amountsHidden)
                    }
                    StatBlock(label = "Projected by month end") {
                        AmountText(amountPaise = forecastPaise, style = PaisaTheme.typography.amountL, masked = amountsHidden)
                    }
                }
                Text(
                    text = "Based on your average daily spend so far this month",
                    style = PaisaTheme.typography.caption,
                    color = PaisaTheme.colors.inkMuted,
                    modifier = Modifier.padding(top = PaisaSpacing.tight),
                )
            }

            Text(
                text = "Budget →",
                style = PaisaTheme.typography.bodyBold,
                color = PaisaTheme.colors.accent,
                modifier = Modifier.clickable(onClick = onNavigateToBudget).padding(top = PaisaSpacing.tight),
            )

            SectionLabel("Last 30 days")
            PaisaCard(modifier = Modifier.fillMaxWidth().padding(top = PaisaSpacing.tight)) {
                AuroraAreaChart(
                    values = dailySpend.map { it.toFloat() },
                    dayLabels = viewModel.dayLabels,
                    lineColor = PaisaTheme.colors.accent,
                    formatValue = { if (amountsHidden) maskedRupees() else formatIndianRupees(it.toLong()) },
                )
            }

            SectionLabel("6-month trend")
            PaisaCard(modifier = Modifier.fillMaxWidth().padding(top = PaisaSpacing.tight)) {
                if (monthlyTrend.size < 2) {
                    Text(
                        text = "Not enough history yet",
                        style = PaisaTheme.typography.caption,
                        color = PaisaTheme.colors.inkMuted,
                    )
                } else {
                    BarChart(
                        values = monthlyTrend.map { it.amountPaise.toFloat() },
                        barColor = PaisaTheme.colors.surface2,
                        baselineColor = PaisaTheme.colors.hairline,
                        dayLabels = monthlyTrend.map { monthShortLabel(it.month) },
                        labelColor = PaisaTheme.colors.inkMuted,
                        showAllLabels = true,
                        showValueLabels = true,
                        highlightLastBar = true,
                        highlightColor = PaisaTheme.colors.accent,
                        formatValue = { if (amountsHidden) maskedRupees() else formatIndianRupees(it.toLong()) },
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = PaisaSpacing.tight), horizontalArrangement = Arrangement.spacedBy(PaisaSpacing.tight)) {
                PaisaCard(modifier = Modifier.weight(1f)) {
                    StatBlock(label = "Weekdays") {
                        AmountText(amountPaise = weekdayWeekendSplit.first, style = PaisaTheme.typography.amountM, masked = amountsHidden)
                    }
                }
                PaisaCard(modifier = Modifier.weight(1f)) {
                    StatBlock(label = "Weekends") {
                        AmountText(amountPaise = weekdayWeekendSplit.second, style = PaisaTheme.typography.amountM, masked = amountsHidden)
                    }
                }
            }

            largestSpend?.let { txn ->
                PaisaCard(modifier = Modifier.fillMaxWidth().padding(top = PaisaSpacing.tight)) {
                    StatBlock(label = "Largest spend this month") {
                        AmountText(amountPaise = txn.amountPaise, style = PaisaTheme.typography.amountM, masked = amountsHidden)
                    }
                    Text(
                        text = txn.payeeNameRaw ?: txn.vpa ?: "Unknown",
                        style = PaisaTheme.typography.caption,
                        color = PaisaTheme.colors.inkMuted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = PaisaTheme.typography.label,
        color = PaisaTheme.colors.inkMuted,
        modifier = Modifier.padding(top = PaisaSpacing.loose),
    )
}

/** "2026-07" -> "Jul" for the 6-month bar chart's X axis. */
private fun monthShortLabel(yearMonth: String): String {
    val month = yearMonth.takeLast(2).toIntOrNull() ?: return yearMonth
    val names = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    return names.getOrElse(month - 1) { yearMonth }
}
