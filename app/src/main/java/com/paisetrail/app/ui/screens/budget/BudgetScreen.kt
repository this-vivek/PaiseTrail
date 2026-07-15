package com.paisetrail.app.ui.screens.budget

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
import com.paisetrail.app.ui.components.CategoryDot
import com.paisetrail.app.ui.components.PaisaCard
import com.paisetrail.app.ui.components.StatBlock
import com.paisetrail.app.ui.components.formatIndianRupees
import com.paisetrail.app.ui.components.maskedRupees
import com.paisetrail.app.ui.components.parseCategoryColor
import com.paisetrail.app.ui.theme.ChipShape
import com.paisetrail.app.ui.theme.PaisaSpacing
import com.paisetrail.app.ui.theme.PaisaTheme
import com.paisetrail.app.ui.theme.SheetShape

/** Its own space (spec 5 TODO), split out of the Insights feed: an overall this-month summary,
 * anything at or over 80% of its limit, then every category's progress bar — tap any row to set
 * or change its limit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(onBack: () -> Unit, viewModel: BudgetViewModel = hiltViewModel()) {
    val budgets by viewModel.budgets.collectAsState()
    val budgetAlerts by viewModel.budgetAlerts.collectAsState()
    val overallTotals by viewModel.overallTotals.collectAsState()
    val amountsHidden by viewModel.amountsHidden.collectAsState()
    var editingBudget by remember { mutableStateOf<BudgetProgress?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Budget", style = PaisaTheme.typography.title) },
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
            overallTotals?.let { (totalBudget, totalSpend) ->
                PaisaCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatBlock(label = "Spent so far") {
                            AmountText(amountPaise = totalSpend, style = PaisaTheme.typography.amountL, masked = amountsHidden)
                        }
                        StatBlock(label = "Total budget") {
                            AmountText(amountPaise = totalBudget, style = PaisaTheme.typography.amountL, masked = amountsHidden)
                        }
                    }
                    val fraction = (totalSpend.toFloat() / totalBudget).coerceIn(0f, 1f)
                    val overBudget = totalSpend > totalBudget
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = PaisaSpacing.normal)
                            .height(8.dp)
                            .background(PaisaTheme.colors.surface2, ChipShape),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(8.dp)
                                .background(if (overBudget) PaisaTheme.colors.negative else PaisaTheme.colors.accent, ChipShape),
                        )
                    }
                    Text(
                        text = if (overBudget) {
                            val amount = if (amountsHidden) maskedRupees() else formatIndianRupees(totalSpend - totalBudget)
                            "$amount over your combined budget"
                        } else {
                            val amount = if (amountsHidden) maskedRupees() else formatIndianRupees(totalBudget - totalSpend)
                            "$amount left across every budgeted category"
                        },
                        style = PaisaTheme.typography.caption,
                        color = if (overBudget) PaisaTheme.colors.negative else PaisaTheme.colors.inkMuted,
                        modifier = Modifier.padding(top = PaisaSpacing.tight),
                    )
                }
            } ?: Text(
                text = "Set a monthly limit on any category below to start tracking it here",
                style = PaisaTheme.typography.caption,
                color = PaisaTheme.colors.inkMuted,
            )

            if (budgetAlerts.isNotEmpty()) {
                SectionLabel("Needs attention")
                PaisaCard(modifier = Modifier.fillMaxWidth().padding(top = PaisaSpacing.tight)) {
                    budgetAlerts.forEachIndexed { index, alert ->
                        BudgetAlertRow(alert, hidden = amountsHidden, onClick = { editingBudget = alert })
                        if (index != budgetAlerts.lastIndex) Box(modifier = Modifier.height(PaisaSpacing.tight))
                    }
                }
            }

            SectionLabel("Every category")
            if (budgets.isEmpty()) {
                Text(
                    text = "No categories yet",
                    style = PaisaTheme.typography.caption,
                    color = PaisaTheme.colors.inkMuted,
                    modifier = Modifier.padding(top = PaisaSpacing.tight),
                )
            } else {
                PaisaCard(modifier = Modifier.fillMaxWidth().padding(top = PaisaSpacing.tight)) {
                    budgets.forEachIndexed { index, budget ->
                        BudgetRow(budget, hidden = amountsHidden, onClick = { editingBudget = budget })
                        if (index != budgets.lastIndex) Box(modifier = Modifier.height(PaisaSpacing.normal))
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
        style = PaisaTheme.typography.label,
        color = PaisaTheme.colors.inkMuted,
        modifier = Modifier.padding(top = PaisaSpacing.loose),
    )
}

@Composable
private fun BudgetAlertRow(alert: BudgetProgress, hidden: Boolean, onClick: () -> Unit) {
    val limitPaise = alert.limitPaise ?: return
    val overBudget = alert.spentPaise > limitPaise
    val percent = ((alert.spentPaise.toDouble() / limitPaise) * 100).toInt()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                (if (overBudget) PaisaTheme.colors.negative else PaisaTheme.colors.warning).copy(alpha = 0.12f),
                ChipShape,
            )
            .padding(PaisaSpacing.tight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryDot(alert.categoryColorHex, alert.categoryEmoji)
            Text(
                text = alert.categoryName,
                style = PaisaTheme.typography.bodyBold,
                color = PaisaTheme.colors.ink,
                modifier = Modifier.padding(start = PaisaSpacing.tight),
            )
        }
        Text(
            text = if (overBudget) {
                val amount = if (hidden) maskedRupees() else formatIndianRupees(alert.spentPaise - limitPaise)
                "$amount over budget"
            } else {
                "$percent% of budget used"
            },
            style = PaisaTheme.typography.caption,
            color = if (overBudget) PaisaTheme.colors.negative else PaisaTheme.colors.warning,
        )
    }
}

@Composable
private fun BudgetRow(budget: BudgetProgress, hidden: Boolean, onClick: () -> Unit) {
    val categoryColor = parseCategoryColor(budget.categoryColorHex)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                    style = PaisaTheme.typography.bodyBold,
                    color = PaisaTheme.colors.ink,
                    modifier = Modifier.padding(start = PaisaSpacing.tight),
                )
            }
            Text(
                text = if (hidden) {
                    maskedRupees()
                } else if (budget.limitPaise != null) {
                    "${formatIndianRupees(budget.spentPaise)} / ${formatIndianRupees(budget.limitPaise)}"
                } else {
                    "${formatIndianRupees(budget.spentPaise)} · no budget set"
                },
                style = PaisaTheme.typography.caption,
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
                    .background(categoryColor.copy(alpha = 0.14f), ChipShape),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .background(if (overBudget) PaisaTheme.colors.negative else categoryColor, ChipShape),
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
                .background(PaisaTheme.colors.surface1, SheetShape),
        ) {
            Column(modifier = Modifier.padding(PaisaSpacing.gutter)) {
                Text(
                    text = "Monthly budget · ${budget.categoryName}",
                    style = PaisaTheme.typography.label,
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
                        .background(PaisaTheme.colors.surface2, ChipShape)
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
                            style = PaisaTheme.typography.bodyBold,
                            color = PaisaTheme.colors.negative,
                            modifier = Modifier.clickable { onSave(null) },
                        )
                    } else {
                        Box(modifier = Modifier)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(PaisaSpacing.loose)) {
                        Text(
                            text = "Cancel",
                            style = PaisaTheme.typography.bodyBold,
                            color = PaisaTheme.colors.inkMuted,
                            modifier = Modifier.clickable(onClick = onDismiss),
                        )
                        Text(
                            text = "Save",
                            style = PaisaTheme.typography.bodyBold,
                            color = PaisaTheme.colors.accent,
                            modifier = Modifier.clickable(onClick = save),
                        )
                    }
                }
            }
        }
    }
}
