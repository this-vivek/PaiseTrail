package com.paisetrail.app.ui.screens.dashboard

import java.time.Instant
import java.time.ZoneId

/** Epoch-millis boundaries for the Dashboard's aggregate queries (spec 7.1) — device timezone,
 * matching how [com.paisetrail.app.data.db.TransactionDao]'s SQLite date functions localize. */
internal object DateWindows {
    fun startOfMonth(epochMillis: Long, monthsAgo: Long = 0): Long =
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .withDayOfMonth(1)
            .minusMonths(monthsAgo)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
}
