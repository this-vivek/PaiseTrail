package com.paisetrail.app.enrich

import com.paisetrail.app.data.db.CategoryDao
import com.paisetrail.app.data.db.CategoryEntity
import com.paisetrail.app.data.db.TagSource
import com.paisetrail.app.data.db.TransactionDao
import com.paisetrail.app.export.ExportBundle
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

data class AutoTagResult(val rulesLearned: Int, val taggedCount: Int, val candidateCount: Int)

/**
 * Bulk-teaches [MerchantResolver] from a previously labeled JSON bundle (same schema as spec
 * 7.6's own export — a device with more history, or one that's already been hand-tagged, can
 * export and hand that file to a fresh install) and then re-runs the merchant-learned-category
 * step of [TransactionEnrichmentCoordinator]'s cascade over every transaction still sitting at
 * NONE/AUTO_LOW. This is the same learning loop [MerchantResolver.learnCategory] already drives
 * from manual tag confirmations — this just seeds it from a whole file at once instead of one
 * merchant at a time.
 */
@Singleton
class CategoryAutoTagger @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val merchantResolver: MerchantResolver,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun learnAndApplyFromJson(jsonString: String): AutoTagResult {
        val bundle = json.decodeFromString(ExportBundle.serializer(), jsonString)

        var rulesLearned = 0
        for (labeled in bundle.transactions) {
            val categoryName = labeled.categoryName ?: continue
            if (labeled.payeeName.isNullOrBlank() && labeled.vpa.isNullOrBlank()) continue
            val categoryId = resolveCategoryId(categoryName)
            val merchantId = merchantResolver.resolve(labeled.payeeName, labeled.vpa) ?: continue
            merchantResolver.learnCategory(merchantId, categoryId)
            rulesLearned++
        }

        val candidates = transactionDao.getNeedingReview()
        var tagged = 0
        for (txn in candidates) {
            val merchantId = txn.merchantId ?: merchantResolver.resolve(txn.payeeNameRaw, txn.vpa)
            val learnedCategoryId = merchantId?.let { merchantResolver.getDefaultCategoryId(it) } ?: continue
            transactionDao.update(txn.copy(categoryId = learnedCategoryId, merchantId = merchantId, tagSource = TagSource.AUTO_HIGH))
            tagged++
        }

        return AutoTagResult(rulesLearned, tagged, candidates.size)
    }

    private suspend fun resolveCategoryId(name: String): Long {
        categoryDao.getByName(name)?.let { return it.id }
        categoryDao.insertAll(listOf(CategoryEntity(name = name, colorHex = FALLBACK_COLOR_HEX)))
        return categoryDao.getByName(name)!!.id
    }

    companion object {
        private const val FALLBACK_COLOR_HEX = "#8A8D93"
    }
}
