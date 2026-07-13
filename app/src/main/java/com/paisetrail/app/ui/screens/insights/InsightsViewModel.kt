package com.paisetrail.app.ui.screens.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paisetrail.app.data.db.BudgetDao
import com.paisetrail.app.data.db.BudgetEntity
import com.paisetrail.app.data.db.CategoryDao
import com.paisetrail.app.data.db.TransactionDao
import com.paisetrail.app.util.DailySeries
import com.paisetrail.app.util.MonthRange
import com.paisetrail.app.util.SpendForecast
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TREND_DAYS = 30

data class BudgetProgress(
    val categoryId: Long,
    val categoryName: String,
    val categoryColorHex: String?,
    val categoryEmoji: String?,
    val spentPaise: Long,
    val limitPaise: Long?,
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    transactionDao: TransactionDao,
    categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
) : ViewModel() {
    private val now = System.currentTimeMillis()
    private val monthStart = MonthRange.of(YearMonth.now()).first
    private val thirtyDaysAgo = now - (TREND_DAYS - 1).toLong() * 24 * 60 * 60 * 1000
    private val last30DayKeys = DailySeries.lastNDays(TREND_DAYS, now)

    val dayLabels: List<String> = last30DayKeys.map { it.takeLast(2) }

    val dailySpend: StateFlow<List<Long>> = transactionDao.observeDailySpend(thirtyDaysAgo)
        .map { rows ->
            val byDay = rows.associate { it.day to it.amountPaise }
            DailySeries.zeroFill(last30DayKeys, byDay, 0L)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), List(TREND_DAYS) { 0L })

    val monthSpend: StateFlow<Long> = transactionDao.observeSpendInWindow(monthStart, now)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** Linear burn-rate projection (spec: reviewed from the Gemini enhancement doc) — assumes the
     * average daily spend seen so far this month continues for the remaining days. */
    val forecastPaise: StateFlow<Long> = monthSpend
        .map { spend ->
            SpendForecast.projectMonthEnd(
                currentSpendPaise = spend,
                daysElapsed = LocalDate.now().dayOfMonth,
                daysInMonth = YearMonth.now().lengthOfMonth(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val budgets: StateFlow<List<BudgetProgress>> = combine(
        transactionDao.observeCategorySpend(monthStart, now),
        categoryDao.observeAll(),
        budgetDao.observeAll(),
    ) { spends, categories, budgetRows ->
        val spendByCategory = spends.mapNotNull { row -> row.categoryId?.let { it to row.amountPaise } }.toMap()
        val limitByCategory = budgetRows.associate { it.categoryId to it.monthlyLimitPaise }
        categories
            .map { category ->
                BudgetProgress(
                    categoryId = category.id,
                    categoryName = category.name,
                    categoryColorHex = category.colorHex,
                    categoryEmoji = category.emoji,
                    spentPaise = spendByCategory[category.id] ?: 0L,
                    limitPaise = limitByCategory[category.id],
                )
            }
            .sortedWith(compareByDescending<BudgetProgress> { it.limitPaise != null }.thenByDescending { it.spentPaise })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Categories at 80%+ of their budget, closest to (or furthest past) the limit first — the
     * Home screen and Insights both surface this so a blown budget can't go unnoticed. */
    val budgetAlerts: StateFlow<List<BudgetProgress>> = budgets
        .map { list ->
            list
                .filter { it.limitPaise != null && it.limitPaise > 0 && it.spentPaise >= it.limitPaise * 0.8 }
                .sortedByDescending { it.spentPaise.toDouble() / it.limitPaise!! }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setBudget(categoryId: Long, limitPaise: Long?) {
        viewModelScope.launch {
            if (limitPaise == null) budgetDao.delete(categoryId) else budgetDao.upsert(BudgetEntity(categoryId, limitPaise))
        }
    }
}
