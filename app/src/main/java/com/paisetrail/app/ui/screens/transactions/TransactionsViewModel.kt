package com.paisetrail.app.ui.screens.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paisetrail.app.data.db.CategoryDao
import com.paisetrail.app.data.db.CategoryEntity
import com.paisetrail.app.data.db.TransactionDao
import com.paisetrail.app.data.db.TransactionEntity
import com.paisetrail.app.data.db.TripDao
import com.paisetrail.app.data.db.TripEntity
import com.paisetrail.app.ui.navigation.Destination
import com.paisetrail.app.util.MonthRange
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class TransactionRow(
    val txn: TransactionEntity,
    val categoryName: String?,
    val categoryColorHex: String?,
    val categoryEmoji: String?,
)
data class TransactionDayGroup(val dayLabel: String, val rows: List<TransactionRow>)

private val DAY_KEY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())
private val DAY_LABEL_FORMAT = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
private val MONTH_LABEL_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy")
private const val UNCATEGORIZED_NAME = "Uncategorized"

/** Sentinel [selectedTripId] value meaning "not tagged to any trip" — safe against collision
 * since real trip ids are Room autoincrement (start at 1). */
const val UNTRIPPED_FILTER_ID = -1L

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionDao: TransactionDao,
    categoryDao: CategoryDao,
    tripDao: TripDao,
) : ViewModel() {
    private val searchQuery = MutableStateFlow("")

    // Filtering by category NAME, not id: the Dashboard's category slices already merge a null
    // categoryId (backfilled rows) and the real "Uncategorized" category under one display name,
    // so a tap-through from there needs the same by-name semantics to match what the user saw.
    private val selectedCategoryName = MutableStateFlow(
        savedStateHandle.get<String>(Destination.Transactions.ARG_CATEGORY_NAME),
    )
    private val selectedTripId = MutableStateFlow<Long?>(null)
    private val selectedMonth = MutableStateFlow(YearMonth.now())

    val categories: StateFlow<List<CategoryEntity>> = categoryDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trips: StateFlow<List<TripEntity>> = tripDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val searchQueryState: StateFlow<String> = searchQuery
    val selectedCategoryNameState: StateFlow<String?> = selectedCategoryName
    val selectedTripIdState: StateFlow<Long?> = selectedTripId

    val selectedMonthLabel: StateFlow<String> = selectedMonth
        .map { MONTH_LABEL_FORMAT.format(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MONTH_LABEL_FORMAT.format(YearMonth.now()))

    /** Whether a search/category/trip filter is active — while browsing month-by-month with no
     * filter, only the selected month's transactions load, but the moment any filter is applied
     * the whole point is "show me every match," not just the ones in whatever month happens to be
     * selected, so the window widens to all time. */
    private val isFiltering = combine(searchQuery, selectedCategoryName, selectedTripId) { query, categoryName, tripId ->
        query.isNotBlank() || categoryName != null || tripId != null
    }

    val groupedTransactions: StateFlow<List<TransactionDayGroup>> = combine(
        combine(selectedMonth, isFiltering) { month, filtering -> month to filtering }
            .flatMapLatest { (month, filtering) ->
                if (filtering) {
                    transactionDao.observeInWindow(0L, Long.MAX_VALUE)
                } else {
                    val range = MonthRange.of(month)
                    transactionDao.observeInWindow(range.first, range.last)
                }
            },
        categories,
        searchQuery,
        selectedCategoryName,
        selectedTripId,
    ) { txns, categoryList, query, categoryName, tripId ->
        val categoriesById = categoryList.associateBy { it.id }
        txns
            .filter { txn ->
                when (categoryName) {
                    null -> true
                    UNCATEGORIZED_NAME -> txn.categoryId == null || categoriesById[txn.categoryId]?.name == UNCATEGORIZED_NAME
                    else -> categoriesById[txn.categoryId]?.name == categoryName
                }
            }
            .filter { txn ->
                when (tripId) {
                    null -> true
                    UNTRIPPED_FILTER_ID -> txn.tripId == null
                    else -> txn.tripId == tripId
                }
            }
            .filter { query.isBlank() || it.payeeNameRaw?.contains(query, ignoreCase = true) == true }
            .groupBy { DAY_KEY_FORMAT.format(Instant.ofEpochMilli(it.occurredAt)) }
            .map { (dayKey, dayTxns) ->
                TransactionDayGroup(
                    dayLabel = DAY_LABEL_FORMAT.format(Instant.ofEpochMilli(dayTxns.first().occurredAt)),
                    rows = dayTxns.map { txn ->
                        val category = txn.categoryId?.let { categoriesById[it] }
                        TransactionRow(txn, category?.name, category?.colorHex, category?.emoji)
                    },
                )
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setSelectedCategoryName(name: String?) {
        selectedCategoryName.value = name
    }

    fun setSelectedTripId(tripId: Long?) {
        selectedTripId.value = tripId
    }

    fun previousMonth() {
        selectedMonth.value = selectedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        val next = selectedMonth.value.plusMonths(1)
        if (!next.isAfter(YearMonth.now())) {
            selectedMonth.value = next
        }
    }
}
