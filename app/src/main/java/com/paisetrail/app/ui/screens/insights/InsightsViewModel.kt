package com.paisetrail.app.ui.screens.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paisetrail.app.data.db.MonthlySpendRow
import com.paisetrail.app.data.db.TransactionDao
import com.paisetrail.app.data.db.TransactionEntity
import com.paisetrail.app.ui.components.PrivacyPreferences
import com.paisetrail.app.util.DailySeries
import com.paisetrail.app.util.MonthRange
import com.paisetrail.app.util.SpendForecast
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private const val TREND_DAYS = 30

@HiltViewModel
class InsightsViewModel @Inject constructor(
    transactionDao: TransactionDao,
    privacyPreferences: PrivacyPreferences,
) : ViewModel() {
    private val now = System.currentTimeMillis()

    // Same toggle as the Home screen (spec 5 TODO) — Insights would otherwise be a loophole back
    // to every amount Home just hid.
    val amountsHidden: StateFlow<Boolean> = privacyPreferences.amountsHidden
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    private val monthStart = MonthRange.of(YearMonth.now()).first
    private val sixMonthsAgo = MonthRange.of(YearMonth.now().minusMonths(5)).first
    private val thirtyDaysAgo = now - (TREND_DAYS - 1).toLong() * 24 * 60 * 60 * 1000
    private val last30DayKeys = DailySeries.lastNDays(TREND_DAYS, now)

    val dayLabels: List<String> = last30DayKeys.map { it.takeLast(2) }

    val dailySpend: StateFlow<List<Long>> = transactionDao.observeDailySpend(thirtyDaysAgo)
        .map { rows ->
            val byDay = rows.associate { it.day to it.amountPaise }
            DailySeries.zeroFill(last30DayKeys, byDay, 0L)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), List(TREND_DAYS) { 0L })

    /** 6-month trend bar chart (moved here from the Dashboard so Home stays a fast glance and the
     * full history lives with the rest of the charts). */
    val monthlyTrend: StateFlow<List<MonthlySpendRow>> = transactionDao.observeMonthlySpend(sixMonthsAgo)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val monthSpend: StateFlow<Long> = transactionDao.observeSpendInWindow(monthStart, now)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val largestSpend: StateFlow<TransactionEntity?> = transactionDao.observeLargestSpend(monthStart, now)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Weekday total to weekend total, over the last 30 days — derived from [dailySpend] rather
     * than a separate query since we already have a zero-filled per-day series. */
    val weekdayWeekendSplit: StateFlow<Pair<Long, Long>> = dailySpend
        .map { amounts ->
            var weekday = 0L
            var weekend = 0L
            last30DayKeys.forEachIndexed { index, dayKey ->
                val date = LocalDate.parse(dayKey, DateTimeFormatter.ISO_LOCAL_DATE)
                if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) {
                    weekend += amounts[index]
                } else {
                    weekday += amounts[index]
                }
            }
            weekday to weekend
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L to 0L)

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
}
