package com.paisetrail.app.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TxnTextRulesTest {

    @Test
    fun `extracts amount with rupee symbol`() {
        assertEquals(45000L, TxnTextRules.extractAmountPaise("You paid ₹450 to Sharma Tea Stall"))
    }

    @Test
    fun `extracts amount with Rs prefix and decimal`() {
        assertEquals(45000L, TxnTextRules.extractAmountPaise("Rs.450.00 debited from a/c **1234"))
    }

    @Test
    fun `extracts amount with thousands separator`() {
        assertEquals(145300_00L, TxnTextRules.extractAmountPaise("₹1,45,300 paid to Landlord"))
    }

    @Test
    fun `extracts amount with INR prefix`() {
        assertEquals(10050L, TxnTextRules.extractAmountPaise("INR 100.50 debited"))
    }

    @Test
    fun `returns null when no amount present`() {
        assertNull(TxnTextRules.extractAmountPaise("Your OTP is 123456"))
    }

    @Test
    fun `amountToPaise strips commas and rounds to integer paise`() {
        assertEquals(45000L, TxnTextRules.amountToPaise("450"))
        assertEquals(45050L, TxnTextRules.amountToPaise("450.50"))
        assertEquals(145300_00L, TxnTextRules.amountToPaise("1,45,300"))
    }

    @Test
    fun `debit signal true for plain paid message`() {
        assertTrue(TxnTextRules.isDebitSignal("You paid ₹450 to Sharma Tea Stall"))
    }

    @Test
    fun `debit signal false when credited keyword present`() {
        assertFalse(TxnTextRules.isDebitSignal("₹450 received from Sharma Tea Stall"))
    }

    @Test
    fun `debit signal false for refund message`() {
        assertFalse(TxnTextRules.isDebitSignal("₹450 refund credited to your account"))
    }

    @Test
    fun `debit signal false for cashback offer`() {
        assertFalse(TxnTextRules.isDebitSignal("Get ₹50 cashback offer on your next payment"))
    }

    @Test
    fun `debit signal false when neither debit nor credit keyword present`() {
        assertFalse(TxnTextRules.isDebitSignal("Your account balance is ₹450"))
    }

    @Test
    fun `debit signal false for someone else paying the user`() {
        // Regression: "paid you" is a credit (someone else sent the user money) — the bare "paid"
        // keyword alone can't distinguish it from "you paid X", a real debit.
        assertFalse(TxnTextRules.isDebitSignal("MUSKAN PARIHAR paid you ₹300.00"))
    }

    @Test
    fun `credit signal true for someone else paying the user`() {
        assertTrue(TxnTextRules.isCreditSignal("MUSKAN PARIHAR paid you ₹300.00"))
    }

    @Test
    fun `debit signal false for someone else sending the user money`() {
        assertFalse(TxnTextRules.isDebitSignal("Rohit sent you ₹500"))
    }

    @Test
    fun `credit signal true for someone else sending the user money`() {
        assertTrue(TxnTextRules.isCreditSignal("Rohit sent you ₹500"))
    }

    @Test
    fun `debit signal still true for the user paying someone else`() {
        assertTrue(TxnTextRules.isDebitSignal("You paid ₹450 to Sharma Tea Stall"))
    }

    @Test
    fun `debit signal still true for the user sending someone else money`() {
        assertTrue(TxnTextRules.isDebitSignal("You sent ₹500 to Rohit"))
    }

    @Test
    fun `promotional true for Postpaid credit-limit marketing text`() {
        // Real captured notification that was wrongly turned into a transaction.
        assertTrue(
            TxnTextRules.isPromotional(
                "Up to ₹60,000 Credit limit No Joining Fee No Joining Fee No Paperwork Tap to activate Paytm Postpaid today",
            ),
        )
    }

    @Test
    fun `promotional false for a real payment message`() {
        assertFalse(TxnTextRules.isPromotional("You paid ₹450 to Sharma Tea Stall"))
    }
}
