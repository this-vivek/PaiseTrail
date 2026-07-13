package com.paisetrail.app.capture

/** A payment where the payee name is the account owner's own name is money moving between their
 * own accounts, not spend — excluded from every spend total via [com.paisetrail.app.data.db.TxnStatus.SELF_TRANSFER],
 * the same mechanism that already excludes REFUNDED debits. Keep this list in sync with the
 * `LIKE` patterns in [com.paisetrail.app.data.db.TransactionDao.markSelfTransfers], which performs
 * the same check in SQL for transactions captured before this detector existed. */
object SelfTransferDetector {
    private val SELF_NAMES = listOf("VIVEK SINGH RAWAT", "VIVEK RAWAT")

    fun isSelfTransfer(payeeName: String?): Boolean {
        if (payeeName.isNullOrBlank()) return false
        return SELF_NAMES.any { payeeName.contains(it, ignoreCase = true) }
    }
}
