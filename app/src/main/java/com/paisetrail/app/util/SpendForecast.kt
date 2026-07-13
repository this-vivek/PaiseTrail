package com.paisetrail.app.util

/** Linear burn-rate month-end spend projection (Insights screen): assumes the average daily
 * spend seen so far this month continues for the remaining days. */
object SpendForecast {
    fun projectMonthEnd(currentSpendPaise: Long, daysElapsed: Int, daysInMonth: Int): Long {
        if (daysElapsed <= 0) return currentSpendPaise
        val daysRemaining = (daysInMonth - daysElapsed).coerceAtLeast(0)
        val dailyRate = currentSpendPaise.toDouble() / daysElapsed
        return currentSpendPaise + (dailyRate * daysRemaining).toLong()
    }
}
