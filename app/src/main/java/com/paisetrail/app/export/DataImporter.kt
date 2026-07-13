package com.paisetrail.app.export

import com.paisetrail.app.data.db.CategoryDao
import com.paisetrail.app.data.db.CategoryEntity
import com.paisetrail.app.data.db.MerchantDao
import com.paisetrail.app.data.db.MerchantEntity
import com.paisetrail.app.data.db.TagSource
import com.paisetrail.app.data.db.TransactionDao
import com.paisetrail.app.data.db.TransactionEntity
import com.paisetrail.app.data.db.TxnDirection
import com.paisetrail.app.data.db.TxnStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

data class ImportResult(val importedCount: Int, val skippedCount: Int)

/**
 * Restores an [ExportBundle] produced by [DataExporter] (spec 7.6). Category/merchant names are
 * resolved back to ids, creating either if a backup is restored onto a fresh install that hasn't
 * seeded/learned them yet. Dedup is by [ExportedTransaction.upiRef] against what's already in the
 * DB — a transaction with no ref (never happens for real UPI payments, but not disallowed by the
 * schema) has no reliable key to dedup on and is always inserted.
 */
@Singleton
class DataImporter @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val merchantDao: MerchantDao,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun importFromJson(jsonString: String): ImportResult {
        val bundle = json.decodeFromString(ExportBundle.serializer(), jsonString)

        var imported = 0
        var skipped = 0
        for (exported in bundle.transactions) {
            if (exported.upiRef != null && transactionDao.getByUpiRef(exported.upiRef) != null) {
                skipped++
                continue
            }

            val categoryId = exported.categoryName?.let { resolveCategoryId(it) }
            val merchantId = exported.merchantName?.let { resolveMerchantId(it) }

            transactionDao.insert(
                TransactionEntity(
                    amountPaise = exported.amountPaise,
                    direction = TxnDirection.valueOf(exported.direction),
                    status = TxnStatus.valueOf(exported.status),
                    payeeNameRaw = exported.payeeName,
                    vpa = exported.vpa,
                    upiRef = exported.upiRef,
                    occurredAt = exported.occurredAt,
                    categoryId = categoryId,
                    tagSource = if (categoryId != null) TagSource.USER else TagSource.NONE,
                    merchantId = merchantId,
                    placeName = exported.placeName,
                    locality = exported.locality,
                    note = exported.note,
                ),
            )
            imported++
        }

        return ImportResult(imported, skipped)
    }

    private suspend fun resolveCategoryId(name: String): Long {
        categoryDao.getByName(name)?.let { return it.id }
        categoryDao.insertAll(listOf(CategoryEntity(name = name, colorHex = FALLBACK_COLOR_HEX)))
        // Re-seeding is IGNORE-on-conflict (unique name index), so a concurrent seed of the same
        // name just means this lookup now finds that row instead of the one just inserted.
        return categoryDao.getByName(name)!!.id
    }

    private suspend fun resolveMerchantId(name: String): Long {
        merchantDao.getByName(name)?.let { return it.id }
        return merchantDao.insert(MerchantEntity(canonicalName = name))
    }

    companion object {
        private const val FALLBACK_COLOR_HEX = "#8A8D93"
    }
}
