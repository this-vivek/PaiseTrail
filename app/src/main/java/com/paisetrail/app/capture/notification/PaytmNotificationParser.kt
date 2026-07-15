package com.paisetrail.app.capture.notification

import com.paisetrail.app.capture.ParsedTxn
import com.paisetrail.app.capture.TxnTextRules
import com.paisetrail.app.data.db.TxnDirection

/** "Rs.450 paid to <name>" (spec 3.1). Credit counterpart unverified against a real
 * notification — see [GPayNotificationParser]'s note. */
class PaytmNotificationParser : NotificationParser {
    private val payeeRegex = Regex(""".*\bto\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val payerRegex = Regex(""".*\bfrom\s+(.+)$""", RegexOption.IGNORE_CASE)

    override fun parse(title: String?, text: String?, bigText: String?, subText: String?): ParsedTxn? {
        val combined = listOfNotNull(title, bigText ?: text, subText).joinToString(" ")
        if (TxnTextRules.isPromotional(combined)) return null
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
