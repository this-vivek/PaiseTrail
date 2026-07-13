package com.paisetrail.app.enrich

/** Seam between capture and enrichment (spec architecture diagram) — [RawEventIngestor] calls
 * this once per genuinely new transaction (never on a dedup merge-update) without needing to
 * know anything about location, categories, or notifications itself. */
fun interface TransactionEnrichmentTrigger {
    suspend fun onNewTransaction(txnId: Long)
}
