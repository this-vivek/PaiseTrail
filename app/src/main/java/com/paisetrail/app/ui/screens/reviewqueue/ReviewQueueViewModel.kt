package com.paisetrail.app.ui.screens.reviewqueue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paisetrail.app.data.db.CategoryDao
import com.paisetrail.app.data.db.TransactionDao
import com.paisetrail.app.data.db.TransactionEntity
import com.paisetrail.app.enrich.CategoryGuesser
import com.paisetrail.app.enrich.TagConfirmationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReviewItem(
    val txn: TransactionEntity,
    val categoryName: String?,
    val suggestions: List<String>,
)

@HiltViewModel
class ReviewQueueViewModel @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val tagConfirmationUseCase: TagConfirmationUseCase,
) : ViewModel() {
    val items: StateFlow<List<ReviewItem>> = combine(
        transactionDao.observeNeedingReview(),
        categoryDao.observeAll(),
    ) { txns, categories ->
        val categoriesById = categories.associateBy { it.id }
        txns.map { txn ->
            ReviewItem(
                txn = txn,
                categoryName = txn.categoryId?.let { categoriesById[it]?.name },
                suggestions = CategoryGuesser.topPredictions(txn.payeeNameRaw, txn.vpa),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun confirmCategory(txnId: Long, categoryName: String) {
        viewModelScope.launch { tagConfirmationUseCase.confirmCategory(txnId, categoryName) }
    }
}
