package com.paisetrail.app.enrich

/** Shared by [LocationStampingUseCase] (per-transaction ONLINE/IN_PERSON payment context) and
 * [MerchantResolver] (a merchant's persistent isOnline flag) — same signal, two different
 * lifetimes (spec 4.1). */
object OnlineMerchantHeuristic {
    private val ONLINE_MERCHANT_REGEX = Regex(
        "razorpay|payu|billdesk|zomato|swiggy|amazon|flipkart|irctc|jio|airtel",
        RegexOption.IGNORE_CASE,
    )

    fun isOnlineMerchant(payeeName: String?, vpa: String?): Boolean {
        val text = listOfNotNull(payeeName, vpa).joinToString(" ")
        return text.isNotBlank() && ONLINE_MERCHANT_REGEX.containsMatchIn(text)
    }
}
