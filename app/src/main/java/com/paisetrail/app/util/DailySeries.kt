package com.paisetrail.app.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Fills in zero-amount entries for any calendar day missing from a sparse day-keyed map, so a
 * daily bar chart's x-axis stays a true continuous calendar timeline instead of silently
 * collapsing gaps — without this, a quiet day just vanishes from the series rather than showing
 * as an empty bar, so bars end up evenly spaced regardless of the real gaps between their dates
 * and the day-number labels look inconsistent with the spacing. */
object DailySeries {
    private val DAY_KEY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** The [n] calendar days ending today (inclusive) in [zone], oldest first, as "yyyy-MM-dd" keys
     * matching the format `TransactionDao`'s SQLite `strftime` queries already produce. */
    fun lastNDays(n: Int, nowMillis: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): List<String> {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return (n - 1 downTo 0).map { DAY_KEY_FORMAT.format(today.minusDays(it.toLong())) }
    }

    fun <T> zeroFill(days: List<String>, sparse: Map<String, T>, zero: T): List<T> = days.map { sparse[it] ?: zero }
}
