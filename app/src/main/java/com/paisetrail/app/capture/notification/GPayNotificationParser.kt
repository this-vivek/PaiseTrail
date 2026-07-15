package com.paisetrail.app.capture.notification

import com.paisetrail.app.capture.ParsedTxn
import com.paisetrail.app.capture.TxnTextRules
import com.paisetrail.app.data.db.TxnDirection

/** "You paid ₹450 to Sharma Tea Stall" (debit) / "MUSKAN PARIHAR paid you ₹300.00" (credit —
 * someone else's name leads the notification, spec 3.1). */
class GPayNotificationParser : NotificationParser {
    private val payeeRegex = Regex(""".*\bto\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val payerFromRegex = Regex(""".*\bfrom\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val payerLeadingNameRegex = Regex("""^(.+?)\s+(?:paid|sent|transferred)\s+(?:you|me)\b""", RegexOption.IGNORE_CASE)

    override fun parse(title: String?, text: String?, bigText: String?, subText: String?): ParsedTxn? {
        val combined = listOfNotNull(title, bigText ?: text, subText).joinToString(" ")
        if (TxnTextRules.isPromotional(combined)) return null
        val amountPaise = TxnTextRules.extractAmountPaise(combined) ?: return null

        if (TxnTextRules.isDebitSignal(combined)) {
            val payee = payeeRegex.find(combined)?.groupValues?.get(1)?.trim(' ', '.')
            return ParsedTxn(amountPaise = amountPaise, payeeName = payee, direction = TxnDirection.DEBIT)
        }
        if (TxnTextRules.isCreditSignal(combined)) {
            // "X paid you ₹Y" carries the payer's name before the verb, not after "from" — tried
            // against just the body (not [combined], which has the title/app-name prepended and
            // would otherwise get swept into the match too) since it's the shape GPay's own credit
            // notifications actually use.
            val body = bigText ?: text ?: ""
            val payer = payerLeadingNameRegex.find(body)?.groupValues?.get(1)?.trim(' ', '.')
                ?: payerFromRegex.find(combined)?.groupValues?.get(1)?.trim(' ', '.')
            return ParsedTxn(amountPaise = amountPaise, payeeName = payer, direction = TxnDirection.CREDIT)
        }
        return null
    }
}
