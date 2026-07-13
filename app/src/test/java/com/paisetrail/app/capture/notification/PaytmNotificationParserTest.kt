package com.paisetrail.app.capture.notification

import com.paisetrail.app.data.db.TxnDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class PaytmNotificationParserTest {
    private val parser = PaytmNotificationParser()

    @Test
    fun `parses Rs paid to name format`() {
        val result = parser.parse(
            title = "Paytm",
            text = "Rs.450 paid to Sharma Tea Stall",
            bigText = null,
            subText = null,
        )
        requireNotNull(result)
        assertEquals(45000L, result.amountPaise)
        assertEquals("Sharma Tea Stall", result.payeeName)
        assertEquals(TxnDirection.DEBIT, result.direction)
    }

    @Test
    fun `parses credit notification as CREDIT direction`() {
        val result = parser.parse(
            title = "Paytm",
            text = "Rs.450 received from Sharma Tea Stall",
            bigText = null,
            subText = null,
        )
        requireNotNull(result)
        assertEquals(45000L, result.amountPaise)
        assertEquals("Sharma Tea Stall", result.payeeName)
        assertEquals(TxnDirection.CREDIT, result.direction)
    }
}
