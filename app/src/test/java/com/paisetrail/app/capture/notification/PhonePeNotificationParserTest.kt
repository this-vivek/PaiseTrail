package com.paisetrail.app.capture.notification

import com.paisetrail.app.data.db.TxnDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class PhonePeNotificationParserTest {
    private val parser = PhonePeNotificationParser()

    @Test
    fun `parses Payment of to is successful format`() {
        val result = parser.parse(
            title = "PhonePe",
            text = "Payment of ₹450 to Sharma Tea Stall is successful",
            bigText = null,
            subText = null,
        )
        requireNotNull(result)
        assertEquals(45000L, result.amountPaise)
        assertEquals("Sharma Tea Stall", result.payeeName)
        assertEquals(TxnDirection.DEBIT, result.direction)
    }

    @Test
    fun `falls back to title for payee when body has no name`() {
        val result = parser.parse(
            title = "Sharma Tea Stall",
            text = "Paid ₹450",
            bigText = null,
            subText = null,
        )
        requireNotNull(result)
        assertEquals(45000L, result.amountPaise)
        assertEquals("Sharma Tea Stall", result.payeeName)
    }

    @Test
    fun `prefers bigText over text`() {
        val result = parser.parse(
            title = "PhonePe",
            text = "truncated",
            bigText = "Payment of ₹1,200 to Landlord is successful",
            subText = null,
        )
        requireNotNull(result)
        assertEquals(120000L, result.amountPaise)
        assertEquals("Landlord", result.payeeName)
    }

    @Test
    fun `parses credit notification as CREDIT direction`() {
        // No "is successful" suffix in this shape, so the payer regex doesn't match and falls
        // back to title — same fallback the debit path already uses.
        val result = parser.parse(
            title = "PhonePe",
            text = "₹450 received from Sharma Tea Stall",
            bigText = null,
            subText = null,
        )
        requireNotNull(result)
        assertEquals(45000L, result.amountPaise)
        assertEquals(TxnDirection.CREDIT, result.direction)
    }
}
