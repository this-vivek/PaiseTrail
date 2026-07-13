package com.paisetrail.app.util

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DailySeriesTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val july13Noon = ZonedDateTime.of(2026, 7, 13, 12, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `lastNDays returns n consecutive calendar days ending today, oldest first`() {
        val days = DailySeries.lastNDays(7, july13Noon, zone)

        assertEquals(
            listOf("2026-07-07", "2026-07-08", "2026-07-09", "2026-07-10", "2026-07-11", "2026-07-12", "2026-07-13"),
            days,
        )
    }

    @Test
    fun `zeroFill fills gaps with the zero value and keeps calendar order`() {
        val days = listOf("2026-07-11", "2026-07-12", "2026-07-13")
        val sparse = mapOf("2026-07-11" to 500L, "2026-07-13" to 900L)

        val filled = DailySeries.zeroFill(days, sparse, zero = 0L)

        assertEquals(listOf(500L, 0L, 900L), filled)
    }

    @Test
    fun `zeroFill works with non-numeric zero values like empty lists`() {
        val days = listOf("2026-07-12", "2026-07-13")
        val sparse = mapOf("2026-07-13" to listOf("Food"))

        val filled = DailySeries.zeroFill(days, sparse, zero = emptyList<String>())

        assertEquals(listOf(emptyList(), listOf("Food")), filled)
    }
}
