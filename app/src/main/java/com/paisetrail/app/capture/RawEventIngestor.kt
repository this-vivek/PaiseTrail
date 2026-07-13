package com.paisetrail.app.capture

import android.util.Log
import com.paisetrail.app.data.db.RawEventDao
import com.paisetrail.app.data.db.RawEventEntity
import com.paisetrail.app.data.db.RawEventSource
import com.paisetrail.app.data.db.TransactionDao
import com.paisetrail.app.data.db.TransactionEntity
import com.paisetrail.app.data.db.TripDao
import com.paisetrail.app.data.db.TxnStatus
import com.paisetrail.app.enrich.TransactionEnrichmentTrigger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * normalize -> parse -> dedup gate (spec 3.3). Capture must never block on this and a parser
 * exception must never kill capture (spec 10 #10) — every step is wrapped so a bad regex or a
 * malformed event degrades to an unlinked raw_event, not a crash.
 */
@Singleton
class RawEventIngestor @Inject constructor(
    private val rawEventDao: RawEventDao,
    private val transactionDao: TransactionDao,
    private val tripDao: TripDao,
    private val enrichmentTrigger: TransactionEnrichmentTrigger,
) {
    suspend fun ingest(
        source: RawEventSource,
        packageOrSender: String,
        fullText: String,
        postedAt: Long,
        parsed: ParsedTxn?,
        triggerEnrichment: Boolean = true,
    ) {
        val rawEventId = try {
            rawEventDao.insert(
                RawEventEntity(
                    source = source,
                    packageOrSender = packageOrSender,
                    fullText = fullText,
                    postedAt = postedAt,
                    parsedOk = parsed != null,
                ),
            )
        } catch (e: Exception) {
            Log.e(TAG, "failed to persist raw event from $packageOrSender", e)
            return
        }

        if (parsed == null) return

        val dedupResult = try {
            dedupAndMerge(source, postedAt, parsed)
        } catch (e: Exception) {
            Log.e(TAG, "dedup/merge failed for raw event $rawEventId", e)
            return
        }

        try {
            rawEventDao.update(
                RawEventEntity(
                    id = rawEventId,
                    source = source,
                    packageOrSender = packageOrSender,
                    fullText = fullText,
                    postedAt = postedAt,
                    parsedOk = true,
                    txnId = dedupResult.txnId,
                ),
            )
        } catch (e: Exception) {
            Log.e(TAG, "failed to link raw event $rawEventId to txn ${dedupResult.txnId}", e)
        }

        // Enrichment (location, category guess, tag popup) only ever fires once per genuinely
        // new transaction — never on a dedup merge-update of an already-enriched one — and never
        // blocks or breaks capture if it fails (spec 3 key principle). Backfill explicitly skips
        // it: historical rows get no location by design (spec 3.4) and firing hundreds of tag
        // popups for months of history at once would be unusable.
        if (dedupResult.isNew && triggerEnrichment) {
            try {
                enrichmentTrigger.onNewTransaction(dedupResult.txnId)
            } catch (e: Exception) {
                Log.e(TAG, "enrichment trigger failed for txn ${dedupResult.txnId}", e)
            }
        }
    }

    private data class DedupResult(val txnId: Long, val isNew: Boolean)

    private suspend fun dedupAndMerge(source: RawEventSource, postedAt: Long, parsed: ParsedTxn): DedupResult {
        val strongMatch = parsed.refId?.let { transactionDao.getByUpiRef(it) }
        if (strongMatch != null) {
            val merged = mergeFields(strongMatch, parsed, source, postedAt)
            transactionDao.update(merged)
            return DedupResult(merged.id, isNew = false)
        }

        val candidates = transactionDao.getInWindow(postedAt - FUZZY_WINDOW_MS, postedAt + FUZZY_WINDOW_MS)
        val fuzzyMatch = candidates.firstOrNull { candidate ->
            candidate.amountPaise == parsed.amountPaise &&
                payeeTokensOverlap(candidate.payeeNameRaw, parsed.payeeName) &&
                !refsConflict(candidate.upiRef, parsed.refId)
        }
        if (fuzzyMatch != null) {
            val merged = mergeFields(fuzzyMatch, parsed, source, postedAt)
            transactionDao.update(merged)
            return DedupResult(merged.id, isNew = false)
        }

        val newId = transactionDao.insert(
            TransactionEntity(
                amountPaise = parsed.amountPaise,
                direction = parsed.direction,
                // A payment to yourself (moving money between your own accounts) isn't spend —
                // excluded from every spend total by status alone, same mechanism as REFUNDED.
                status = if (SelfTransferDetector.isSelfTransfer(parsed.payeeName)) {
                    TxnStatus.SELF_TRANSFER
                } else {
                    TxnStatus.CONFIRMED
                },
                payeeNameRaw = parsed.payeeName,
                vpa = parsed.vpa,
                upiRef = parsed.refId,
                bankAcctLast4 = parsed.acctLast4,
                occurredAt = postedAt,
                // Trip mode (spec 7.4/5): a payment during an active trip is tagged to it the
                // moment it's created, not just when the tag popup is confirmed.
                tripId = tripDao.getActiveTrip()?.id,
            ),
        )
        return DedupResult(newId, isNew = true)
    }

    /** notification wins payeeName, SMS wins vpa/refId/bankAcctLast4 (spec 3.3 point 3). Earlier
     * of the two postedAt values is kept as occurredAt — closer to the payment moment. */
    private fun mergeFields(
        existing: TransactionEntity,
        incoming: ParsedTxn,
        incomingSource: RawEventSource,
        incomingPostedAt: Long,
    ): TransactionEntity {
        val payeeName = if (incomingSource == RawEventSource.NOTIFICATION && incoming.payeeName != null) {
            incoming.payeeName
        } else {
            existing.payeeNameRaw ?: incoming.payeeName
        }
        val vpa = if (incomingSource == RawEventSource.SMS && incoming.vpa != null) {
            incoming.vpa
        } else {
            existing.vpa ?: incoming.vpa
        }
        val upiRef = if (incomingSource == RawEventSource.SMS && incoming.refId != null) {
            incoming.refId
        } else {
            existing.upiRef ?: incoming.refId
        }
        val bankAcctLast4 = if (incomingSource == RawEventSource.SMS && incoming.acctLast4 != null) {
            incoming.acctLast4
        } else {
            existing.bankAcctLast4 ?: incoming.acctLast4
        }
        return existing.copy(
            payeeNameRaw = payeeName,
            vpa = vpa,
            upiRef = upiRef,
            bankAcctLast4 = bankAcctLast4,
            occurredAt = minOf(existing.occurredAt, incomingPostedAt),
        )
    }

    /** Two events carrying different, explicit UPI ref numbers are provably different
     * transactions, even if amount/payee/timing all coincide — e.g. several same-amount
     * same-payee payments sent minutes apart (spec 3.3 point 1 implies the converse: a ref
     * mismatch rules out a match, it doesn't just fail to confirm one). Without this, rapid
     * repeat payments to the same person collapse into a single transaction. */
    private fun refsConflict(existingRef: String?, incomingRef: String?): Boolean =
        existingRef != null && incomingRef != null && existingRef != incomingRef

    private fun payeeTokensOverlap(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return true
        return tokenize(a).intersect(tokenize(b)).isNotEmpty()
    }

    private fun tokenize(s: String): Set<String> =
        s.lowercase().split(Regex("[^a-z0-9]+")).filterTo(mutableSetOf()) { it.isNotBlank() }

    companion object {
        private const val TAG = "RawEventIngestor"
        private const val FUZZY_WINDOW_MS = 180_000L
    }
}
