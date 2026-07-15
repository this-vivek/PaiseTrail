package com.paisetrail.app.capture

import java.math.BigDecimal
import java.math.RoundingMode

/** Shared across notification and SMS parsers — spec 3.1 / 3.2. */
object TxnTextRules {
    val AMOUNT_REGEX = Regex("""(?:₹|Rs\.?|INR)\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?)""")

    // "paid"/"sent"/"transferred" are directional: "X paid/sent you ₹Y" is a credit (someone else
    // sent the user money) while "you paid/sent X ₹Y" is a debit — the bare keyword can't tell
    // these apart, so it's excluded here whenever immediately followed by "you"/"me" and instead
    // handled by [DIRECTIONAL_CREDIT_REGEX] below.
    private val DEBIT_KEYWORDS = listOf("debited", "withdrawn", "trf to", "payment of")
    private val CREDIT_KEYWORDS =
        listOf("received", "credited", "refund", "reminder", "offer", "cashback", "deposited")
    private val DIRECTIONAL_VERB_REGEX = Regex("""\b(?:paid|sent|transferred)\b(?!\s+(?:you|me)\b)""", RegexOption.IGNORE_CASE)
    private val DIRECTIONAL_CREDIT_REGEX = Regex("""\b(?:paid|sent|transferred)\s+(?:you|me)\b""", RegexOption.IGNORE_CASE)

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
        val hasDebitWord = DEBIT_KEYWORDS.any { text.lowercase().contains(it) } || DIRECTIONAL_VERB_REGEX.containsMatchIn(text)
        val hasCreditWord = CREDIT_KEYWORDS.any { text.lowercase().contains(it) } || DIRECTIONAL_CREDIT_REGEX.containsMatchIn(text)
        return hasDebitWord && !hasCreditWord
    }

    /** The credit counterpart (spec 3.1 point 3's "separate credit/ignore path") — feeds refund
     * matching (spec 3.3 point 5). Deliberately excludes "reminder"/"offer"/"cashback": those are
     * promotional noise, not a real credit event, even though they share the keyword bucket used
     * to REJECT a debit-shaped message. */
    fun isCreditSignal(text: String): Boolean {
        val hasRealCreditWord = REAL_CREDIT_KEYWORDS.any { text.lowercase().contains(it) } || DIRECTIONAL_CREDIT_REGEX.containsMatchIn(text)
        val hasDebitWord = DEBIT_KEYWORDS.any { text.lowercase().contains(it) } || DIRECTIONAL_VERB_REGEX.containsMatchIn(text)
        return hasRealCreditWord && !hasDebitWord
    }

    private val REAL_CREDIT_KEYWORDS = listOf("received", "credited", "refund", "deposited")

    // Marketing blasts for credit lines / cards ("Up to ₹60,000 Credit limit... No Joining Fee...
    // Tap to activate Paytm Postpaid today") can coincidentally carry both a currency-prefixed
    // number and, depending on wording, a debit-shaped word — checked before anything else so a
    // promo never becomes a phantom transaction regardless of what else it happens to contain.
    private val PROMOTIONAL_REGEX = Regex(
        "credit limit|joining fee|no paperwork|activate.*(?:postpaid|card)|" +
            "limited period offer|apply now|pre-approved|instant loan|t&c apply|click here",
        RegexOption.IGNORE_CASE,
    )

    fun isPromotional(text: String): Boolean = PROMOTIONAL_REGEX.containsMatchIn(text)
}
