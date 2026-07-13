package com.paisetrail.app.util

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthRangeTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    @Test
    fun `range starts at the first instant of the month`() {
        val range = MonthRange.of(YearMonth.of(2026, 7), zone)

        val start = Instant.ofEpochMilli(range.first).atZone(zone)
        assertEquals(2026, start.year)
        assertEquals(7, start.monthValue)
        assertEquals(1, start.dayOfMonth)
        assertEquals(0, start.hour)
    }

    @Test
    fun `range ends the millisecond before the next month starts`() {
        val range = MonthRange.of(YearMonth.of(2026, 7), zone)
        val nextMonthStart = MonthRange.of(YearMonth.of(2026, 8), zone).first

        assertEquals(nextMonthStart - 1, range.last)
    }

    @Test
    fun `february in a leap year has 29 days`() {
        val range = MonthRange.of(YearMonth.of(2028, 2), zone)

        val end = Instant.ofEpochMilli(range.last).atZone(zone)
        assertEquals(29, end.dayOfMonth)
    }

    @Test
    fun `a timestamp inside the month is contained in the range`() {
        val range = MonthRange.of(YearMonth.of(2026, 7), zone)
        val midMonth = Instant.parse("2026-07-15T10:00:00Z").toEpochMilli()

        assertTrue(midMonth in range)
    }
}
