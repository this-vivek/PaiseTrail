package com.paisetrail.app.enrich

import com.paisetrail.app.data.db.CategoryDao
import com.paisetrail.app.data.db.TagSource
import com.paisetrail.app.data.db.TransactionDao
import javax.inject.Inject
import javax.inject.Singleton

/** Every place a category gets confirmed for a transaction — the tag notification's action
 * buttons, the overlay's buttons, Review Queue's one-tap confirm — funnels through here so the
 * merchant learning loop (spec 4.2) can't accidentally be wired up in only some of them. */
@Singleton
class TagConfirmationUseCase @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val merchantResolver: MerchantResolver,
) {
    suspend fun confirmCategory(txnId: Long, categoryName: String) {
        val txn = transactionDao.getById(txnId) ?: return
        val category = categoryDao.getByName(categoryName) ?: return
        transactionDao.update(txn.copy(categoryId = category.id, tagSource = TagSource.USER))
        txn.merchantId?.let { merchantResolver.learnCategory(it, category.id) }
    }

    /** The tag popup's delete action (spec 8 #10-adjacent) — a false-positive capture (a
     * promotional message that slipped past the parser, a duplicate) is most obvious right when
     * the popup appears, so removing it shouldn't require a trip into the transaction list. */
    suspend fun deleteTransaction(txnId: Long) {
        transactionDao.deleteById(txnId)
    }
}
