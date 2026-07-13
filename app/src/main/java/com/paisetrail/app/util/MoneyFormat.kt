package com.paisetrail.app.util

/** Minimal formatter for background/notification use. The real canonical renderer is
 * `AmountText` (Phase 4, spec 7.7) with full Indian digit grouping — this is a placeholder
 * subset shared by the tag notification and Review Queue until that exists. */
fun formatRupees(amountPaise: Long): String {
    val rupees = amountPaise / 100
    val paise = amountPaise % 100
    return if (paise == 0L) "₹$rupees" else "₹$rupees.${paise.toString().padStart(2, '0')}"
}
