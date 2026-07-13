package com.paisetrail.app.ui.screens.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paisetrail.app.data.db.CategoryDao
import com.paisetrail.app.data.db.CategoryEntity
import com.paisetrail.app.data.db.TransactionDao
import com.paisetrail.app.data.db.TransactionEntity
import com.paisetrail.app.data.db.TripDao
import com.paisetrail.app.enrich.TagConfirmationUseCase
import com.paisetrail.app.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the transaction detail screen (spec 7.3) — where it went, when, its UPI ref/trip if any,
 * and a tappable category that re-tags through the same [TagConfirmationUseCase] as everywhere
 * else. */
@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    transactionDao: TransactionDao,
    categoryDao: CategoryDao,
    tripDao: TripDao,
    private val tagConfirmationUseCase: TagConfirmationUseCase,
) : ViewModel() {
    private val txnId: Long = checkNotNull(savedStateHandle[Destination.TransactionDetail.ARG_TXN_ID])

    val txn = transactionDao.observeById(txnId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null as TransactionEntity?)

    val categories = categoryDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<CategoryEntity>())

    val category = combine(txn, categories) { t, cats ->
        t?.categoryId?.let { id -> cats.firstOrNull { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null as CategoryEntity?)

    val tripName = combine(txn, tripDao.observeAll()) { t, trips ->
        t?.tripId?.let { id -> trips.firstOrNull { it.id == id }?.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null as String?)

    fun retag(categoryName: String) {
        viewModelScope.launch { tagConfirmationUseCase.confirmCategory(txnId, categoryName) }
    }
}
