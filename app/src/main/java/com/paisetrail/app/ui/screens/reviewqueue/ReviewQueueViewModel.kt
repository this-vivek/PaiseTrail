package com.paisetrail.app.ui.screens.reviewqueue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paisetrail.app.data.db.CategoryDao
import com.paisetrail.app.data.db.CategoryEntity
import com.paisetrail.app.data.db.MerchantDao
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
    private val merchantDao: MerchantDao,
    private val tagConfirmationUseCase: TagConfirmationUseCase,
) : ViewModel() {
    val items: StateFlow<List<ReviewItem>> = combine(
        transactionDao.observeNeedingReview(),
        categoryDao.observeAll(),
        merchantDao.observeAll(),
        transactionDao.observeCategoryUsageFrequency(),
    ) { txns, categories, merchants, usage ->
        val categoriesById = categories.associateBy { it.id }
        val merchantsById = merchants.associateBy { it.id }
        // This user's own most-tagged categories overall, most frequent first — the fallback
        // suggestion pool (TODO: "dynamic to existing taggings") when a merchant has no learned
        // category of its own and no keyword rule matches, instead of a fixed default list.
        val fallbackCategories = usage.sortedByDescending { it.count }
            .mapNotNull { categoriesById[it.categoryId]?.name }
        txns.map { txn ->
            val learnedCategoryName = txn.merchantId
                ?.let { merchantsById[it]?.defaultCategoryId }
                ?.let { categoriesById[it]?.name }
            ReviewItem(
                txn = txn,
                categoryName = txn.categoryId?.let { categoriesById[it]?.name },
                suggestions = CategoryGuesser.topPredictions(
                    txn.payeeNameRaw,
                    txn.vpa,
                    learnedCategoryName = learnedCategoryName,
                    fallbackCategories = fallbackCategories,
                ),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The full category list, for the "choose from all categories" picker — [suggestions] on
     * each [ReviewItem] is only ever the top few predictions. */
    val categories: StateFlow<List<CategoryEntity>> = categoryDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun confirmCategory(txnId: Long, categoryName: String) {
        viewModelScope.launch { tagConfirmationUseCase.confirmCategory(txnId, categoryName) }
    }
}
