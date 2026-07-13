package com.paisetrail.app.util

import java.time.YearMonth
import java.time.ZoneId

/** Epoch-millis bounds for one calendar month in the device timezone — matches how
 * [com.paisetrail.app.data.db.TransactionDao]'s SQLite date functions localize (spec 7.3). */
object MonthRange {
    fun of(month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): LongRange {
        val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return start..end
    }
}
