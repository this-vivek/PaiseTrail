package com.paisetrail.app.capture.notification

import com.paisetrail.app.capture.ParsedTxn
import com.paisetrail.app.capture.TxnTextRules
import com.paisetrail.app.data.db.TxnDirection

/** "₹450 paid via CRED UPI" — payee often in title, not the body (spec 3.1). Credit counterpart
 * unverified against a real notification — see [GPayNotificationParser]'s note. */
class CredNotificationParser : NotificationParser {
    override fun parse(title: String?, text: String?, bigText: String?, subText: String?): ParsedTxn? {
        val body = bigText ?: text ?: ""
        val combined = listOfNotNull(title, body, subText).joinToString(" ")
        val amountPaise = TxnTextRules.extractAmountPaise(combined) ?: return null
        val name = title?.trim()?.takeIf { it.isNotBlank() }

        if (TxnTextRules.isDebitSignal(combined)) {
            return ParsedTxn(amountPaise = amountPaise, payeeName = name, direction = TxnDirection.DEBIT)
        }
        if (TxnTextRules.isCreditSignal(combined)) {
            return ParsedTxn(amountPaise = amountPaise, payeeName = name, direction = TxnDirection.CREDIT)
        }
        return null
    }
}
