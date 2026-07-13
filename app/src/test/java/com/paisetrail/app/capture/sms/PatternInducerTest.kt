package com.paisetrail.app.capture.sms

import kotlin.text.MatchNamedGroupCollection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternInducerTest {
    // The confirmed real HDFC UPI-debit alert text from BankSmsPatternSeed's doc comment.
    private val hdfcExample1 = """
        Sent Rs.1.00
        From HDFC Bank A/C *6996
        To Vivek Singh Rawat
        On 13/07/26
        Ref 656020609612
        Not You?
        Call 18002586161/SMS BLOCK UPI to 7308080808
    """.trimIndent()

    private val hdfcExample2 = """
        Sent Rs.450.50
        From HDFC Bank A/C *6996
        To Cafe Coffee Day
        On 20/07/26
        Ref 778812345678
        Not You?
        Call 18002586161/SMS BLOCK UPI to 7308080808
    """.trimIndent()

    @Test
    fun `single example induces a regex that extracts every field correctly`() {
        val induced = PatternInducer.induce(listOf(hdfcExample1))

        assertNotNull(induced)
        assertEquals(1, induced!!.matchedCount)
        assertEquals(1, induced.totalCount)
        assertTrue("amount" in induced.fields)
        assertTrue("acctLast4" in induced.fields)
        assertTrue("payee" in induced.fields)
        assertTrue("ref" in induced.fields)

        val regex = Regex(induced.regex, RegexOption.DOT_MATCHES_ALL)
        val result = regex.find(hdfcExample1)
        assertNotNull(result)
        val groups = result!!.groups as MatchNamedGroupCollection
        assertEquals("1.00", groups["amount"]?.value)
        assertEquals("6996", groups["acctLast4"]?.value)
        assertEquals("Vivek Singh Rawat", groups["payee"]?.value)
        assertEquals("656020609612", groups["ref"]?.value)
    }

    @Test
    fun `two examples of the same template both match the induced regex`() {
        val induced = PatternInducer.induce(listOf(hdfcExample1, hdfcExample2))

        assertNotNull(induced)
        assertEquals(2, induced!!.matchedCount)
        assertEquals(2, induced.totalCount)

        val regex = Regex(induced.regex, RegexOption.DOT_MATCHES_ALL)
        val secondResult = regex.find(hdfcExample2)
        val groups = secondResult!!.groups as MatchNamedGroupCollection
        assertEquals("450.50", groups["amount"]?.value)
        assertEquals("Cafe Coffee Day", groups["payee"]?.value)
    }

    @Test
    fun `an unrelated example lowers the matched count instead of crashing`() {
        val induced = PatternInducer.induce(listOf(hdfcExample1, "Your OTP is 482910, valid for 5 minutes"))

        assertNotNull(induced)
        assertEquals(1, induced!!.matchedCount)
        assertEquals(2, induced.totalCount)
    }

    @Test
    fun `text with no amount produces no pattern`() {
        val induced = PatternInducer.induce(listOf("Your OTP is 482910, valid for 5 minutes"))

        assertNull(induced)
    }

    @Test
    fun `blank input produces no pattern`() {
        assertNull(PatternInducer.induce(emptyList()))
        assertNull(PatternInducer.induce(listOf("   ")))
    }

    // Real IndusInd debit-card alert reported as failing to induce a pattern at all: the "Avl
    // BAL" figure is a second number matching the amount rule, which used to produce two
    // (?<amount>...) groups in one regex — invalid, so induce() silently returned null.
    private val indusIndExample =
        "INR 500.00 debited from your A/C 158***279903 towards Debit Card Purchase. " +
            "Avl BAL INR 2,774.12 - Not you? Call 18602677777 to report issue - IndusInd Bank"

    @Test
    fun `a debited-amount plus Avl Bal figure both being present no longer crashes induction`() {
        val induced = PatternInducer.induce(listOf(indusIndExample))

        assertNotNull(induced)
        assertEquals(1, induced!!.matchedCount)
        assertTrue("amount" in induced.fields)

        val regex = Regex(induced.regex, RegexOption.DOT_MATCHES_ALL)
        val result = regex.find(indusIndExample)
        assertNotNull(result)
        val groups = result!!.groups as MatchNamedGroupCollection
        assertEquals("500.00", groups["amount"]?.value)
    }

    @Test
    fun `account number masked in the middle still captures the true last 4 digits`() {
        val induced = PatternInducer.induce(listOf(indusIndExample))

        val regex = Regex(induced!!.regex, RegexOption.DOT_MATCHES_ALL)
        val result = regex.find(indusIndExample)!!
        val groups = result.groups as MatchNamedGroupCollection
        assertEquals("9903", groups["acctLast4"]?.value)
    }

    // Real Union Bank of India debit alert reported as failing to induce a pattern at all: "Rs:"
    // (colon, no space) wasn't recognized by the amount rule, which only allowed whitespace
    // between the currency keyword and the number.
    private val unionBankExample = "A/c *6623 Debited for Rs:130.00 on 18-05-2026 18:52:24 by Mob Bk ref no " +
        "650416143597 Avl Bal Rs:1144.03.If not you, Call 1800222243 -Union Bank of India"

    @Test
    fun `a colon between the currency keyword and the amount is recognized`() {
        val induced = PatternInducer.induce(listOf(unionBankExample))

        assertNotNull(induced)
        val regex = Regex(induced!!.regex, RegexOption.DOT_MATCHES_ALL)
        val result = regex.find(unionBankExample)
        assertNotNull(result)
        val groups = result!!.groups as MatchNamedGroupCollection
        assertEquals("130.00", groups["amount"]?.value)
        assertEquals("6623", groups["acctLast4"]?.value)
        assertEquals("650416143597", groups["ref"]?.value)
    }

    @Test
    fun `a same-day different-time second example still cross-matches`() {
        val laterSameDay = unionBankExample.replace("18:52:24", "09:03:11").replace("130.00", "75.50")

        val induced = PatternInducer.induce(listOf(unionBankExample, laterSameDay))

        assertNotNull(induced)
        assertEquals(2, induced!!.matchedCount)
    }
}
