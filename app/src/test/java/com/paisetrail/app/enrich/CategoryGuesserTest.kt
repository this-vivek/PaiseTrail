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
    fun `topPredictions does not duplicate the primary guess in defaults`() {
        val predictions = CategoryGuesser.topPredictions("Ola Cabs", null)
        assertEquals(listOf("Travel", "Food", "Shopping"), predictions)
    }
}
