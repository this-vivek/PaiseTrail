package com.paisetrail.app.capture

import com.paisetrail.app.data.db.RawEventDao
import com.paisetrail.app.data.db.RawEventEntity
import com.paisetrail.app.data.db.RawEventSource
import com.paisetrail.app.data.db.TripEntity
import com.paisetrail.app.data.db.TxnDirection
import com.paisetrail.app.data.db.TxnStatus
import com.paisetrail.app.enrich.TransactionEnrichmentTrigger
import com.paisetrail.app.testutil.FakeTransactionDao
import com.paisetrail.app.testutil.FakeTripDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeRawEventDao : RawEventDao {
    val events = MutableStateFlow<List<RawEventEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(event: RawEventEntity): Long {
        val id = nextId++
        events.value = events.value + event.copy(id = id)
        return id
    }

    override suspend fun update(event: RawEventEntity) {
        events.value = events.value.map { if (it.id == event.id) event else it }
    }

    override fun observeRecent(limit: Int) = events.map { it.take(limit) }

    override suspend fun getUnlinked(): List<RawEventEntity> = events.value.filter { it.txnId == null }

    override suspend fun getInWindow(fromMillis: Long, toMillis: Long): List<RawEventEntity> =
        events.value.filter { it.postedAt in fromMillis..toMillis }
}

class RawEventIngestorTest {
    private lateinit var rawEventDao: FakeRawEventDao
    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var tripDao: FakeTripDao
    private lateinit var newTransactionIds: MutableList<Long>
    private lateinit var ingestor: RawEventIngestor

    private val notifTxn = ParsedTxn(
        amountPaise = 45000L,
        payeeName = "Sharma Tea Stall",
        direction = TxnDirection.DEBIT,
    )
    private val smsTxn = ParsedTxn(
        amountPaise = 45000L,
        vpa = "sharmatea@ybl",
        refId = "618712345678",
        acctLast4 = "1234",
        direction = TxnDirection.DEBIT,
    )

    @Before
    fun setUp() {
        rawEventDao = FakeRawEventDao()
        transactionDao = FakeTransactionDao()
        tripDao = FakeTripDao()
        newTransactionIds = mutableListOf()
        ingestor = RawEventIngestor(
            rawEventDao,
            transactionDao,
            tripDao,
            TransactionEnrichmentTrigger { txnId -> newTransactionIds.add(txnId) },
        )
    }

    @Test
    fun `notification then SMS with shared ref merges into one transaction`() = runTest {
        ingestor.ingest(RawEventSource.NOTIFICATION, "com.google.android.apps.nbu.paisa.user", "notif text", 1_000L, notifTxn)
        ingestor.ingest(RawEventSource.SMS, "VM-HDFCBK", "sms text", 1_010L, smsTxn.copy(refId = null))

        assertEquals(1, transactionDao.transactions.value.size)
    }

    @Test
    fun `enrichment trigger fires exactly once for a new transaction, not on the merging update`() = runTest {
        ingestor.ingest(RawEventSource.NOTIFICATION, "com.google.android.apps.nbu.paisa.user", "notif text", 1_000L, notifTxn)
        ingestor.ingest(RawEventSource.SMS, "VM-HDFCBK", "sms text", 1_010L, smsTxn.copy(refId = null))

        val txnId = transactionDao.transactions.value.single().id
        assertEquals(listOf(txnId), newTransactionIds)
    }

    @Test
    fun `enrichment trigger does not fire for unparseable events`() = runTest {
        ingestor.ingest(RawEventSource.NOTIFICATION, "com.google.android.apps.nbu.paisa.user", "not a payment", 1_000L, null)

        assertTrue(newTransactionIds.isEmpty())
    }

    @Test
    fun `SMS then notification arrival order also merges into one transaction`() = runTest {
        ingestor.ingest(RawEventSource.SMS, "VM-HDFCBK", "sms text", 1_010L, smsTxn.copy(refId = null))
        ingestor.ingest(RawEventSource.NOTIFICATION, "com.google.android.apps.nbu.paisa.user", "notif text", 1_000L, notifTxn)

        assertEquals(1, transactionDao.transactions.value.size)
    }

    @Test
    fun `fuzzy merge fields follow merge policy from spec`() = runTest {
        ingestor.ingest(RawEventSource.NOTIFICATION, "com.google.android.apps.nbu.paisa.user", "notif text", 1_000L, notifTxn)
        ingestor.ingest(RawEventSource.SMS, "VM-HDFCBK", "sms text", 1_010L, smsTxn.copy(refId = null))

        val txn = transactionDao.transactions.value.single()
        assertEquals("Sharma Tea Stall", txn.payeeNameRaw)
        assertEquals("sharmatea@ybl", txn.vpa)
        assertEquals("1234", txn.bankAcctLast4)
        assertEquals(1_000L, txn.occurredAt)
    }

    @Test
    fun `strong key merge via shared UPI ref regardless of order`() = runTest {
        ingestor.ingest(RawEventSource.SMS, "VM-HDFCBK", "first sms", 2_000L, smsTxn)
        ingestor.ingest(RawEventSource.SMS, "VM-HDFCBK", "duplicate delivery", 2_500L, smsTxn)

        assertEquals(1, transactionDao.transactions.value.size)
        assertEquals("618712345678", transactionDao.transactions.value.single().upiRef)
    }

    @Test
    fun `different amounts within fuzzy window do not merge`() = runTest {
        ingestor.ingest(RawEventSource.NOTIFICATION, "com.google.android.apps.nbu.paisa.user", "notif text", 1_000L, notifTxn)
        ingestor.ingest(
            RawEventSource.SMS,
            "VM-HDFCBK",
            "sms text",
            1_010L,
            smsTxn.copy(refId = null, amountPaise = 50000L),
        )

        assertEquals(2, transactionDao.transactions.value.size)
    }

    @Test
    fun `same amount and payee within the fuzzy window but different refs do not merge`() = runTest {
        // Three real-world repeat payments of the same amount to the same person minutes apart —
        // each carries its own distinct UPI ref, which must override the amount+payee+time match.
        val hdfcSend = ParsedTxn(
            amountPaise = 100L,
            payeeName = "Vivek Singh Rawat",
            direction = TxnDirection.DEBIT,
        )
        ingestor.ingest(RawEventSource.SMS, "VM-HDFCBK-T", "sms 1", 1_000L, hdfcSend.copy(refId = "656007576262"))
        ingestor.ingest(RawEventSource.SMS, "VM-HDFCBK-T", "sms 2", 1_050L, hdfcSend.copy(refId = "656020610497"))
        ingestor.ingest(RawEventSource.SMS, "AX-HDFCBK-S", "sms 3", 1_090L, hdfcSend.copy(refId = "656008589580"))

        assertEquals(3, transactionDao.transactions.value.size)
        assertEquals(
            setOf("656007576262", "656020610497", "656008589580"),
            transactionDao.transactions.value.mapNotNull { it.upiRef }.toSet(),
        )
    }

    @Test
    fun `fuzzy merge still succeeds when only one side carries a ref`() = runTest {
        // The conflict check only blocks a merge when BOTH sides carry a ref and they differ —
        // a ref-less notification followed by the SMS twin that carries the real ref must still
        // merge normally.
        ingestor.ingest(RawEventSource.NOTIFICATION, "com.google.android.apps.nbu.paisa.user", "notif text", 1_000L, notifTxn)
        ingestor.ingest(RawEventSource.SMS, "VM-HDFCBK", "sms text", 1_010L, smsTxn)

        assertEquals(1, transactionDao.transactions.value.size)
        assertEquals("618712345678", transactionDao.transactions.value.single().upiRef)
    }

    @Test
    fun `events outside fuzzy window do not merge`() = runTest {
        ingestor.ingest(RawEventSource.NOTIFICATION, "com.google.android.apps.nbu.paisa.user", "notif text", 0L, notifTxn)
        ingestor.ingest(RawEventSource.SMS, "VM-HDFCBK", "sms text", 300_000L, smsTxn.copy(refId = null))

        assertEquals(2, transactionDao.transactions.value.size)
    }

    @Test
    fun `unparseable event is persisted as raw event but creates no transaction`() = runTest {
        ingestor.ingest(RawEventSource.NOTIFICATION, "com.google.android.apps.nbu.paisa.user", "not a payment", 1_000L, null)

        assertEquals(1, rawEventDao.events.value.size)
        assertEquals(0, transactionDao.transactions.value.size)
        assertNull(rawEventDao.events.value.single().txnId)
    }

    @Test
    fun `a new transaction is auto-tagged to the active trip`() = runTest {
        tripDao.insert(TripEntity(name = "Ladakh", startAt = 500L))

        ingestor.ingest(RawEventSource.NOTIFICATION, "com.google.android.apps.nbu.paisa.user", "notif text", 1_000L, notifTxn)

        assertEquals(tripDao.trips.value.single().id, transactionDao.transactions.value.single().tripId)
    }

    @Test
    fun `a new transaction is not tagged to a trip when none is active`() = runTest {
        ingestor.ingest(RawEventSource.NOTIFICATION, "com.google.android.apps.nbu.paisa.user", "notif text", 1_000L, notifTxn)

        assertNull(transactionDao.transactions.value.single().tripId)
    }

    @Test
    fun `a payment to yourself is inserted as SELF_TRANSFER, not CONFIRMED`() = runTest {
        val selfPayment = ParsedTxn(
            amountPaise = 500000L,
            payeeName = "VIVEK SINGH RAWAT",
            direction = TxnDirection.DEBIT,
        )
        ingestor.ingest(RawEventSource.SMS, "VM-HDFCBK", "sms text", 1_000L, selfPayment)

        assertEquals(TxnStatus.SELF_TRANSFER, transactionDao.transactions.value.single().status)
    }

    @Test
    fun `a payment to a real merchant stays CONFIRMED`() = runTest {
        ingestor.ingest(RawEventSource.NOTIFICATION, "com.google.android.apps.nbu.paisa.user", "notif text", 1_000L, notifTxn)

        assertEquals(TxnStatus.CONFIRMED, transactionDao.transactions.value.single().status)
    }

    @Test
    fun `raw events are linked to the merged transaction id`() = runTest {
        ingestor.ingest(RawEventSource.NOTIFICATION, "com.google.android.apps.nbu.paisa.user", "notif text", 1_000L, notifTxn)
        ingestor.ingest(RawEventSource.SMS, "VM-HDFCBK", "sms text", 1_010L, smsTxn.copy(refId = null))

        val txnId = transactionDao.transactions.value.single().id
        assertEquals(2, rawEventDao.events.value.size)
        assertEquals(listOf(txnId, txnId), rawEventDao.events.value.map { it.txnId })
    }
}
