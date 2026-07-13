package com.paisetrail.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SpendForecastTest {
    @Test
    fun `projects linearly from the average daily rate so far`() {
        val projected = SpendForecast.projectMonthEnd(currentSpendPaise = 10_00000, daysElapsed = 10, daysInMonth = 30)

        assertEquals(30_00000L, projected)
    }

    @Test
    fun `no days elapsed returns current spend unchanged`() {
        val projected = SpendForecast.projectMonthEnd(currentSpendPaise = 5_00000, daysElapsed = 0, daysInMonth = 30)

        assertEquals(5_00000L, projected)
    }

    @Test
    fun `last day of month returns current spend unchanged`() {
        val projected = SpendForecast.projectMonthEnd(currentSpendPaise = 42_00000, daysElapsed = 30, daysInMonth = 30)

        assertEquals(42_00000L, projected)
    }
}
