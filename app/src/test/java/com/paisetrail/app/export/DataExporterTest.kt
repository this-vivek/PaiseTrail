package com.paisetrail.app.export

import com.paisetrail.app.data.db.CategoryDao
import com.paisetrail.app.data.db.CategoryEntity
import com.paisetrail.app.data.db.MerchantDao
import com.paisetrail.app.data.db.MerchantEntity
import com.paisetrail.app.data.db.MerchantVpaEntity
import com.paisetrail.app.data.db.TransactionEntity
import com.paisetrail.app.data.db.TxnDirection
import com.paisetrail.app.testutil.FakeTransactionDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeCategoryDao(private val categories: List<CategoryEntity>) : CategoryDao {
    override suspend fun insertAll(categories: List<CategoryEntity>) = Unit
    override suspend fun insert(category: CategoryEntity): Long = 0L
    override suspend fun update(category: CategoryEntity) = Unit
    override suspend fun delete(category: CategoryEntity) = Unit
    override fun observeAll(): Flow<List<CategoryEntity>> = flowOf(categories)
    override suspend fun getAll(): List<CategoryEntity> = categories
    override suspend fun getByName(name: String): CategoryEntity? = categories.firstOrNull { it.name == name }
    override suspend fun getById(id: Long): CategoryEntity? = categories.firstOrNull { it.id == id }
    override suspend fun backfillEmojiIfMissing(name: String, emoji: String) = Unit
    override suspend fun backfillColorIfDefault(name: String, colorHex: String) = Unit
}

private class FakeMerchantDao(private val merchants: List<MerchantEntity>) : MerchantDao {
    override suspend fun insert(merchant: MerchantEntity) = 0L
    override suspend fun update(merchant: MerchantEntity) = Unit
    override suspend fun delete(merchant: MerchantEntity) = Unit
    override suspend fun getById(id: Long): MerchantEntity? = merchants.firstOrNull { it.id == id }
    override suspend fun getAll(): List<MerchantEntity> = merchants
    override fun observeAll() = kotlinx.coroutines.flow.flowOf(merchants)
    override suspend fun getByName(canonicalName: String): MerchantEntity? =
        merchants.firstOrNull { it.canonicalName == canonicalName }
    override suspend fun linkVpa(link: MerchantVpaEntity) = Unit
    override suspend fun getMerchantIdForVpa(vpa: String): Long? = null
    override suspend fun getInPersonLocationsForMerchant(merchantId: Long) = emptyList<com.paisetrail.app.data.db.LatLngRow>()
}

class DataExporterTest {
    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var exporter: DataExporter

    @Before
    fun setUp() {
        transactionDao = FakeTransactionDao()
        val categoryDao = FakeCategoryDao(listOf(CategoryEntity(id = 1L, name = "Food", colorHex = "#8A8B5C")))
        val merchantDao = FakeMerchantDao(listOf(MerchantEntity(id = 1L, canonicalName = "Sharma Tea Stall")))
        exporter = DataExporter(transactionDao, categoryDao, merchantDao)
    }

    @Test
    fun `exports transactions with resolved category and merchant names`() = runTest {
        transactionDao.insert(
            TransactionEntity(
                amountPaise = 45000L,
                direction = TxnDirection.DEBIT,
                payeeNameRaw = "Sharma Tea Stall",
                categoryId = 1L,
                merchantId = 1L,
                occurredAt = 1_000L,
            ),
        )

        val jsonString = exporter.buildExportJson()
        val bundle = Json.decodeFromString(ExportBundle.serializer(), jsonString)

        assertEquals(1, bundle.transactionCount)
        val exported = bundle.transactions.single()
        assertEquals(45000L, exported.amountPaise)
        assertEquals("Food", exported.categoryName)
        assertEquals("Sharma Tea Stall", exported.merchantName)
    }

    @Test
    fun `handles a transaction with no category or merchant`() = runTest {
        transactionDao.insert(
            TransactionEntity(amountPaise = 10000L, direction = TxnDirection.DEBIT, occurredAt = 1_000L),
        )

        val jsonString = exporter.buildExportJson()
        val bundle = Json.decodeFromString(ExportBundle.serializer(), jsonString)

        val exported = bundle.transactions.single()
        assertTrue(exported.categoryName == null)
        assertTrue(exported.merchantName == null)
    }

    @Test
    fun `empty database exports an empty bundle`() = runTest {
        val jsonString = exporter.buildExportJson()
        val bundle = Json.decodeFromString(ExportBundle.serializer(), jsonString)

        assertEquals(0, bundle.transactionCount)
        assertTrue(bundle.transactions.isEmpty())
    }
}
