package com.paisetrail.app.capture.notification

import com.paisetrail.app.data.db.TxnDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GPayNotificationParserTest {
    private val parser = GPayNotificationParser()

    @Test
    fun `parses You paid to name format`() {
        val result = parser.parse(
            title = "Google Pay",
            text = "You paid ₹450 to Sharma Tea Stall",
            bigText = null,
            subText = null,
        )
        requireNotNull(result)
        assertEquals(45000L, result.amountPaise)
        assertEquals("Sharma Tea Stall", result.payeeName)
        assertEquals(TxnDirection.DEBIT, result.direction)
    }

    @Test
    fun `parses amount paid to name format`() {
        val result = parser.parse(
            title = "Google Pay",
            text = "₹450 paid to Sharma Tea Stall",
            bigText = null,
            subText = null,
        )
        requireNotNull(result)
        assertEquals(45000L, result.amountPaise)
        assertEquals("Sharma Tea Stall", result.payeeName)
    }

    @Test
    fun `prefers bigText over text when both present`() {
        val result = parser.parse(
            title = "Google Pay",
            text = "truncated",
            bigText = "You paid ₹1,200 to Landlord",
            subText = null,
        )
        requireNotNull(result)
        assertEquals(120000L, result.amountPaise)
        assertEquals("Landlord", result.payeeName)
    }

    @Test
    fun `parses credit notification as CREDIT direction`() {
        val result = parser.parse(
            title = "Google Pay",
            text = "You received ₹450 from Sharma Tea Stall",
            bigText = null,
            subText = null,
        )
        requireNotNull(result)
        assertEquals(45000L, result.amountPaise)
        assertEquals("Sharma Tea Stall", result.payeeName)
        assertEquals(TxnDirection.CREDIT, result.direction)
    }

    @Test
    fun `parses someone-else-paid-you notification as CREDIT with their name`() {
        // Real GPay notification shape (bug repro): the payer's name leads, not "You received...
        // from X" — the naive "paid" keyword alone previously misclassified this as a DEBIT.
        val result = parser.parse(
            title = "Google Pay",
            text = "MUSKAN PARIHAR paid you ₹300.00",
            bigText = null,
            subText = null,
        )
        requireNotNull(result)
        assertEquals(30000L, result.amountPaise)
        assertEquals("MUSKAN PARIHAR", result.payeeName)
        assertEquals(TxnDirection.CREDIT, result.direction)
    }

    @Test
    fun `returns null when amount missing despite debit keyword`() {
        val result = parser.parse(
            title = "Google Pay",
            text = "Your payment to Sharma Tea Stall was paid successfully",
            bigText = null,
            subText = null,
        )
        assertNull(result)
    }
}
