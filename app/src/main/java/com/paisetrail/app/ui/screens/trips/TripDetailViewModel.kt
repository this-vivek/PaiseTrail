package com.paisetrail.app.ui.screens.trips

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paisetrail.app.data.db.CategoryDao
import com.paisetrail.app.data.db.DailySpendRow
import com.paisetrail.app.data.db.TransactionDao
import com.paisetrail.app.data.db.TripDao
import com.paisetrail.app.data.db.TripEntity
import com.paisetrail.app.enrich.CategoryGuesser
import com.paisetrail.app.ui.navigation.Destination
import com.paisetrail.app.ui.screens.map.TxnClusterItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TripCategorySlice(val name: String, val colorHex: String?, val emoji: String?, val amountPaise: Long)

/** Per-category and per-day breakdown for one trip (spec 7.4) — the drill-down from a row in
 * [TripsScreen]'s past-trips list. Distance-ordered polyline map and PDF/image export are
 * deferred (not built in this phase). */
@HiltViewModel
class TripDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripDao: TripDao,
    private val transactionDao: TransactionDao,
    categoryDao: CategoryDao,
) : ViewModel() {
    private val tripId: Long = checkNotNull(savedStateHandle[Destination.TripDetail.ARG_TRIP_ID])

    val trip = tripDao.observeAll()
        .map { trips -> trips.firstOrNull { it.id == tripId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null as TripEntity?)

    val totalPaise = transactionDao.observeCategorySpendForTrip(tripId)
        .map { rows -> rows.sumOf { it.amountPaise } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val categorySlices = combine(
        transactionDao.observeCategorySpendForTrip(tripId),
        categoryDao.observeAll(),
    ) { spends, categories ->
        val categoriesById = categories.associateBy { it.id }
        // Resolve a null categoryId through the real "Uncategorized" row too (see the identical
        // fix in DashboardViewModel.categorySlices) — otherwise it and an explicitly-tagged
        // Uncategorized transaction merge into one slice whose color/emoji depends on group order.
        val uncategorized = categories.firstOrNull { it.name == CategoryGuesser.UNCATEGORIZED }
        spends
            .filter { it.amountPaise > 0 }
            .map { spend ->
                val category = spend.categoryId?.let { categoriesById[it] } ?: uncategorized
                TripCategorySlice(
                    name = category?.name ?: CategoryGuesser.UNCATEGORIZED,
                    colorHex = category?.colorHex,
                    emoji = category?.emoji,
                    amountPaise = spend.amountPaise,
                )
            }
            .groupBy { it.name }
            .map { (name, group) ->
                TripCategorySlice(name, group.first().colorHex, group.first().emoji, group.sumOf { it.amountPaise })
            }
            .sortedByDescending { it.amountPaise }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dailySpend = transactionDao.observeDailySpendForTrip(tripId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<DailySpendRow>())

    val transactions = transactionDao.observeForTrip(tripId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Pins for the trip detail mini-map (spec 7.4 "view the map in each trip") — same
     * [TxnClusterItem] the main Map screen uses, so pin color stays consistent everywhere. */
    val mapItems = combine(transactions, categoryDao.observeAll()) { txns, cats ->
        val categoriesById = cats.associateBy { it.id }
        txns
            .filter { it.lat != null && it.lng != null }
            .map { txn ->
                val category = txn.categoryId?.let { categoriesById[it] }
                TxnClusterItem(txn, category?.colorHex, category?.emoji)
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Unlinks this trip's transactions then deletes it (spec 7.4 "delete trip"). [onDeleted]
     * navigates back — there's nothing left here to show once the trip is gone. */
    fun deleteTrip(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val current = trip.value ?: return@launch
            transactionDao.clearTrip(current.id)
            tripDao.delete(current)
            onDeleted()
        }
    }
}
