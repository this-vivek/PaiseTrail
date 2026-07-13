package com.paisetrail.app.enrich.ai

import com.paisetrail.app.data.db.CategoryDao
import com.paisetrail.app.data.db.TagSource
import com.paisetrail.app.data.db.TransactionDao
import javax.inject.Inject
import javax.inject.Singleton

data class AiTagResult(val taggedByAi: Int, val taggedByLocal: Int, val candidateCount: Int, val aiStatus: String)

/**
 * One button, one pass over everything still needing review: try on-device Gemini Nano first for
 * each transaction, fall back to the offline similarity classifier the moment Nano is unavailable
 * or gives no usable answer. Tags land as [TagSource.AUTO_AI] regardless of which suggester
 * actually produced the category — that field distinguishes "an AI mechanism decided this" from
 * the keyword/merchant-learned cascade in [com.paisetrail.app.enrich.TransactionEnrichmentCoordinator],
 * not which AI mechanism specifically.
 */
@Singleton
class AiCategoryTagger @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val aiSuggester: CategorySuggester,
    private val localSuggester: LocalCategorySuggester,
) {
    suspend fun autoTagAll(): AiTagResult {
        val categories = categoryDao.getAll()
        val categoryNames = categories.map { it.name }
        val candidates = transactionDao.getNeedingReview()
        val aiReady = aiSuggester.ensureReady()

        var taggedByAi = 0
        var taggedByLocal = 0
        for (txn in candidates) {
            val aiAnswer = if (aiReady) aiSuggester.suggestCategory(txn.payeeNameRaw, txn.vpa, categoryNames) else null
            val categoryName = aiAnswer ?: localSuggester.suggestCategory(txn.payeeNameRaw, txn.vpa, categoryNames)
            val category = categoryName?.let { name -> categories.firstOrNull { it.name.equals(name, ignoreCase = true) } }
                ?: continue

            transactionDao.update(txn.copy(categoryId = category.id, tagSource = TagSource.AUTO_AI))
            if (aiAnswer != null) taggedByAi++ else taggedByLocal++
        }

        return AiTagResult(taggedByAi, taggedByLocal, candidates.size, aiSuggester.statusDescription())
    }
}
