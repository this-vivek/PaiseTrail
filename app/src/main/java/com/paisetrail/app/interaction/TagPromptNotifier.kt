package com.paisetrail.app.interaction

import com.paisetrail.app.data.db.TransactionEntity

/** Posts the heads-up "tag this payment" popup (spec 5). Interface exists mainly so
 * [com.paisetrail.app.enrich.TransactionEnrichmentCoordinator] doesn't depend on Android
 * notification APIs directly. */
fun interface TagPromptNotifier {
    suspend fun postTagPrompt(txn: TransactionEntity)
}
