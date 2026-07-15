package com.paisetrail.app.ui.screens.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paisetrail.app.data.db.BudgetDao
import com.paisetrail.app.data.db.BudgetEntity
import com.paisetrail.app.data.db.CategoryDao
import com.paisetrail.app.data.db.TransactionDao
import com.paisetrail.app.ui.components.PrivacyPreferences
import com.paisetrail.app.util.MonthRange
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BudgetProgress(
    val categoryId: Long,
    val categoryName: String,
    val categoryColorHex: String?,
    val categoryEmoji: String?,
    val spentPaise: Long,
    val limitPaise: Long?,
)

/** Its own screen rather than folded into Insights (spec 5 TODO) — setting and reviewing
 * per-category limits is a distinct task from browsing charts, and deserves a focused space
 * rather than competing with them for scroll real estate. */
@HiltViewModel
class BudgetViewModel @Inject constructor(
    transactionDao: TransactionDao,
    categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
    privacyPreferences: PrivacyPreferences,
) : ViewModel() {
    private val now = System.currentTimeMillis()
    private val monthStart = MonthRange.of(YearMonth.now()).first

    // Same toggle as the Home screen (spec 5 TODO) — Budget would otherwise be a loophole back to
    // every amount Home just hid.
    val amountsHidden: StateFlow<Boolean> = privacyPreferences.amountsHidden
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

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
     * Home screen surfaces the same count so a blown budget can't go unnoticed there either. */
    val budgetAlerts: StateFlow<List<BudgetProgress>> = budgets
        .map { list ->
            list
                .filter { it.limitPaise != null && it.limitPaise > 0 && it.spentPaise >= it.limitPaise * 0.8 }
                .sortedByDescending { it.spentPaise.toDouble() / it.limitPaise!! }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** (totalBudget, totalSpend) across every category that has a limit set — null when none do —
     * an at-a-glance overall read before scanning the per-category rows below it. */
    val overallTotals: StateFlow<Pair<Long, Long>?> = budgets
        .map { list ->
            val withLimit = list.filter { it.limitPaise != null && it.limitPaise > 0 }
            if (withLimit.isEmpty()) return@map null
            withLimit.sumOf { it.limitPaise!! } to withLimit.sumOf { it.spentPaise }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setBudget(categoryId: Long, limitPaise: Long?) {
        viewModelScope.launch {
            if (limitPaise == null) budgetDao.delete(categoryId) else budgetDao.upsert(BudgetEntity(categoryId, limitPaise))
        }
    }
}
