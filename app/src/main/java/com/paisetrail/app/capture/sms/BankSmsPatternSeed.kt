package com.paisetrail.app.capture.sms

import com.paisetrail.app.data.db.BankSmsPatternEntity

/**
 * Data, not code (spec 3.2) — starter rows for the three banks named in the plan. Sender IDs
 * carry both a route prefix and a trailing suffix around the bank code (e.g. "VM-HDFCBK-T",
 * "AX-HDFCBK-S") — [BankSmsPatternRegistry] matches [BankSmsPatternEntity.senderSuffix] as a
 * substring, not a true suffix, to account for that.
 * Regex must use named groups from {amount, payee, vpa, ref, acctLast4} — whichever the format
 * carries. Formats drift; verify against real SMS during the phase-1 field test and edit here.
 *
 * HDFC's regex below is confirmed against a real UPI-send alert (2026-07-13):
 *   "Sent Rs.1.00\nFrom HDFC Bank A/C *6996\nTo Vivek Singh Rawat\nOn 13/07/26\nRef 656020609612\n
 *    Not You?\nCall 18002586161/SMS BLOCK UPI to 7308080808"
 * ICICI and SBI below are still spec-derived example text, unverified against a real SMS — expect
 * them to need the same kind of correction once real messages are seen.
 */
object BankSmsPatternSeed {
    val DEFAULT_PATTERNS = listOf(
        BankSmsPatternEntity(
            bankId = "HDFC",
            senderSuffix = "HDFCBK",
            regex = """Sent\s+Rs\.?\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s+From HDFC Bank A/C\s*\*+(?<acctLast4>[0-9]{4})\s+To\s+(?<payee>.+?)\s+On\s+[0-9/]+\s+Ref\s+(?<ref>[0-9]+)""",
        ),
        BankSmsPatternEntity(
            bankId = "ICICI",
            senderSuffix = "ICICIB",
            regex = """Acct\s+XX(?<acctLast4>[0-9]+)\s+debited with Rs\.?\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?).*?;\s*(?<payee>.+?)\s+credited.*?UPI:?\s*(?<ref>[0-9]+)""",
        ),
        BankSmsPatternEntity(
            bankId = "SBI",
            senderSuffix = "SBIUPI",
            regex = """A/C X(?<acctLast4>[0-9]+)\s+debited by\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?).*?trf to\s+(?<payee>.+?)\s+Refno\.?\s*(?<ref>[0-9]+)""",
        ),
    )
}
