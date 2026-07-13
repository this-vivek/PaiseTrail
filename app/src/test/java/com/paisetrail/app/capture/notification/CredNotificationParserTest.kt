package com.paisetrail.app.capture.notification

import com.paisetrail.app.data.db.TxnDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CredNotificationParserTest {
    private val parser = CredNotificationParser()

    @Test
    fun `parses amount paid via CRED UPI with payee in title`() {
        val result = parser.parse(
            title = "Sharma Tea Stall",
            text = "₹450 paid via CRED UPI",
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
            title = "Sharma Tea Stall",
            text = "₹450 refund credited via CRED UPI",
            bigText = null,
            subText = null,
        )
        requireNotNull(result)
        assertEquals(45000L, result.amountPaise)
        assertEquals("Sharma Tea Stall", result.payeeName)
        assertEquals(TxnDirection.CREDIT, result.direction)
    }

    @Test
    fun `returns null when amount missing`() {
        val result = parser.parse(
            title = "Sharma Tea Stall",
            text = "Your bill was paid via CRED UPI",
            bigText = null,
            subText = null,
        )
        assertNull(result)
    }
}
