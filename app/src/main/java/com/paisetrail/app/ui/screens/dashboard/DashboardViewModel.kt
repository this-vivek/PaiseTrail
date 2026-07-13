package com.paisetrail.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paisetrail.app.data.db.BudgetDao
import com.paisetrail.app.data.db.CategoryDao
import com.paisetrail.app.data.db.MerchantSpendRow
import com.paisetrail.app.data.db.MonthlySpendRow
import com.paisetrail.app.data.db.TransactionDao
import com.paisetrail.app.util.DailySeries
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class CategorySlice(val name: String, val colorHex: String?, val emoji: String?, val amountPaise: Long)
data class WeekDay(val dayLabel: String, val segments: List<Pair<String?, Long>>)

private const val WEEK_DAYS = 7

@HiltViewModel
class DashboardViewModel @Inject constructor(
    transactionDao: TransactionDao,
    categoryDao: CategoryDao,
    budgetDao: BudgetDao,
) : ViewModel() {
    private val now = System.currentTimeMillis()
    private val monthStart = DateWindows.startOfMonth(now)
    private val prevMonthStart = DateWindows.startOfMonth(now, monthsAgo = 1)
    private val sevenDaysAgo = now - (WEEK_DAYS - 1).toLong() * 24 * 60 * 60 * 1000
    private val sixMonthsAgo = DateWindows.startOfMonth(now, monthsAgo = 5)
    private val last7DayKeys = DailySeries.lastNDays(WEEK_DAYS, now)

    val monthTotal: StateFlow<Long> = transactionDao.observeSpendInWindow(monthStart, now)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val previousMonthTotal: StateFlow<Long> = transactionDao.observeSpendInWindow(prevMonthStart, monthStart - 1)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val categorySlices: StateFlow<List<CategorySlice>> = combine(
        transactionDao.observeCategorySpend(monthStart, now),
        categoryDao.observeAll(),
    ) { spends, categories ->
        val categoriesById = categories.associateBy { it.id }
        spends
            .filter { it.amountPaise > 0 }
            .map { spend ->
                val category = spend.categoryId?.let { categoriesById[it] }
                CategorySlice(
                    name = category?.name ?: "Uncategorized",
                    colorHex = category?.colorHex,
                    emoji = category?.emoji,
                    amountPaise = spend.amountPaise,
                )
            }
            // A null categoryId (backfilled rows, which skip enrichment entirely — spec 3.4) and
            // a transaction explicitly guessed as the "Uncategorized" category both display under
            // that same name; without merging them here they'd show as two identical-looking
            // slices with different underlying ids.
            .groupBy { it.name }
            .map { (name, group) ->
                CategorySlice(name, group.first().colorHex, group.first().emoji, group.sumOf { it.amountPaise })
            }
            .sortedByDescending { it.amountPaise }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** "Last 7 days" stacked-by-category chart (spec 7.1) — every calendar day in the window gets
     * a slot (zero-filled via [DailySeries]) so bars stay evenly spaced by real date, not just by
     * which days happened to have spending. */
    val weeklyStackedSpend: StateFlow<List<WeekDay>> = combine(
        transactionDao.observeDailyCategorySpend(sevenDaysAgo),
        categoryDao.observeAll(),
    ) { rows, categories ->
        val categoriesById = categories.associateBy { it.id }
        val byDay = rows.groupBy { it.day }
        last7DayKeys.map { day ->
            val segments = byDay[day].orEmpty()
                .sortedByDescending { it.amountPaise }
                .map { row -> row.categoryId?.let { categoriesById[it]?.colorHex } to row.amountPaise }
            WeekDay(dayLabel = day.takeLast(2), segments = segments)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Legend for [weeklyStackedSpend] — which color is which category, same merge-by-name rule
     * as the monthly [categorySlices]. */
    val weeklyCategorySlices: StateFlow<List<CategorySlice>> = combine(
        transactionDao.observeDailyCategorySpend(sevenDaysAgo),
        categoryDao.observeAll(),
    ) { rows, categories ->
        val categoriesById = categories.associateBy { it.id }
        rows
            .filter { it.amountPaise > 0 }
            .map { row ->
                val category = row.categoryId?.let { categoriesById[it] }
                CategorySlice(category?.name ?: "Uncategorized", category?.colorHex, category?.emoji, row.amountPaise)
            }
            .groupBy { it.name }
            .map { (name, group) ->
                CategorySlice(name, group.first().colorHex, group.first().emoji, group.sumOf { it.amountPaise })
            }
            .sortedByDescending { it.amountPaise }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val monthlyTrend: StateFlow<List<MonthlySpendRow>> = transactionDao.observeMonthlySpend(sixMonthsAgo)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val topMerchants: StateFlow<List<MerchantSpendRow>> = transactionDao.observeTopMerchants(monthStart, now)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val reviewCount: StateFlow<Int> = transactionDao.observeNeedingReview()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Categories at 80%+ of their monthly budget — surfaced here too (not just on the Insights
     * screen) so a blown budget can't go unnoticed on the fast Home glance. */
    val budgetAlertCount: StateFlow<Int> = combine(
        transactionDao.observeCategorySpend(monthStart, now),
        budgetDao.observeAll(),
    ) { spends, budgets ->
        val spendByCategory = spends.mapNotNull { row -> row.categoryId?.let { it to row.amountPaise } }.toMap()
        budgets.count { budget ->
            budget.monthlyLimitPaise > 0 && (spendByCategory[budget.categoryId] ?: 0L) >= budget.monthlyLimitPaise * 0.8
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
