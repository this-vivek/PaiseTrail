package com.paisetrail.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Data, not code (spec 3.2) — new banks are added rows, not new classes. [regex] must use
 * named capture groups from {amount, payee, vpa, ref, acctLast4}; any subset is fine, whichever
 * groups the bank's SMS format actually carries.
 *
 * [bankId] is deliberately NOT the primary key: real banks send several distinct SMS templates
 * for what's semantically the same debit event (a plain UPI transfer reads very differently from
 * an IMPS or a card-swipe alert from the same bank), so more than one pattern can legitimately
 * share a [bankId] — [com.paisetrail.app.capture.sms.BankSmsPatternRegistry] tries every
 * sender-matching, enabled pattern in turn until one's regex actually parses the message. The
 * unique index is on (bankId, regex) instead, so re-seeding the same
 * [com.paisetrail.app.capture.sms.BankSmsPatternSeed] rows stays a no-op without blocking a
 * second, genuinely different pattern for the same bank.
 */
@Entity(tableName = "bank_sms_patterns", indices = [Index(value = ["bankId", "regex"], unique = true)])
data class BankSmsPatternEntity(
    val bankId: String,
    val senderSuffix: String,
    val regex: String,
    val enabled: Boolean = true,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
)
