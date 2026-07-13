package com.paisetrail.app.capture

import java.math.BigDecimal
import java.math.RoundingMode

/** Shared across notification and SMS parsers — spec 3.1 / 3.2. */
object TxnTextRules {
    val AMOUNT_REGEX = Regex("""(?:₹|Rs\.?|INR)\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?)""")

    private val DEBIT_KEYWORDS =
        listOf("paid", "sent", "debited", "withdrawn", "trf to", "transferred", "payment of")
    private val CREDIT_KEYWORDS =
        listOf("received", "credited", "refund", "reminder", "offer", "cashback", "deposited")

    /** Strips commas and converts to integer paise — never Float/Double for money (spec 3.1). */
    fun amountToPaise(rawDigits: String): Long {
        val normalized = rawDigits.replace(",", "")
        return BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP)
            .movePointRight(2)
            .longValueExact()
    }

    fun extractAmountPaise(text: String): Long? =
        AMOUNT_REGEX.find(text)?.groupValues?.get(1)?.let(::amountToPaise)

    /** Debit-signal filter (spec 3.1 step 3 / 3.2 debit-only gate): must look like a debit and
     * must not simultaneously look like a credit/refund/offer. */
    fun isDebitSignal(text: String): Boolean {
        val lower = text.lowercase()
        val hasDebitWord = DEBIT_KEYWORDS.any { lower.contains(it) }
        val hasCreditWord = CREDIT_KEYWORDS.any { lower.contains(it) }
        return hasDebitWord && !hasCreditWord
    }

    /** The credit counterpart (spec 3.1 point 3's "separate credit/ignore path") — feeds refund
     * matching (spec 3.3 point 5). Deliberately excludes "reminder"/"offer"/"cashback": those are
     * promotional noise, not a real credit event, even though they share the keyword bucket used
     * to REJECT a debit-shaped message. */
    fun isCreditSignal(text: String): Boolean {
        val lower = text.lowercase()
        val hasRealCreditWord = REAL_CREDIT_KEYWORDS.any { lower.contains(it) }
        val hasDebitWord = DEBIT_KEYWORDS.any { lower.contains(it) }
        return hasRealCreditWord && !hasDebitWord
    }

    private val REAL_CREDIT_KEYWORDS = listOf("received", "credited", "refund", "deposited")
}
