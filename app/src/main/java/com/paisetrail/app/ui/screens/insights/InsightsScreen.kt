package com.paisetrail.app.ui.screens.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.paisetrail.app.ui.components.AmountText
import com.paisetrail.app.ui.components.BarChart
import com.paisetrail.app.ui.components.CategoryDot
import com.paisetrail.app.ui.components.StatBlock
import com.paisetrail.app.ui.components.formatIndianRupees
import com.paisetrail.app.ui.theme.PaisaShape
import com.paisetrail.app.ui.theme.PaisaSpacing
import com.paisetrail.app.ui.theme.PaisaTheme

/** The comprehensive-charts-and-forecasting home away from the fast Dashboard glance: 30-day
 * spend trend, per-category budgets (set from the app, spec 3.2's "data not code" philosophy
 * extended to budgets), and a linear burn-rate month-end forecast. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(onBack: () -> Unit, viewModel: InsightsViewModel = hiltViewModel()) {
    val dailySpend by viewModel.dailySpend.collectAsState()
    val monthSpend by viewModel.monthSpend.collectAsState()
    val forecastPaise by viewModel.forecastPaise.collectAsState()
    val budgets by viewModel.budgets.collectAsState()
    val budgetAlerts by viewModel.budgetAlerts.collectAsState()
    var editingBudget by remember { mutableStateOf<BudgetProgress?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Insights", style = PaisaTheme.typography.body) },
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
            StatBlock(label = "So far this month") {
                AmountText(amountPaise = monthSpend, style = PaisaTheme.typography.amountListHeader)
            }
            StatBlock(label = "Projected by month end", modifier = Modifier.padding(top = PaisaSpacing.normal)) {
                AmountText(amountPaise = forecastPaise, style = PaisaTheme.typography.amountListHeader)
            }
            Text(
                text = "Based on your average daily spend so far this month",
                style = PaisaTheme.typography.bodySecondary,
                color = PaisaTheme.colors.inkMuted,
                modifier = Modifier.padding(top = PaisaSpacing.tight),
            )

            if (budgetAlerts.isNotEmpty()) {
                SectionLabel("Budget alerts")
                Column(modifier = Modifier.padding(top = PaisaSpacing.normal)) {
                    budgetAlerts.forEach { alert ->
                        BudgetAlertRow(alert, onClick = { editingBudget = alert })
                    }
                }
            }

            SectionLabel("Last 30 days")
            BarChart(
                values = dailySpend.map { it.toFloat() },
                barColor = PaisaTheme.colors.ink,
                baselineColor = PaisaTheme.colors.hairline,
                modifier = Modifier.padding(top = PaisaSpacing.normal),
                dayLabels = viewModel.dayLabels,
                labelColor = PaisaTheme.colors.inkMuted,
            )

            SectionLabel("Budgets")
            if (budgets.isEmpty()) {
                Text(
                    text = "No categories yet",
                    style = PaisaTheme.typography.bodySecondary,
                    color = PaisaTheme.colors.inkMuted,
                    modifier = Modifier.padding(top = PaisaSpacing.tight),
                )
            } else {
                Column(modifier = Modifier.padding(top = PaisaSpacing.normal)) {
                    budgets.forEach { budget ->
                        BudgetRow(budget, onClick = { editingBudget = budget })
                    }
                }
            }
        }
    }

    editingBudget?.let { budget ->
        BudgetEditSheet(
            budget = budget,
            onDismiss = { editingBudget = null },
            onSave = { limitPaise ->
                viewModel.setBudget(budget.categoryId, limitPaise)
                editingBudget = null
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = PaisaTheme.typography.overline,
        color = PaisaTheme.colors.inkMuted,
        modifier = Modifier.padding(top = PaisaSpacing.loose),
    )
}

@Composable
private fun BudgetAlertRow(alert: BudgetProgress, onClick: () -> Unit) {
    val limitPaise = alert.limitPaise ?: return
    val overBudget = alert.spentPaise > limitPaise
    val percent = ((alert.spentPaise.toDouble() / limitPaise) * 100).toInt()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                (if (overBudget) PaisaTheme.colors.negative else PaisaTheme.colors.accent).copy(alpha = 0.1f),
                PaisaShape,
            )
            .padding(PaisaSpacing.tight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryDot(alert.categoryColorHex, alert.categoryEmoji)
            Text(
                text = alert.categoryName,
                style = PaisaTheme.typography.body,
                color = PaisaTheme.colors.ink,
                modifier = Modifier.padding(start = PaisaSpacing.tight),
            )
        }
        Text(
            text = if (overBudget) {
                "${formatIndianRupees(alert.spentPaise - limitPaise)} over budget"
            } else {
                "$percent% of budget used"
            },
            style = PaisaTheme.typography.bodySecondary,
            color = if (overBudget) PaisaTheme.colors.negative else PaisaTheme.colors.accent,
        )
    }
    Box(modifier = Modifier.height(PaisaSpacing.tight))
}

@Composable
private fun BudgetRow(budget: BudgetProgress, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = PaisaSpacing.tight),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryDot(budget.categoryColorHex, budget.categoryEmoji)
                Text(
                    text = budget.categoryName,
                    style = PaisaTheme.typography.body,
                    color = PaisaTheme.colors.ink,
                    modifier = Modifier.padding(start = PaisaSpacing.tight),
                )
            }
            Text(
                text = if (budget.limitPaise != null) {
                    "${formatIndianRupees(budget.spentPaise)} / ${formatIndianRupees(budget.limitPaise)}"
                } else {
                    "${formatIndianRupees(budget.spentPaise)} · no budget set"
                },
                style = PaisaTheme.typography.bodySecondary,
                color = PaisaTheme.colors.inkMuted,
            )
        }
        if (budget.limitPaise != null && budget.limitPaise > 0) {
            val fraction = (budget.spentPaise.toFloat() / budget.limitPaise).coerceIn(0f, 1f)
            val overBudget = budget.spentPaise > budget.limitPaise
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(6.dp)
                    .background(PaisaTheme.colors.hairline, RoundedCornerShape(50)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .background(
                            if (overBudget) PaisaTheme.colors.negative else PaisaTheme.colors.accent,
                            RoundedCornerShape(50),
                        ),
                )
            }
        }
    }
}

@Composable
private fun BudgetEditSheet(budget: BudgetProgress, onDismiss: () -> Unit, onSave: (Long?) -> Unit) {
    var text by remember(budget.categoryId) {
        mutableStateOf(budget.limitPaise?.let { (it / 100).toString() } ?: "")
    }
    val save = {
        val rupees = text.toLongOrNull()
        if (rupees != null && rupees > 0) onSave(rupees * 100)
    }

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
                .background(PaisaTheme.colors.surface, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
        ) {
            Column(modifier = Modifier.padding(PaisaSpacing.gutter)) {
                Text(
                    text = "Monthly budget · ${budget.categoryName}",
                    style = PaisaTheme.typography.overline,
                    color = PaisaTheme.colors.inkMuted,
                    modifier = Modifier.padding(bottom = PaisaSpacing.tight),
                )
                BasicTextField(
                    value = text,
                    onValueChange = { input -> text = input.filter { it.isDigit() } },
                    textStyle = TextStyle(color = PaisaTheme.colors.ink, fontSize = PaisaTheme.typography.body.fontSize),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PaisaTheme.colors.bg, PaisaShape)
                        .padding(PaisaSpacing.tight),
                    decorationBox = { inner ->
                        if (text.isEmpty()) {
                            Text("Amount in rupees", style = PaisaTheme.typography.body, color = PaisaTheme.colors.inkMuted)
                        }
                        inner()
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = PaisaSpacing.normal),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (budget.limitPaise != null) {
                        Text(
                            text = "Clear budget",
                            style = PaisaTheme.typography.body,
                            color = PaisaTheme.colors.negative,
                            modifier = Modifier.clickable { onSave(null) },
                        )
                    } else {
                        Box(modifier = Modifier)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(PaisaSpacing.loose)) {
                        Text(
                            text = "Cancel",
                            style = PaisaTheme.typography.body,
                            color = PaisaTheme.colors.inkMuted,
                            modifier = Modifier.clickable(onClick = onDismiss),
                        )
                        Text(
                            text = "Save",
                            style = PaisaTheme.typography.body,
                            color = PaisaTheme.colors.accent,
                            modifier = Modifier.clickable(onClick = save),
                        )
                    }
                }
            }
        }
    }
}
