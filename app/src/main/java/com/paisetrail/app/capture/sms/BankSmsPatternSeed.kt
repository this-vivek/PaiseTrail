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
        // Confirmed against a real UPI-collect alert (2026-07-15):
        //   "A/C *XX9903 debited by Rs 1.00 towards shivanibaunthiyal301@okhdfcbank. RRN:656224868668.
        //    Avl Bal:4421.82. Not you? Call 18602677777 - IndusInd bank"
        BankSmsPatternEntity(
            bankId = "INDUSIND",
            senderSuffix = "INDUSB",
            regex = """A/C\s+\*XX(?<acctLast4>[0-9]+)\s+debited by Rs\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s+towards\s+(?<vpa>\S+@\S+?)\.?\s*RRN:(?<ref>[0-9]+)""",
        ),
        // Confirmed against a real mobile-banking debit alert (2026-07-15):
        //   "Union Bank of India A/c *6623 Debited Rs:1.00 on 15-07-2026 01:04:15 by Mob Bk ref no
        //    656215869888, Fvg: SHIVANI Avl Bal Rs:14511.55. Not you?Call 180023"
        // No payee name in this template (a self-initiated mobile-banking transfer, not a UPI
        // collect) — just amount/account/ref, same as any other bank's format that omits it.
        BankSmsPatternEntity(
            bankId = "UNIONBANK",
            senderSuffix = "UNIONB",
            regex = """A/c\s+\*(?<acctLast4>[0-9]+)\s+Debited\s+Rs:(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s+on\s+[0-9-]+\s+[0-9:]+\s+by\s+Mob Bk ref no\s+(?<ref>[0-9]+)""",
        ),
    )
}
