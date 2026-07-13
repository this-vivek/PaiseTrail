package com.paisetrail.app.enrich.ai

import com.paisetrail.app.data.db.CategoryDao
import com.paisetrail.app.data.db.TransactionDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Guaranteed-to-work fallback for [AiCategoryTagger]: no network, no model download, works on any
 * phone. Finds the most similar ALREADY-tagged transaction (by payee/VPA name, same token-set
 * Jaccard similarity [com.paisetrail.app.enrich.MerchantResolver] already uses for merchant
 * matching) and copies its category — this is "learn from what I've already tagged," just without
 * an LLM in the loop.
 */
@Singleton
class LocalCategorySuggester @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
) : CategorySuggester {
    override suspend fun ensureReady(): Boolean = true

    override fun statusDescription(): String = "Offline local classifier (always available)"

    override suspend fun suggestCategory(payeeName: String?, vpa: String?, categoryNames: List<String>): String? {
        val queryTokens = tokenize(payeeName ?: vpa ?: return null)
        if (queryTokens.isEmpty()) return null

        val categoriesById = categoryDao.getAll().associateBy { it.id }
        val labeled = transactionDao.getAllForExport()
            .filter { it.categoryId != null && !it.payeeNameRaw.isNullOrBlank() }

        return labeled
            .mapNotNull { txn ->
                val name = categoriesById[txn.categoryId]?.name?.takeIf { it in categoryNames } ?: return@mapNotNull null
                name to jaccard(queryTokens, tokenize(txn.payeeNameRaw!!))
            }
            .filter { (_, similarity) -> similarity >= SIMILARITY_THRESHOLD }
            .maxByOrNull { (_, similarity) -> similarity }
            ?.first
    }

    private fun tokenize(s: String): Set<String> =
        s.lowercase().split(Regex("[^a-z0-9]+")).filterTo(mutableSetOf()) { it.isNotBlank() }

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val intersection = a.intersect(b).size
        val union = a.union(b).size
        return intersection.toDouble() / union
    }

    companion object {
        private const val SIMILARITY_THRESHOLD = 0.4
    }
}
