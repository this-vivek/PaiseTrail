package com.paisetrail.app.capture.notification

import com.paisetrail.app.capture.ParsedTxn
import com.paisetrail.app.capture.TxnTextRules
import com.paisetrail.app.data.db.TxnDirection

/** "You paid ₹450 to Sharma Tea Stall" / "₹450 paid to <name>" (spec 3.1). Credit counterpart
 * ("You received ₹450 from X") is unverified against a real notification — same caveat as any
 * pattern built from the spec's example text rather than a captured sample (spec 3.3 point 5's
 * refund matching needs *some* credit signal to match against, so it's here despite that). */
class GPayNotificationParser : NotificationParser {
    private val payeeRegex = Regex(""".*\bto\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val payerRegex = Regex(""".*\bfrom\s+(.+)$""", RegexOption.IGNORE_CASE)

    override fun parse(title: String?, text: String?, bigText: String?, subText: String?): ParsedTxn? {
        val combined = listOfNotNull(title, bigText ?: text, subText).joinToString(" ")
        val amountPaise = TxnTextRules.extractAmountPaise(combined) ?: return null

        if (TxnTextRules.isDebitSignal(combined)) {
            val payee = payeeRegex.find(combined)?.groupValues?.get(1)?.trim(' ', '.')
            return ParsedTxn(amountPaise = amountPaise, payeeName = payee, direction = TxnDirection.DEBIT)
        }
        if (TxnTextRules.isCreditSignal(combined)) {
            val payer = payerRegex.find(combined)?.groupValues?.get(1)?.trim(' ', '.')
            return ParsedTxn(amountPaise = amountPaise, payeeName = payer, direction = TxnDirection.CREDIT)
        }
        return null
    }
}
