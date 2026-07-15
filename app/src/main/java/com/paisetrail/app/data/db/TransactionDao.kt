package com.paisetrail.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class CategorySpendRow(val categoryId: Long?, val amountPaise: Long)
data class DailySpendRow(val day: String, val amountPaise: Long)
data class DailyCategorySpendRow(val day: String, val categoryId: Long?, val amountPaise: Long)
data class MonthlySpendRow(val month: String, val amountPaise: Long)
data class MerchantSpendRow(val merchantId: Long, val merchantName: String, val amountPaise: Long, val count: Int)
data class TripSpendRow(val tripId: Long, val amountPaise: Long, val count: Int)
data class CategoryUsageRow(val categoryId: Long, val count: Int)

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(txn: TransactionEntity): Long

    @Update
    suspend fun update(txn: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    /** Transaction detail screen (spec 7.3) — reactive so a re-tag from the picker updates the
     * screen immediately without a manual refresh. */
    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeById(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE upiRef = :upiRef LIMIT 1")
    suspend fun getByUpiRef(upiRef: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE occurredAt BETWEEN :fromMillis AND :toMillis ORDER BY occurredAt ASC")
    suspend fun getInWindow(fromMillis: Long, toMillis: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<TransactionEntity>>

    /** Month-by-month browsing (spec 7.3). */
    @Query("SELECT * FROM transactions WHERE occurredAt BETWEEN :fromMillis AND :toMillis ORDER BY occurredAt DESC")
    fun observeInWindow(fromMillis: Long, toMillis: Long): Flow<List<TransactionEntity>>

    /** Debug-only reset (spec 7.6 debug tools) — wipes every transaction so the app can be
     * re-tested from a clean slate without reinstalling. */
    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    /** Manual cleanup from the transaction detail screen — e.g. a duplicate the dedup gate let
     * through (two sources describing the same real payment with no way to auto-merge). */
    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Deleting a trip (spec 7.4 "delete trip") unlinks its transactions rather than deleting
     * them — they just stop being trip-scoped and fall back into the regular Transactions list. */
    @Query("UPDATE transactions SET tripId = NULL WHERE tripId = :tripId")
    suspend fun clearTrip(tripId: Long)

    /** Deleting a merchant (merchant management screen) shouldn't leave transactions pointing at
     * an id that no longer exists — clear it back to null so they just stop counting toward Top
     * Merchants rather than silently keeping a dangling reference. */
    @Query("UPDATE transactions SET merchantId = NULL WHERE merchantId = :merchantId")
    suspend fun clearMerchant(merchantId: Long)

    /** Deleting a category (category management screen) shouldn't leave transactions pointing at
     * an id that no longer exists — clear it back to NONE so they show up in Review Queue again
     * rather than silently keeping a dangling reference. */
    @Query("UPDATE transactions SET categoryId = NULL, tagSource = 'NONE' WHERE categoryId = :categoryId")
    suspend fun clearCategory(categoryId: Long)

    /** One-time-per-launch correction for transactions captured before self-transfer detection
     * existed (see [com.paisetrail.app.capture.SelfTransferDetector] — keep these two in sync).
     * Idempotent: once flipped to SELF_TRANSFER a row no longer matches status = 'CONFIRMED'. */
    @Query(
        "UPDATE transactions SET status = 'SELF_TRANSFER' WHERE status = 'CONFIRMED' AND " +
            "(payeeNameRaw LIKE '%VIVEK SINGH RAWAT%' OR payeeNameRaw LIKE '%VIVEK RAWAT%')",
    )
    suspend fun markSelfTransfers()

    /** One-shot full read for JSON export (spec 7.6) — a Flow doesn't fit a "snapshot to a file
     * right now" operation. */
    @Query("SELECT * FROM transactions ORDER BY occurredAt ASC")
    suspend fun getAllForExport(): List<TransactionEntity>

    /** LOW-confidence / uncategorized items for the Review Queue (spec 7.5). */
    @Query("SELECT * FROM transactions WHERE tagSource IN ('NONE', 'AUTO_LOW') ORDER BY occurredAt DESC")
    fun observeNeedingReview(): Flow<List<TransactionEntity>>

    /** Same set as [observeNeedingReview], one-shot — bulk operations like
     * [com.paisetrail.app.enrich.CategoryAutoTagger] sweep this once rather than subscribing. */
    @Query("SELECT * FROM transactions WHERE tagSource IN ('NONE', 'AUTO_LOW') ORDER BY occurredAt DESC")
    suspend fun getNeedingReview(): List<TransactionEntity>

    /** Dashboard aggregates (spec 7.1). REFUNDED debits are excluded from spend by design (spec
     * 3.3 point 5) — only CONFIRMED debits count. */
    @Query(
        "SELECT COALESCE(SUM(amountPaise), 0) FROM transactions " +
            "WHERE direction = 'DEBIT' AND status = 'CONFIRMED' AND occurredAt BETWEEN :fromMillis AND :toMillis",
    )
    fun observeSpendInWindow(fromMillis: Long, toMillis: Long): Flow<Long>

    /** Insights "largest single spend" stat (spec §4.3). */
    @Query(
        "SELECT * FROM transactions " +
            "WHERE direction = 'DEBIT' AND status = 'CONFIRMED' AND occurredAt BETWEEN :fromMillis AND :toMillis " +
            "ORDER BY amountPaise DESC LIMIT 1",
    )
    fun observeLargestSpend(fromMillis: Long, toMillis: Long): Flow<TransactionEntity?>

    @Query(
        "SELECT categoryId, SUM(amountPaise) as amountPaise FROM transactions " +
            "WHERE direction = 'DEBIT' AND status = 'CONFIRMED' AND occurredAt BETWEEN :fromMillis AND :toMillis " +
            "GROUP BY categoryId",
    )
    fun observeCategorySpend(fromMillis: Long, toMillis: Long): Flow<List<CategorySpendRow>>

    @Query(
        "SELECT strftime('%Y-%m-%d', occurredAt / 1000, 'unixepoch', 'localtime') as day, " +
            "SUM(amountPaise) as amountPaise FROM transactions " +
            "WHERE direction = 'DEBIT' AND status = 'CONFIRMED' AND occurredAt >= :fromMillis " +
            "GROUP BY day ORDER BY day ASC",
    )
    fun observeDailySpend(fromMillis: Long): Flow<List<DailySpendRow>>

    /** Per-day, per-category breakdown (the Dashboard's "last 7 days" stacked chart) — same
     * CONFIRMED-debit rule as every other spend aggregate. */
    @Query(
        "SELECT strftime('%Y-%m-%d', occurredAt / 1000, 'unixepoch', 'localtime') as day, " +
            "categoryId, SUM(amountPaise) as amountPaise FROM transactions " +
            "WHERE direction = 'DEBIT' AND status = 'CONFIRMED' AND occurredAt >= :fromMillis " +
            "GROUP BY day, categoryId ORDER BY day ASC",
    )
    fun observeDailyCategorySpend(fromMillis: Long): Flow<List<DailyCategorySpendRow>>

    @Query(
        "SELECT strftime('%Y-%m', occurredAt / 1000, 'unixepoch', 'localtime') as month, " +
            "SUM(amountPaise) as amountPaise FROM transactions " +
            "WHERE direction = 'DEBIT' AND status = 'CONFIRMED' AND occurredAt >= :fromMillis " +
            "GROUP BY month ORDER BY month ASC",
    )
    fun observeMonthlySpend(fromMillis: Long): Flow<List<MonthlySpendRow>>

    @Query(
        "SELECT t.merchantId as merchantId, m.canonicalName as merchantName, SUM(t.amountPaise) as amountPaise, " +
            "COUNT(*) as count " +
            "FROM transactions t JOIN merchants m ON m.id = t.merchantId " +
            "WHERE t.direction = 'DEBIT' AND t.status = 'CONFIRMED' AND t.merchantId IS NOT NULL " +
            "AND t.occurredAt BETWEEN :fromMillis AND :toMillis " +
            "GROUP BY t.merchantId ORDER BY amountPaise DESC LIMIT 5",
    )
    fun observeTopMerchants(fromMillis: Long, toMillis: Long): Flow<List<MerchantSpendRow>>

    /** Map view pins (spec 7.2) — only located, confirmed debits; REFUNDED and MISSING-location
     * rows have nothing to plot. */
    @Query("SELECT * FROM transactions WHERE direction = 'DEBIT' AND status = 'CONFIRMED' AND lat IS NOT NULL AND lng IS NOT NULL")
    fun observeMapped(): Flow<List<TransactionEntity>>

    /** Trip summary (spec 7.4). */
    @Query("SELECT * FROM transactions WHERE tripId = :tripId ORDER BY occurredAt ASC")
    fun observeForTrip(tripId: Long): Flow<List<TransactionEntity>>

    /** Trip list totals (spec 7.4) — CONFIRMED debits only, same rule as the dashboard aggregates,
     * so a refund or a suspect-dup tagged to a trip doesn't inflate its total. */
    @Query(
        "SELECT tripId, SUM(amountPaise) as amountPaise, COUNT(*) as count FROM transactions " +
            "WHERE direction = 'DEBIT' AND status = 'CONFIRMED' AND tripId IS NOT NULL " +
            "GROUP BY tripId",
    )
    fun observeTripSpend(): Flow<List<TripSpendRow>>

    /** Trip detail per-category breakdown (spec 7.4). */
    @Query(
        "SELECT categoryId, SUM(amountPaise) as amountPaise FROM transactions " +
            "WHERE direction = 'DEBIT' AND status = 'CONFIRMED' AND tripId = :tripId GROUP BY categoryId",
    )
    fun observeCategorySpendForTrip(tripId: Long): Flow<List<CategorySpendRow>>

    /** Trip detail per-day breakdown (spec 7.4). */
    @Query(
        "SELECT strftime('%Y-%m-%d', occurredAt / 1000, 'unixepoch', 'localtime') as day, " +
            "SUM(amountPaise) as amountPaise FROM transactions " +
            "WHERE direction = 'DEBIT' AND status = 'CONFIRMED' AND tripId = :tripId GROUP BY day ORDER BY day ASC",
    )
    fun observeDailySpendForTrip(tripId: Long): Flow<List<DailySpendRow>>

    /** Auto-detect candidates (spec 7.4): confirmed in-person debits in the last 24h not
     * already on a trip. */
    @Query(
        "SELECT * FROM transactions WHERE direction = 'DEBIT' AND status = 'CONFIRMED' " +
            "AND paymentContext = 'IN_PERSON' AND tripId IS NULL " +
            "AND lat IS NOT NULL AND lng IS NOT NULL AND occurredAt >= :sinceMillis",
    )
    suspend fun getRecentUntripped(sinceMillis: Long): List<TransactionEntity>

    /** This user's own most-tagged categories overall — the tag popup / Review Queue's fallback
     * suggestion pool (spec 5 TODO: "dynamic to existing taggings") when a merchant has no learned
     * category and no keyword rule matches, instead of a fixed, merchant-irrelevant default list. */
    @Query(
        "SELECT categoryId, COUNT(*) as count FROM transactions " +
            "WHERE categoryId IS NOT NULL AND tagSource != 'NONE' " +
            "GROUP BY categoryId ORDER BY count DESC LIMIT 5",
    )
    suspend fun getCategoryUsageFrequency(): List<CategoryUsageRow>

    /** Same as [getCategoryUsageFrequency], reactive — the Review Queue's suggestions update live
     * as the user tags more transactions during the same session. */
    @Query(
        "SELECT categoryId, COUNT(*) as count FROM transactions " +
            "WHERE categoryId IS NOT NULL AND tagSource != 'NONE' " +
            "GROUP BY categoryId ORDER BY count DESC LIMIT 5",
    )
    fun observeCategoryUsageFrequency(): Flow<List<CategoryUsageRow>>
}
