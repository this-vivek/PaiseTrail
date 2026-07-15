package com.paisetrail.app.enrich

/**
 * Rule cascade (spec 4.3), Phase 2 slice only: reverse-geocoded place type / amount+context
 * heuristics are not implemented yet. First hit wins; falls back to Uncategorized so nothing is
 * ever left with no guess at all — the tag popup and Review Queue both need *some* category name
 * to show.
 */
object CategoryGuesser {
    const val UNCATEGORIZED = "Uncategorized"
    const val P2P = "P2P Transfer"

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

    /** Suffixes/keywords that mark a settlement name as a registered business rather than a
     * person, so [looksLikePersonName] doesn't mistake e.g. "UFF COSMETICS LLP" for a person. */
    private val BUSINESS_SUFFIX_REGEX = Regex(
        "\\b(pvt|ltd|llp|limited|enterprises?|stores?|mart|shop|traders?|foods?|restaurant|hotel|" +
            "pay(?:tm)?|bank|services?|solutions?|technologies|india|company|corp|cafe|kitchen|" +
            "centre|center|clinic|hospital|pharmacy|academy|school|college|university|" +
            "association|society|trust|foundation|infra|constructions?|motors?|electronics?|" +
            "textiles?|apparels?|garments?|cosmetics?|jewell?ers?|opticals?|automobiles?)\\b",
        RegexOption.IGNORE_CASE,
    )

    /** Best single guess from payee name / VPA text. */
    fun guess(payeeName: String?, vpa: String?): String {
        val text = listOfNotNull(payeeName, vpa).joinToString(" ")
        if (text.isBlank()) return UNCATEGORIZED
        return KEYWORD_RULES.firstOrNull { (regex, _) -> regex.containsMatchIn(text) }?.second ?: UNCATEGORIZED
    }

    /** A payee that reads like a person's name rather than a registered business — plain letters
     * and spaces, a handful of words, none of the entity suffixes bank statements append, and no
     * keyword-rule match (those are curated business signals). Bank-settlement names are often
     * garbled ("DANISH ALI SHAH SO SH SH") but still read as clearly personal. Used to offer "P2P
     * Transfer" as a suggestion for money sent directly to someone rather than paid to a merchant. */
    fun looksLikePersonName(payeeName: String?): Boolean {
        if (payeeName.isNullOrBlank()) return false
        val trimmed = payeeName.trim()
        if (BUSINESS_SUFFIX_REGEX.containsMatchIn(trimmed)) return false
        if (KEYWORD_RULES.any { (regex, _) -> regex.containsMatchIn(trimmed) }) return false
        val tokens = trimmed.split(Regex("\\s+"))
        // Real bank settlement names for P2P transfers are often garbled well past a normal name's
        // word count ("DANISH ALI SHAH SO SH SH") — bounded high enough to still catch those but
        // not so high a paragraph-length business description slips through.
        if (tokens.isEmpty() || tokens.size > 8) return false
        return tokens.all { token -> token.isNotEmpty() && token.all { it.isLetter() } }
    }

    /** Up to 3 categories for the tag popup's inline action buttons (spec 5, Android caps
     * notification actions at 3) and the Review Queue's quick-suggestion chips.
     *
     * Priority order for the leading (highlighted) suggestion: [learnedCategoryName] (this
     * merchant's own tagging history, spec 4.2) beats a keyword match beats [looksLikePersonName]
     * beats [UNCATEGORIZED]. [fallbackCategories] — the caller's own most-used categories overall —
     * only fill remaining slots when there's already a *real* per-transaction signal to lead with;
     * padding a payee with zero signal using the same globally-popular categories made every
     * unrecognized business look identical and suggested false confidence, which defeated the
     * point of "dynamic to existing taggings" — a genuinely unknown payee now honestly shows just
     * [UNCATEGORIZED] alongside the "choose another category" escape hatch instead. */
    fun topPredictions(
        payeeName: String?,
        vpa: String?,
        learnedCategoryName: String? = null,
        fallbackCategories: List<String> = emptyList(),
    ): List<String> {
        val keywordGuess = guess(payeeName, vpa)
        val isPerson = looksLikePersonName(payeeName)
        val primary = when {
            learnedCategoryName != null -> learnedCategoryName
            keywordGuess != UNCATEGORIZED -> keywordGuess
            isPerson -> P2P
            else -> UNCATEGORIZED
        }
        val hasRealSignal = primary != UNCATEGORIZED
        val candidates = buildList {
            add(primary)
            if (isPerson && primary != P2P) add(P2P)
            if (keywordGuess != UNCATEGORIZED && primary != keywordGuess) add(keywordGuess)
            if (hasRealSignal) addAll(fallbackCategories)
            add(UNCATEGORIZED)
        }
        return candidates.distinct().take(3)
    }
}
