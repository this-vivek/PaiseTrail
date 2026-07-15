package com.paisetrail.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paisetrail.app.data.db.BudgetDao
import com.paisetrail.app.data.db.CategoryDao
import com.paisetrail.app.data.db.MerchantSpendRow
import com.paisetrail.app.data.db.TransactionDao
import com.paisetrail.app.enrich.CategoryGuesser
import com.paisetrail.app.ui.components.PrivacyPreferences
import com.paisetrail.app.util.DailySeries
import com.paisetrail.app.util.MonthRange
import com.paisetrail.app.util.SpendForecast
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategorySlice(val name: String, val colorHex: String?, val emoji: String?, val amountPaise: Long)

private const val WEEK_DAYS = 7
private val MONTH_LABEL_FORMAT = DateTimeFormatter.ofPattern("MMMM")

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    transactionDao: TransactionDao,
    categoryDao: CategoryDao,
    budgetDao: BudgetDao,
    private val privacyPreferences: PrivacyPreferences,
) : ViewModel() {
    private val now = System.currentTimeMillis()
    private val sevenDaysAgo = now - (WEEK_DAYS - 1).toLong() * 24 * 60 * 60 * 1000
    private val last7DayKeys = DailySeries.lastNDays(WEEK_DAYS, now)

    // Hidden by default (spec 5 TODO "hide amounts") — a shoulder-surfing glance at the phone
    // shouldn't reveal spend; the user explicitly reveals it, not the other way round.
    val amountsHidden: StateFlow<Boolean> = privacyPreferences.amountsHidden
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun toggleAmountsHidden() {
        viewModelScope.launch { privacyPreferences.setAmountsHidden(!amountsHidden.value) }
    }

    // Browsing to a previous month is read-only history; this always resets to the live current
    // month on a fresh launch rather than remembering wherever the user last left it.
    private val selectedMonth = MutableStateFlow(YearMonth.now())

    val selectedMonthLabel: StateFlow<String> = selectedMonth
        .map { MONTH_LABEL_FORMAT.format(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MONTH_LABEL_FORMAT.format(YearMonth.now()))

    val isCurrentMonth: StateFlow<Boolean> = selectedMonth
        .map { it == YearMonth.now() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun previousMonth() {
        selectedMonth.value = selectedMonth.value.minusMonths(1)
    }

    /** No-op once already on the current month — browsing is strictly into the past, there's
     * nothing to show beyond "now" yet. */
    fun nextMonth() {
        val next = selectedMonth.value.plusMonths(1)
        if (!next.isAfter(YearMonth.now())) selectedMonth.value = next
    }

    // A past month's window is its full calendar range; the current month's end is "now" so
    // spend totals don't count days that haven't happened yet.
    private val monthWindow: StateFlow<Pair<Long, Long>> = selectedMonth
        .map { month ->
            if (month == YearMonth.now()) MonthRange.of(month).first to now else MonthRange.of(month).let { it.first to it.last }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthRange.of(YearMonth.now()).first to now)

    val monthTotal: StateFlow<Long> = monthWindow
        .flatMapLatest { (start, end) -> transactionDao.observeSpendInWindow(start, end) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val previousMonthTotal: StateFlow<Long> = selectedMonth
        .flatMapLatest { month ->
            val range = MonthRange.of(month.minusMonths(1))
            transactionDao.observeSpendInWindow(range.first, range.last)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val categorySlices: StateFlow<List<CategorySlice>> = combine(
        monthWindow.flatMapLatest { (start, end) -> transactionDao.observeCategorySpend(start, end) },
        categoryDao.observeAll(),
    ) { spends, categories ->
        val categoriesById = categories.associateBy { it.id }
        // A null categoryId (backfilled rows, which skip enrichment entirely — spec 3.4) displays
        // under the same "Uncategorized" name as a transaction explicitly tagged that category —
        // resolving both through this same real row means they always share its actual color/emoji
        // rather than one of the two silently falling back to a hardcoded null.
        val uncategorized = categories.firstOrNull { it.name == CategoryGuesser.UNCATEGORIZED }
        spends
            .filter { it.amountPaise > 0 }
            .map { spend ->
                val category = spend.categoryId?.let { categoriesById[it] } ?: uncategorized
                CategorySlice(
                    name = category?.name ?: CategoryGuesser.UNCATEGORIZED,
                    colorHex = category?.colorHex,
                    emoji = category?.emoji,
                    amountPaise = spend.amountPaise,
                )
            }
            .groupBy { it.name }
            .map { (name, group) ->
                CategorySlice(name, group.first().colorHex, group.first().emoji, group.sumOf { it.amountPaise })
            }
            .sortedByDescending { it.amountPaise }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Sparkline in the corner of the Aurora hero (spec §4.2) — every calendar day zero-filled so
     * the shape reflects real date gaps, not just which days happened to have spending. Always the
     * last real 7 days regardless of [selectedMonth] browsing — a sparkline for a past month has
     * no "recent" days to show. */
    val weeklySparkline: StateFlow<List<Float>> = transactionDao.observeDailySpend(sevenDaysAgo)
        .map { rows ->
            val byDay = rows.associate { it.day to it.amountPaise }
            DailySeries.zeroFill(last7DayKeys, byDay, 0L).map { it.toFloat() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), List(WEEK_DAYS) { 0f })

    val topMerchants: StateFlow<List<MerchantSpendRow>> = monthWindow
        .flatMapLatest { (start, end) -> transactionDao.observeTopMerchants(start, end) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val reviewCount: StateFlow<Int> = transactionDao.observeNeedingReview()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Categories at 80%+ of their monthly budget — surfaced here too (not just on the Insights
     * screen) so a blown budget can't go unnoticed on the fast Home glance. */
    val budgetAlertCount: StateFlow<Int> = combine(
        monthWindow.flatMapLatest { (start, end) -> transactionDao.observeCategorySpend(start, end) },
        budgetDao.observeAll(),
    ) { spends, budgets ->
        val spendByCategory = spends.mapNotNull { row -> row.categoryId?.let { it to row.amountPaise } }.toMap()
        budgets.count { budget ->
            budget.monthlyLimitPaise > 0 && (spendByCategory[budget.categoryId] ?: 0L) >= budget.monthlyLimitPaise * 0.8
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Linear burn-rate month-end projection, same formula as Insights — only meaningful for the
     * live current month (a past month is already over), gated by [isCurrentMonth] in the screen. */
    val forecastPaise: StateFlow<Long> = monthTotal
        .map { spend ->
            SpendForecast.projectMonthEnd(
                currentSpendPaise = spend,
                daysElapsed = LocalDate.now().dayOfMonth,
                daysInMonth = YearMonth.now().lengthOfMonth(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** (totalBudget, totalSpend) across every category that has a budget set — null when no
     * budgets exist at all. Shared source for [budgetHealthRatio] and [safeToSpendPerDay] so they
     * don't each re-run the same query/combine. */
    private val budgetTotals: StateFlow<Pair<Long, Long>?> = combine(
        monthWindow.flatMapLatest { (start, end) -> transactionDao.observeCategorySpend(start, end) },
        budgetDao.observeAll(),
    ) { spends, budgets ->
        if (budgets.isEmpty()) return@combine null
        val spendByCategory = spends.mapNotNull { row -> row.categoryId?.let { it to row.amountPaise } }.toMap()
        val totalBudget = budgets.sumOf { it.monthlyLimitPaise }
        val totalSpend = budgets.sumOf { spendByCategory[it.categoryId] ?: 0L }
        if (totalBudget <= 0L) null else totalBudget to totalSpend
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Null when no budgets are configured at all (the Aurora then defaults to calm — nothing to
     * measure against yet); otherwise month-to-date spend as a fraction of the total budget across
     * every category that has one, driving the hero's hue (calm/mid/hot). */
    val budgetHealthRatio: StateFlow<Float?> = budgetTotals
        .map { totals -> totals?.let { (budget, spend) -> spend.toFloat() / budget.toFloat() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** "Safe to spend" per day for the rest of the month (spec §5.3) — (total budget − MTD spend)
     * / days remaining. Null with no budgets configured, or zero/negative when already over. Only
     * meaningful for the live current month, gated by [isCurrentMonth] in the screen. */
    val safeToSpendPerDay: StateFlow<Long?> = budgetTotals
        .map { totals ->
            totals?.let { (budget, spend) ->
                val daysRemaining = (YearMonth.now().lengthOfMonth() - LocalDate.now().dayOfMonth + 1).coerceAtLeast(1)
                ((budget - spend) / daysRemaining).coerceAtLeast(0L)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
