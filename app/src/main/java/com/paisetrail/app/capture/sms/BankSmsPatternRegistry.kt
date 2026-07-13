package com.paisetrail.app.capture.sms

import android.util.Log
import com.paisetrail.app.capture.ParsedTxn
import com.paisetrail.app.capture.TxnTextRules
import com.paisetrail.app.data.db.BankSmsPatternDao
import com.paisetrail.app.data.db.BankSmsPatternEntity
import com.paisetrail.app.data.db.TxnDirection
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.text.MatchNamedGroupCollection

/** [senderRecognized] is true whenever the sender matched a known bank's [BankSmsPatternEntity.senderSuffix]
 * — independent of whether the body regex actually matched. The caller uses this to distinguish
 * "not a bank SMS at all" (skip entirely) from "a bank SMS whose body didn't match our regex"
 * (still worth a raw_event so the pattern can be tuned — spec 7.6's whole reason to exist). */
data class SmsMatchResult(val senderRecognized: Boolean, val parsed: ParsedTxn?)

/**
 * Matches an incoming SMS body against the data-driven bank pattern table (spec 3.2). Every
 * stored regex is written to match only a debit-shaped message for that bank, so a successful
 * match is inherently a debit — unlike the notification path, no separate keyword gate is needed
 * here (ICICI's "credited" appears in genuine debit SMS, which is exactly why spec 3.2 says the
 * per-bank regex handles it rather than a generic keyword filter).
 */
@Singleton
class BankSmsPatternRegistry @Inject constructor(
    private val dao: BankSmsPatternDao,
) {
    suspend fun match(sender: String, body: String): SmsMatchResult {
        val patterns = dao.getEnabled()
        // Real sender IDs carry both a route prefix and a trailing suffix around the bank code
        // (e.g. "VM-HDFCBK-T", "AX-HDFCBK-S") — the code sits in the middle, not at the end, so
        // this has to be a substring check, not endsWith.
        val candidates = patterns.filter { sender.contains(it.senderSuffix, ignoreCase = true) }
        if (candidates.isEmpty()) return SmsMatchResult(senderRecognized = false, parsed = null)

        // More than one pattern can share a bankId (multiple real SMS templates for the same
        // bank — see BankSmsPatternEntity) — try each until one actually parses, instead of
        // committing to whichever happened to be enumerated first.
        for (candidate in candidates) {
            val parsed = tryParse(candidate, body) ?: continue
            return SmsMatchResult(senderRecognized = true, parsed = parsed)
        }
        return SmsMatchResult(senderRecognized = true, parsed = null)
    }

    private fun tryParse(candidate: BankSmsPatternEntity, body: String): ParsedTxn? {
        val regex = try {
            Regex(candidate.regex, RegexOption.DOT_MATCHES_ALL)
        } catch (e: Exception) {
            Log.e(TAG, "invalid regex stored for bank ${candidate.bankId}", e)
            return null
        }

        val result = regex.find(body) ?: return null
        val groups = result.groups as MatchNamedGroupCollection
        val amountRaw = groups.valueOrNull("amount") ?: return null
        val amountPaise = try {
            TxnTextRules.amountToPaise(amountRaw)
        } catch (e: Exception) {
            Log.e(TAG, "amount parse failed for bank ${candidate.bankId}: $amountRaw", e)
            return null
        }

        return ParsedTxn(
            amountPaise = amountPaise,
            payeeName = groups.valueOrNull("payee")?.trim(),
            vpa = groups.valueOrNull("vpa")?.trim(),
            refId = groups.valueOrNull("ref")?.trim(),
            acctLast4 = groups.valueOrNull("acctLast4")?.trim(),
            direction = TxnDirection.DEBIT,
        )
    }

    /** [MatchNamedGroupCollection.get] throws IllegalArgumentException when the compiled regex
     * doesn't declare a group with this name at all (as opposed to returning null when the group
     * is declared but didn't participate in the match) — each bank's regex only declares whichever
     * of {amount, payee, vpa, ref, acctLast4} its SMS format actually carries (spec 3.2). */
    private fun MatchNamedGroupCollection.valueOrNull(name: String): String? =
        try {
            this[name]?.value
        } catch (e: IllegalArgumentException) {
            null
        }

    companion object {
        private const val TAG = "BankSmsPatternRegistry"
    }
}
