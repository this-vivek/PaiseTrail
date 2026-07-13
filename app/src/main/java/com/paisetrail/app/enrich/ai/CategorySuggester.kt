package com.paisetrail.app.enrich.ai

/** One "which category does this transaction belong to" opinion-source — [GeminiNanoCategorySuggester]
 * (on-device Gemini Nano, when the phone's AICore supports it) and [LocalCategorySuggester] (an
 * offline similarity match against the user's own already-tagged history, the guaranteed
 * fallback) both implement this the same way so [AiCategoryTagger] can try one after the other. */
interface CategorySuggester {
    /** Whether this suggester can be used right now — the local one is always ready; the Nano one
     * may need a one-time model download, or may not be available on this device at all. */
    suspend fun ensureReady(): Boolean

    /** Null means "no opinion" (not just low confidence) — the caller moves on to the next
     * suggester rather than tagging with a guess. */
    suspend fun suggestCategory(payeeName: String?, vpa: String?, categoryNames: List<String>): String?

    /** One line explaining the most recent [ensureReady]/[suggestCategory] outcome — surfaced to
     * the user so "0 tagged by AI" isn't a silent mystery. */
    fun statusDescription(): String
}
