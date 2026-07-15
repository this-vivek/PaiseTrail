package com.paisetrail.app.enrich

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryGuesserTest {

    @Test
    fun `guesses Food for tea stall payee`() {
        assertEquals("Food", CategoryGuesser.guess("Sharma Tea Stall", null))
    }

    @Test
    fun `guesses Fuel for petrol pump payee`() {
        assertEquals("Fuel", CategoryGuesser.guess("HPCL Petrol Pump", null))
    }

    @Test
    fun `guesses Travel for ola vpa`() {
        assertEquals("Travel", CategoryGuesser.guess(null, "ola@ybl"))
    }

    @Test
    fun `is case insensitive`() {
        assertEquals("Stay", CategoryGuesser.guess("OYO ROOMS DELHI", null))
    }

    @Test
    fun `falls back to Uncategorized when nothing matches`() {
        assertEquals("Uncategorized", CategoryGuesser.guess("Random Person", null))
    }

    @Test
    fun `falls back to Uncategorized when both inputs are null`() {
        assertEquals("Uncategorized", CategoryGuesser.guess(null, null))
    }

    @Test
    fun `first matching rule wins`() {
        // Contains both a food and fuel keyword — food rule is earlier in the cascade.
        assertEquals("Food", CategoryGuesser.guess("Cafe near HPCL pump", null))
    }

    @Test
    fun `topPredictions puts the primary guess first and returns at most 3`() {
        val predictions = CategoryGuesser.topPredictions("Sharma Tea Stall", null)
        assertEquals("Food", predictions.first())
        assert(predictions.size <= 3)
    }

    @Test
    fun `topPredictions does not duplicate the keyword guess`() {
        val predictions = CategoryGuesser.topPredictions("Ola Cabs", null)
        assertEquals(listOf("Travel", "Uncategorized"), predictions)
    }

    @Test
    fun `topPredictions leads with the merchant-learned category over the keyword guess`() {
        // Decathlon has no keyword rule of its own, but the user tagged it Shopping before —
        // that learned signal should lead, not a generic guess.
        val predictions = CategoryGuesser.topPredictions("DECATHLON SPORTS INDIA", null, learnedCategoryName = "Shopping")
        assertEquals("Shopping", predictions.first())
    }

    @Test
    fun `topPredictions does not pad a payee with zero real signal using the global fallback`() {
        // Regression: padding every unrecognized business with the same globally-popular
        // categories made unrelated payees show identical suggestions — worse than the original
        // hardcoded-defaults complaint this feature was meant to fix. A payee with no keyword
        // match, no learned category, and no person-name pattern should honestly show just
        // Uncategorized rather than a false-confidence guess.
        val predictions = CategoryGuesser.topPredictions(
            "XYZ123 Retail Outlet",
            null,
            fallbackCategories = listOf("Groceries", "Bills"),
        )
        assertEquals(listOf("Uncategorized"), predictions)
    }

    @Test
    fun `topPredictions fills remaining slots from usage history only once there's a real signal to lead with`() {
        val predictions = CategoryGuesser.topPredictions(
            "Sharma Tea Stall",
            null,
            fallbackCategories = listOf("Groceries", "Bills"),
        )
        assertEquals(listOf("Food", "Groceries", "Bills"), predictions)
    }

    @Test
    fun `topPredictions offers P2P for a payee that looks like a person's name`() {
        val predictions = CategoryGuesser.topPredictions("Pankaj Upreti", null)
        assert(predictions.contains(CategoryGuesser.P2P))
    }

    @Test
    fun `topPredictions does not offer P2P for a registered business name`() {
        val predictions = CategoryGuesser.topPredictions("UFF COSMETICS LLP", null)
        assert(!predictions.contains(CategoryGuesser.P2P))
    }

    @Test
    fun `looksLikePersonName is true for a garbled but clearly personal bank settlement name`() {
        assert(CategoryGuesser.looksLikePersonName("DANISH ALI SHAH SO SH SH"))
    }

    @Test
    fun `looksLikePersonName is false for a keyword-matched business`() {
        assert(!CategoryGuesser.looksLikePersonName("Ola Cabs"))
    }

    @Test
    fun `looksLikePersonName is false for null or blank`() {
        assert(!CategoryGuesser.looksLikePersonName(null))
        assert(!CategoryGuesser.looksLikePersonName("  "))
    }
}
