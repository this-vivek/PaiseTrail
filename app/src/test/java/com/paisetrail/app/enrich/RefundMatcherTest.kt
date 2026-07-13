package com.paisetrail.app.enrich

import com.paisetrail.app.data.db.TransactionEntity
import com.paisetrail.app.data.db.TxnDirection
import com.paisetrail.app.data.db.TxnStatus
import com.paisetrail.app.testutil.FakeTransactionDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RefundMatcherTest {
    private lateinit var dao: FakeTransactionDao
    private lateinit var matcher: RefundMatcher

    @Before
    fun setUp() {
        dao = FakeTransactionDao()
        matcher = RefundMatcher(dao)
    }

    private suspend fun insertDebit(
        amountPaise: Long,
        payeeName: String? = null,
        vpa: String? = null,
        occurredAt: Long,
        status: TxnStatus = TxnStatus.CONFIRMED,
    ): Long = dao.insert(
        TransactionEntity(
            amountPaise = amountPaise,
            direction = TxnDirection.DEBIT,
            payeeNameRaw = payeeName,
            vpa = vpa,
            occurredAt = occurredAt,
            status = status,
        ),
    )

    private suspend fun insertCredit(
        amountPaise: Long,
        payeeName: String? = null,
        vpa: String? = null,
        occurredAt: Long,
    ): Long = dao.insert(
        TransactionEntity(
            amountPaise = amountPaise,
            direction = TxnDirection.CREDIT,
            payeeNameRaw = payeeName,
            vpa = vpa,
            occurredAt = occurredAt,
        ),
    )

    @Test
    fun `matching vpa and amount within 48h links refund and flips debit to REFUNDED`() = runTest {
        val debitId = insertDebit(amountPaise = 45000L, vpa = "sharmatea@ybl", occurredAt = 1_000L)
        val creditId = insertCredit(amountPaise = 45000L, vpa = "sharmatea@ybl", occurredAt = 2_000L)

        matcher.tryMatchRefund(creditId)

        assertEquals(TxnStatus.REFUNDED, dao.getById(debitId)?.status)
        assertEquals(debitId, dao.getById(creditId)?.refundOfTxnId)
    }

    @Test
    fun `matching payee tokens without a vpa also links the refund`() = runTest {
        val debitId = insertDebit(amountPaise = 45000L, payeeName = "Sharma Tea Stall", occurredAt = 1_000L)
        val creditId = insertCredit(amountPaise = 45000L, payeeName = "Sharma Tea Stall", occurredAt = 2_000L)

        matcher.tryMatchRefund(creditId)

        assertEquals(TxnStatus.REFUNDED, dao.getById(debitId)?.status)
    }

    @Test
    fun `credit outside the 48h window does not match`() = runTest {
        val debitId = insertDebit(amountPaise = 45000L, vpa = "sharmatea@ybl", occurredAt = 0L)
        val creditId = insertCredit(amountPaise = 45000L, vpa = "sharmatea@ybl", occurredAt = 49L * 60 * 60 * 1000)

        matcher.tryMatchRefund(creditId)

        assertEquals(TxnStatus.CONFIRMED, dao.getById(debitId)?.status)
        assertNull(dao.getById(creditId)?.refundOfTxnId)
    }

    @Test
    fun `different amount does not match even with same vpa`() = runTest {
        val debitId = insertDebit(amountPaise = 45000L, vpa = "sharmatea@ybl", occurredAt = 1_000L)
        val creditId = insertCredit(amountPaise = 50000L, vpa = "sharmatea@ybl", occurredAt = 2_000L)

        matcher.tryMatchRefund(creditId)

        assertEquals(TxnStatus.CONFIRMED, dao.getById(debitId)?.status)
    }

    @Test
    fun `neither vpa nor payee overlap does not match`() = runTest {
        val debitId = insertDebit(amountPaise = 45000L, payeeName = "Sharma Tea Stall", occurredAt = 1_000L)
        val creditId = insertCredit(amountPaise = 45000L, payeeName = "Ola Cabs", occurredAt = 2_000L)

        matcher.tryMatchRefund(creditId)

        assertEquals(TxnStatus.CONFIRMED, dao.getById(debitId)?.status)
    }

    @Test
    fun `already refunded debit is not matched again`() = runTest {
        val debitId = insertDebit(
            amountPaise = 45000L,
            vpa = "sharmatea@ybl",
            occurredAt = 1_000L,
            status = TxnStatus.REFUNDED,
        )
        val creditId = insertCredit(amountPaise = 45000L, vpa = "sharmatea@ybl", occurredAt = 2_000L)

        matcher.tryMatchRefund(creditId)

        assertNull(dao.getById(creditId)?.refundOfTxnId)
        assertEquals(TxnStatus.REFUNDED, dao.getById(debitId)?.status)
    }

    @Test
    fun `debit transactions are never treated as refund candidates`() = runTest {
        val firstDebitId = insertDebit(amountPaise = 45000L, vpa = "sharmatea@ybl", occurredAt = 1_000L)

        matcher.tryMatchRefund(firstDebitId)

        assertEquals(TxnStatus.CONFIRMED, dao.getById(firstDebitId)?.status)
        assertNull(dao.getById(firstDebitId)?.refundOfTxnId)
    }
}
