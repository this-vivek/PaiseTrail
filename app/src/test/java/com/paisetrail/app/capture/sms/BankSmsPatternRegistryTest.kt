package com.paisetrail.app.capture.sms

import com.paisetrail.app.data.db.BankSmsPatternDao
import com.paisetrail.app.data.db.BankSmsPatternEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeBankSmsPatternDao(
    private val patterns: List<BankSmsPatternEntity>,
) : BankSmsPatternDao {
    override suspend fun insertAll(patterns: List<BankSmsPatternEntity>) = Unit
    override suspend fun upsert(pattern: BankSmsPatternEntity) = Unit
    override suspend fun delete(pattern: BankSmsPatternEntity) = Unit
    override fun observeAll() = kotlinx.coroutines.flow.flowOf(patterns)
    override suspend fun getAll(): List<BankSmsPatternEntity> = patterns
    override suspend fun getEnabled(): List<BankSmsPatternEntity> = patterns.filter { it.enabled }
    override suspend fun count(): Int = patterns.size
}

class BankSmsPatternRegistryTest {
    private lateinit var registry: BankSmsPatternRegistry

    // Real HDFC UPI-send alert captured on-device 2026-07-13.
    private val realHdfcBody = "Sent Rs.1.00\n" +
        "From HDFC Bank A/C *6996\n" +
        "To Vivek Singh Rawat\n" +
        "On 13/07/26\n" +
        "Ref 656020609612\n" +
        "Not You?\n" +
        "Call 18002586161/SMS BLOCK UPI to 7308080808"

    @Before
    fun setUp() {
        registry = BankSmsPatternRegistry(FakeBankSmsPatternDao(BankSmsPatternSeed.DEFAULT_PATTERNS))
    }

    @Test
    fun `matches real HDFC UPI-send alert`() = runTest {
        val match = registry.match(sender = "VM-HDFCBK-T", body = realHdfcBody)
        assertTrue(match.senderRecognized)
        val result = requireNotNull(match.parsed)
        assertEquals(100L, result.amountPaise)
        assertEquals("6996", result.acctLast4)
        assertEquals("Vivek Singh Rawat", result.payeeName)
        assertEquals("656020609612", result.refId)
    }

    @Test
    fun `sender code in the middle of the id is recognized`() = runTest {
        // Real sender IDs carry a route prefix AND a trailing suffix around the bank code.
        for (sender in listOf("VM-HDFCBK-T", "AX-HDFCBK-S", "VD-HDFCBK-T")) {
            val match = registry.match(sender = sender, body = realHdfcBody)
            assertTrue("expected $sender to be recognized", match.senderRecognized)
            assertEquals(100L, match.parsed?.amountPaise)
        }
    }

    @Test
    fun `matches ICICI debit SMS`() = runTest {
        val match = registry.match(
            sender = "AD-ICICIB",
            body = "Acct XX123 debited with Rs 450.00 on 11-Jul-26; ABC CAFE credited. UPI:618712345678",
        )
        val result = requireNotNull(match.parsed)
        assertEquals(45000L, result.amountPaise)
        assertEquals("123", result.acctLast4)
        assertEquals("ABC CAFE", result.payeeName)
        assertEquals("618712345678", result.refId)
    }

    @Test
    fun `matches SBI debit SMS`() = runTest {
        val match = registry.match(
            sender = "JD-SBIUPI",
            body = "Dear UPI user A/C X1234 debited by 450.0 on date 11Jul26 trf to SHARMA TEA Refno 618712345678",
        )
        val result = requireNotNull(match.parsed)
        assertEquals(45000L, result.amountPaise)
        assertEquals("1234", result.acctLast4)
        assertEquals("SHARMA TEA", result.payeeName)
        assertEquals("618712345678", result.refId)
    }

    @Test
    fun `sender match is case insensitive`() = runTest {
        val match = registry.match(sender = "vm-hdfcbk-t", body = realHdfcBody)
        assertTrue(match.senderRecognized)
        assertEquals(100L, match.parsed?.amountPaise)
    }

    @Test
    fun `unrecognized sender is not recognized and has no parse result`() = runTest {
        val match = registry.match(sender = "AX-UNKNOWN", body = realHdfcBody)
        assertFalse(match.senderRecognized)
        assertNull(match.parsed)
    }

    @Test
    fun `recognized sender with non-matching body is recognized but has no parse result`() = runTest {
        val match = registry.match(
            sender = "VM-HDFCBK-T",
            body = "Your OTP for login is 123456. Do not share it with anyone.",
        )
        assertTrue(match.senderRecognized)
        assertNull(match.parsed)
    }

    @Test
    fun `falls through to a second pattern for the same bank when the first doesn't match`() = runTest {
        val twoTemplatesForHdfc = BankSmsPatternRegistry(
            FakeBankSmsPatternDao(
                listOf(
                    // A plausible but wrong IMPS-style template that won't match the real UPI body.
                    BankSmsPatternEntity(
                        bankId = "HDFC",
                        senderSuffix = "HDFCBK",
                        regex = """IMPS of Rs\.(?<amount>[0-9.]+) credited""",
                    ),
                    BankSmsPatternSeed.DEFAULT_PATTERNS.first { it.bankId == "HDFC" },
                ),
            ),
        )

        val match = twoTemplatesForHdfc.match(sender = "VM-HDFCBK-T", body = realHdfcBody)

        assertTrue(match.senderRecognized)
        assertEquals(100L, match.parsed?.amountPaise)
        assertEquals("Vivek Singh Rawat", match.parsed?.payeeName)
    }

    @Test
    fun `matches real IndusInd UPI-collect alert`() = runTest {
        // Real bug repro (2026-07-15): this exact body previously fell through every pattern and
        // never created a transaction at all, so the tag popup never fired.
        val match = registry.match(
            sender = "AX-INDUSB-S",
            body = "A/C *XX9903 debited by Rs 1.00 towards shivanibaunthiyal301@okhdfcbank. " +
                "RRN:656224868668. Avl Bal:4421.82. Not you? Call 18602677777 - IndusInd bank",
        )
        assertTrue(match.senderRecognized)
        val result = requireNotNull(match.parsed)
        assertEquals(100L, result.amountPaise)
        assertEquals("9903", result.acctLast4)
        assertEquals("shivanibaunthiyal301@okhdfcbank", result.vpa)
        assertEquals("656224868668", result.refId)
    }

    @Test
    fun `matches real Union Bank of India mobile-banking debit alert`() = runTest {
        // Real bug repro (2026-07-15): same as the IndusInd case above.
        val match = registry.match(
            sender = "JD-UNIONB-T",
            body = "Union Bank of India A/c *6623 Debited Rs:1.00 on 15-07-2026 01:04:15 by Mob Bk " +
                "ref no 656215869888, Fvg: SHIVANI Avl Bal Rs:14511.55. Not you?Call 180023",
        )
        assertTrue(match.senderRecognized)
        val result = requireNotNull(match.parsed)
        assertEquals(100L, result.amountPaise)
        assertEquals("6623", result.acctLast4)
        assertEquals("656215869888", result.refId)
    }

    @Test
    fun `disabled pattern is not recognized`() = runTest {
        val disabledOnly = BankSmsPatternRegistry(
            FakeBankSmsPatternDao(
                BankSmsPatternSeed.DEFAULT_PATTERNS.map { it.copy(enabled = false) },
            ),
        )
        val match = disabledOnly.match(sender = "VM-HDFCBK-T", body = realHdfcBody)
        assertFalse(match.senderRecognized)
        assertNull(match.parsed)
    }
}
