package com.paisetrail.app.testutil

import com.paisetrail.app.data.db.CategorySpendRow
import com.paisetrail.app.data.db.DailyCategorySpendRow
import com.paisetrail.app.data.db.DailySpendRow
import com.paisetrail.app.data.db.MerchantSpendRow
import com.paisetrail.app.data.db.MonthlySpendRow
import com.paisetrail.app.data.db.TagSource
import com.paisetrail.app.data.db.TransactionDao
import com.paisetrail.app.data.db.TransactionEntity
import com.paisetrail.app.data.db.TripSpendRow
import com.paisetrail.app.data.db.TxnDirection
import com.paisetrail.app.data.db.TxnStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [TransactionDao] double shared by tests that don't need real SQLite — a plain Room
 * DAO test double, not a mock, so tests read like the real query semantics they approximate. */
class FakeTransactionDao : TransactionDao {
    val transactions = MutableStateFlow<List<TransactionEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(txn: TransactionEntity): Long {
        val id = nextId++
        transactions.value = transactions.value + txn.copy(id = id)
        return id
    }

    override suspend fun update(txn: TransactionEntity) {
        transactions.value = transactions.value.map { if (it.id == txn.id) txn else it }
    }

    override suspend fun getById(id: Long): TransactionEntity? = transactions.value.firstOrNull { it.id == id }

    override fun observeById(id: Long) = transactions.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun getByUpiRef(upiRef: String): TransactionEntity? =
        transactions.value.firstOrNull { it.upiRef == upiRef }

    override suspend fun getInWindow(fromMillis: Long, toMillis: Long): List<TransactionEntity> =
        transactions.value.filter { it.occurredAt in fromMillis..toMillis }

    override fun observeRecent(limit: Int) = transactions.map { it.take(limit) }

    override fun observeInWindow(fromMillis: Long, toMillis: Long) =
        transactions.map { list -> list.filter { it.occurredAt in fromMillis..toMillis }.sortedByDescending { it.occurredAt } }

    override suspend fun clearTrip(tripId: Long) {
        transactions.value = transactions.value.map { txn ->
            if (txn.tripId == tripId) txn.copy(tripId = null) else txn
        }
    }

    override suspend fun clearMerchant(merchantId: Long) {
        transactions.value = transactions.value.map { txn ->
            if (txn.merchantId == merchantId) txn.copy(merchantId = null) else txn
        }
    }

    override suspend fun clearCategory(categoryId: Long) {
        transactions.value = transactions.value.map { txn ->
            if (txn.categoryId == categoryId) txn.copy(categoryId = null, tagSource = TagSource.NONE) else txn
        }
    }

    override suspend fun deleteAll() {
        transactions.value = emptyList()
    }

    override suspend fun markSelfTransfers() {
        transactions.value = transactions.value.map { txn ->
            val name = txn.payeeNameRaw
            val isSelf = name != null &&
                (name.contains("VIVEK SINGH RAWAT", ignoreCase = true) || name.contains("VIVEK RAWAT", ignoreCase = true))
            if (txn.status == TxnStatus.CONFIRMED && isSelf) txn.copy(status = TxnStatus.SELF_TRANSFER) else txn
        }
    }

    override suspend fun getAllForExport(): List<TransactionEntity> = transactions.value.sortedBy { it.occurredAt }

    override fun observeNeedingReview() =
        transactions.map { list -> list.filter { it.tagSource == TagSource.NONE || it.tagSource == TagSource.AUTO_LOW } }

    override suspend fun getNeedingReview(): List<TransactionEntity> =
        transactions.value.filter { it.tagSource == TagSource.NONE || it.tagSource == TagSource.AUTO_LOW }

    private fun confirmedDebitsIn(fromMillis: Long, toMillis: Long): List<TransactionEntity> =
        transactions.value.filter {
            it.direction == TxnDirection.DEBIT && it.status == TxnStatus.CONFIRMED && it.occurredAt in fromMillis..toMillis
        }

    override fun observeSpendInWindow(fromMillis: Long, toMillis: Long) =
        transactions.map { confirmedDebitsIn(fromMillis, toMillis).sumOf { txn -> txn.amountPaise } }

    override fun observeCategorySpend(fromMillis: Long, toMillis: Long) =
        transactions.map {
            confirmedDebitsIn(fromMillis, toMillis)
                .groupBy { txn -> txn.categoryId }
                .map { (categoryId, txns) -> CategorySpendRow(categoryId, txns.sumOf { it.amountPaise }) }
        }

    override fun observeDailySpend(fromMillis: Long) =
        transactions.map {
            val dayFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())
            confirmedDebitsIn(fromMillis, Long.MAX_VALUE)
                .groupBy { txn -> dayFormat.format(Instant.ofEpochMilli(txn.occurredAt)) }
                .map { (day, txns) -> DailySpendRow(day, txns.sumOf { it.amountPaise }) }
                .sortedBy { it.day }
        }

    override fun observeDailyCategorySpend(fromMillis: Long) =
        transactions.map {
            val dayFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())
            confirmedDebitsIn(fromMillis, Long.MAX_VALUE)
                .groupBy { txn -> dayFormat.format(Instant.ofEpochMilli(txn.occurredAt)) to txn.categoryId }
                .map { (key, txns) -> DailyCategorySpendRow(key.first, key.second, txns.sumOf { it.amountPaise }) }
                .sortedBy { it.day }
        }

    override fun observeMonthlySpend(fromMillis: Long) =
        transactions.map {
            val monthFormat = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneId.systemDefault())
            confirmedDebitsIn(fromMillis, Long.MAX_VALUE)
                .groupBy { txn -> monthFormat.format(Instant.ofEpochMilli(txn.occurredAt)) }
                .map { (month, txns) -> MonthlySpendRow(month, txns.sumOf { it.amountPaise }) }
                .sortedBy { it.month }
        }

    override fun observeTopMerchants(fromMillis: Long, toMillis: Long) =
        transactions.map {
            confirmedDebitsIn(fromMillis, toMillis)
                .filter { txn -> txn.merchantId != null }
                .groupBy { txn -> txn.merchantId!! }
                .map { (merchantId, txns) -> MerchantSpendRow(merchantId, "Merchant $merchantId", txns.sumOf { it.amountPaise }) }
                .sortedByDescending { it.amountPaise }
                .take(5)
        }

    override fun observeMapped() =
        transactions.map { list ->
            list.filter {
                it.direction == TxnDirection.DEBIT && it.status == TxnStatus.CONFIRMED && it.lat != null && it.lng != null
            }
        }

    override fun observeForTrip(tripId: Long) =
        transactions.map { list -> list.filter { it.tripId == tripId }.sortedBy { it.occurredAt } }

    private fun confirmedDebitsForTrip(tripId: Long): List<TransactionEntity> =
        transactions.value.filter {
            it.direction == TxnDirection.DEBIT && it.status == TxnStatus.CONFIRMED && it.tripId == tripId
        }

    override fun observeTripSpend() =
        transactions.map { list ->
            list
                .filter { it.direction == TxnDirection.DEBIT && it.status == TxnStatus.CONFIRMED && it.tripId != null }
                .groupBy { it.tripId!! }
                .map { (tripId, txns) -> TripSpendRow(tripId, txns.sumOf { it.amountPaise }, txns.size) }
        }

    override fun observeCategorySpendForTrip(tripId: Long) =
        transactions.map {
            confirmedDebitsForTrip(tripId)
                .groupBy { txn -> txn.categoryId }
                .map { (categoryId, txns) -> CategorySpendRow(categoryId, txns.sumOf { it.amountPaise }) }
        }

    override fun observeDailySpendForTrip(tripId: Long) =
        transactions.map {
            val dayFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())
            confirmedDebitsForTrip(tripId)
                .groupBy { txn -> dayFormat.format(Instant.ofEpochMilli(txn.occurredAt)) }
                .map { (day, txns) -> DailySpendRow(day, txns.sumOf { it.amountPaise }) }
                .sortedBy { it.day }
        }

    override suspend fun getRecentUntripped(sinceMillis: Long): List<TransactionEntity> =
        transactions.value.filter {
            it.direction == TxnDirection.DEBIT && it.status == TxnStatus.CONFIRMED &&
                it.paymentContext == com.paisetrail.app.data.db.PaymentContext.IN_PERSON &&
                it.tripId == null && it.lat != null && it.lng != null && it.occurredAt >= sinceMillis
        }
}
