package com.paisetrail.app.capture.notification

import com.paisetrail.app.capture.ParsedTxn
import com.paisetrail.app.capture.TxnTextRules
import com.paisetrail.app.data.db.TxnDirection

/** "Payment of ₹450 to <name> is successful" / "Paid ₹450" with payee sometimes in title (spec
 * 3.1). Credit counterpart unverified against a real notification — see
 * [GPayNotificationParser]'s note. */
class PhonePeNotificationParser : NotificationParser {
    private val payeeRegex = Regex("""to\s+(.+?)\s+is successful""", RegexOption.IGNORE_CASE)
    private val payerRegex = Regex("""from\s+(.+?)\s+is successful""", RegexOption.IGNORE_CASE)

    override fun parse(title: String?, text: String?, bigText: String?, subText: String?): ParsedTxn? {
        val body = bigText ?: text ?: ""
        val combined = listOfNotNull(title, body, subText).joinToString(" ")
        if (TxnTextRules.isPromotional(combined)) return null
        val amountPaise = TxnTextRules.extractAmountPaise(combined) ?: return null

        if (TxnTextRules.isDebitSignal(combined)) {
            val payee = payeeRegex.find(body)?.groupValues?.get(1)?.trim(' ', '.')
                ?: title?.trim()?.takeIf { it.isNotBlank() }
            return ParsedTxn(amountPaise = amountPaise, payeeName = payee, direction = TxnDirection.DEBIT)
        }
        if (TxnTextRules.isCreditSignal(combined)) {
            val payer = payerRegex.find(body)?.groupValues?.get(1)?.trim(' ', '.')
                ?: title?.trim()?.takeIf { it.isNotBlank() }
            return ParsedTxn(amountPaise = amountPaise, payeeName = payer, direction = TxnDirection.CREDIT)
        }
        return null
    }
}
