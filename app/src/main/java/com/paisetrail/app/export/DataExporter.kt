package com.paisetrail.app.export

import com.paisetrail.app.data.db.CategoryDao
import com.paisetrail.app.data.db.MerchantDao
import com.paisetrail.app.data.db.TransactionDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Singleton
class DataExporter @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val merchantDao: MerchantDao,
) {
    private val json = Json { prettyPrint = true }

    suspend fun buildExportJson(): String {
        val transactions = transactionDao.getAllForExport()
        val categoriesById = categoryDao.getAll().associateBy { it.id }
        val merchantsById = merchantDao.getAll().associateBy { it.id }

        val exported = transactions.map { txn ->
            ExportedTransaction(
                amountPaise = txn.amountPaise,
                direction = txn.direction.name,
                status = txn.status.name,
                payeeName = txn.payeeNameRaw,
                vpa = txn.vpa,
                upiRef = txn.upiRef,
                occurredAt = txn.occurredAt,
                categoryName = txn.categoryId?.let { categoriesById[it]?.name },
                merchantName = txn.merchantId?.let { merchantsById[it]?.canonicalName },
                placeName = txn.placeName,
                locality = txn.locality,
                note = txn.note,
            )
        }

        val bundle = ExportBundle(
            exportedAt = System.currentTimeMillis(),
            transactionCount = exported.size,
            transactions = exported,
        )
        return json.encodeToString(ExportBundle.serializer(), bundle)
    }
}
