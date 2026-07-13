package com.paisetrail.app.ui.screens.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paisetrail.app.data.db.CategoryDao
import com.paisetrail.app.data.db.CategoryEntity
import com.paisetrail.app.data.db.TransactionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MapViewModel @Inject constructor(
    transactionDao: TransactionDao,
    categoryDao: CategoryDao,
) : ViewModel() {
    private val selectedCategoryId = MutableStateFlow<Long?>(null)
    private val showTripPins = MutableStateFlow(false)

    val categories: StateFlow<List<CategoryEntity>> = categoryDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Everyday spending is the main map's job — a trip already has its own dedicated mini-map
     * (spec 7.4), so trip-tagged pins are hidden here by default to avoid double-showing the same
     * data, revealed only via the trip-pins toggle. */
    val clusterItems: StateFlow<List<TxnClusterItem>> = combine(
        transactionDao.observeMapped(),
        categories,
        selectedCategoryId,
        showTripPins,
    ) { txns, categoryList, categoryId, showTrips ->
        val categoriesById = categoryList.associateBy { it.id }
        txns
            .filter { categoryId == null || it.categoryId == categoryId }
            .filter { showTrips || it.tripId == null }
            .map { txn ->
                val category = txn.categoryId?.let { categoriesById[it] }
                TxnClusterItem(txn, category?.colorHex, category?.emoji)
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The most recent located transaction — the camera centers here at city-level zoom instead
     * of always opening on a country-wide view of India. */
    val lastLocation: StateFlow<Pair<Double, Double>?> = transactionDao.observeMapped()
        .map { txns -> txns.maxByOrNull { it.occurredAt }?.let { it.lat!! to it.lng!! } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setSelectedCategory(categoryId: Long?) {
        selectedCategoryId.value = categoryId
    }

    fun setShowTripPins(show: Boolean) {
        showTripPins.value = show
    }
}
