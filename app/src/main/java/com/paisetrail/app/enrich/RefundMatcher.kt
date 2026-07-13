package com.paisetrail.app.enrich

import com.paisetrail.app.data.db.TransactionDao
import com.paisetrail.app.data.db.TransactionEntity
import com.paisetrail.app.data.db.TxnDirection
import com.paisetrail.app.data.db.TxnStatus
import javax.inject.Inject
import javax.inject.Singleton

/** Refund matching (spec 3.3 point 5): a credit with the same amount and a matching VPA or
 * payee, within 48h *after* an existing confirmed debit, links the two and flips the debit to
 * REFUNDED (excluded from spend totals). Credit-side capture is unverified against real
 * notifications/SMS (see the notification parsers' notes) — this only ever runs on whatever
 * credit events actually get parsed. */
@Singleton
class RefundMatcher @Inject constructor(
    private val transactionDao: TransactionDao,
) {
    suspend fun tryMatchRefund(creditTxnId: Long) {
        val credit = transactionDao.getById(creditTxnId) ?: return
        if (credit.direction != TxnDirection.CREDIT) return

        val candidates = transactionDao.getInWindow(credit.occurredAt - REFUND_WINDOW_MS, credit.occurredAt)
        val originalDebit = candidates.firstOrNull { debit -> isRefundOf(debit, credit) } ?: return

        transactionDao.update(originalDebit.copy(status = TxnStatus.REFUNDED))
        transactionDao.update(credit.copy(refundOfTxnId = originalDebit.id))
    }

    private fun isRefundOf(debit: TransactionEntity, credit: TransactionEntity): Boolean {
        if (debit.direction != TxnDirection.DEBIT) return false
        if (debit.status != TxnStatus.CONFIRMED) return false
        if (debit.amountPaise != credit.amountPaise) return false
        val vpaMatches = debit.vpa != null && debit.vpa == credit.vpa
        val payeeOverlaps = payeeTokensOverlap(debit.payeeNameRaw, credit.payeeNameRaw)
        return vpaMatches || payeeOverlaps
    }

    /** Unlike [com.paisetrail.app.capture.RawEventIngestor]'s dedup version of this check, a
     * missing name on either side returns false here, not true — dedup treats missing data as
     * "don't block a same-source merge," but a wrongly linked refund is worse than a missed one,
     * so this only counts a real overlap, falling back to the VPA match when names are absent. */
    private fun payeeTokensOverlap(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        return tokenize(a).intersect(tokenize(b)).isNotEmpty()
    }

    private fun tokenize(s: String): Set<String> =
        s.lowercase().split(Regex("[^a-z0-9]+")).filterTo(mutableSetOf()) { it.isNotBlank() }

    companion object {
        private const val REFUND_WINDOW_MS = 48L * 60 * 60 * 1000
    }
}
