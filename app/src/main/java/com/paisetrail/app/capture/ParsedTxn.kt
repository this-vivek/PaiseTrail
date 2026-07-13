package com.paisetrail.app.capture

import com.paisetrail.app.data.db.TxnDirection

/** Amount is stored in paise (integer), never Float/Double — spec 3.1. */
data class ParsedTxn(
    val amountPaise: Long,
    val payeeName: String? = null,
    val vpa: String? = null,
    val refId: String? = null,
    val acctLast4: String? = null,
    val direction: TxnDirection,
)
