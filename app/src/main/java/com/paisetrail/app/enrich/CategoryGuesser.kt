package com.paisetrail.app.enrich

/**
 * Rule cascade (spec 4.3), Phase 2 slice only: merchant-learned defaultCategory (needs
 * MerchantResolver, Phase 3) and reverse-geocoded place type / amount+context heuristics are not
 * implemented yet. First hit wins; falls back to Uncategorized so nothing is ever left with no
 * guess at all — the tag popup and Review Queue both need *some* category name to show.
 */
object CategoryGuesser {
    const val UNCATEGORIZED = "Uncategorized"

    private val KEYWORD_RULES: List<Pair<Regex, String>> = listOf(
        Regex("tea|cafe|coffee|restaurant|dhaba|zomato|swiggy", RegexOption.IGNORE_CASE) to "Food",
        Regex("petrol|diesel|fuel|hpcl|iocl|bpcl", RegexOption.IGNORE_CASE) to "Fuel",
        Regex("irctc|redbus|ola|uber|rapido", RegexOption.IGNORE_CASE) to "Travel",
        Regex("oyo|hotel|treebo|resort", RegexOption.IGNORE_CASE) to "Stay",
        Regex("grocery|bigbasket|blinkit|zepto|dmart", RegexOption.IGNORE_CASE) to "Groceries",
        Regex("electricity|broadband|recharge|dth|billdesk", RegexOption.IGNORE_CASE) to "Bills",
        Regex("netflix|spotify|bookmyshow|hotstar|prime video", RegexOption.IGNORE_CASE) to "Entertainment",
        Regex("pharmacy|hospital|clinic|apollo|medplus", RegexOption.IGNORE_CASE) to "Health",
        Regex("amazon|flipkart|myntra", RegexOption.IGNORE_CASE) to "Shopping",
    )

    /** Best single guess from payee name / VPA text. */
    fun guess(payeeName: String?, vpa: String?): String {
        val text = listOfNotNull(payeeName, vpa).joinToString(" ")
        if (text.isBlank()) return UNCATEGORIZED
        return KEYWORD_RULES.firstOrNull { (regex, _) -> regex.containsMatchIn(text) }?.second ?: UNCATEGORIZED
    }

    /** Up to 3 categories for the tag popup's inline action buttons (spec 5) — Android allows a
     * max of 3 notification actions. Without a learned merchant history yet, the best we can do
     * beyond the primary guess is offer the two most common categories as quick alternatives. */
    fun topPredictions(payeeName: String?, vpa: String?): List<String> {
        val primary = guess(payeeName, vpa)
        val commonDefaults = listOf("Food", "Travel", "Shopping")
        return (listOf(primary) + commonDefaults).distinct().take(3)
    }
}
