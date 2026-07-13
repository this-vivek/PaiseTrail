package com.paisetrail.app.export

import com.paisetrail.app.data.db.CategoryDao
import com.paisetrail.app.data.db.CategoryEntity
import com.paisetrail.app.data.db.MerchantDao
import com.paisetrail.app.data.db.MerchantEntity
import com.paisetrail.app.data.db.MerchantVpaEntity
import com.paisetrail.app.data.db.TagSource
import com.paisetrail.app.data.db.TransactionEntity
import com.paisetrail.app.data.db.TxnDirection
import com.paisetrail.app.testutil.FakeTransactionDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private class ImportFakeCategoryDao : CategoryDao {
    val categories = mutableListOf<CategoryEntity>()
    private var nextId = 1L

    override suspend fun insertAll(categories: List<CategoryEntity>) {
        categories.forEach { category ->
            if (this.categories.none { it.name == category.name }) {
                this.categories.add(category.copy(id = nextId++))
            }
        }
    }

    override suspend fun insert(category: CategoryEntity): Long {
        val id = nextId++
        categories.add(category.copy(id = id))
        return id
    }

    override suspend fun update(category: CategoryEntity) {
        val index = categories.indexOfFirst { it.id == category.id }
        if (index >= 0) categories[index] = category
    }

    override suspend fun delete(category: CategoryEntity) {
        categories.removeAll { it.id == category.id }
    }

    override fun observeAll(): Flow<List<CategoryEntity>> = flowOf(categories)
    override suspend fun getAll(): List<CategoryEntity> = categories
    override suspend fun getByName(name: String): CategoryEntity? = categories.firstOrNull { it.name == name }
    override suspend fun getById(id: Long): CategoryEntity? = categories.firstOrNull { it.id == id }

    override suspend fun backfillEmojiIfMissing(name: String, emoji: String) {
        val index = categories.indexOfFirst { it.name == name && it.emoji == null }
        if (index >= 0) categories[index] = categories[index].copy(emoji = emoji)
    }
}

private class ImportFakeMerchantDao : MerchantDao {
    val merchants = mutableListOf<MerchantEntity>()
    private var nextId = 1L

    override suspend fun insert(merchant: MerchantEntity): Long {
        val id = nextId++
        merchants.add(merchant.copy(id = id))
        return id
    }

    override suspend fun update(merchant: MerchantEntity) {
        val index = merchants.indexOfFirst { it.id == merchant.id }
        if (index >= 0) merchants[index] = merchant
    }

    override suspend fun delete(merchant: MerchantEntity) {
        merchants.removeAll { it.id == merchant.id }
    }

    override suspend fun getById(id: Long): MerchantEntity? = merchants.firstOrNull { it.id == id }
    override suspend fun getAll(): List<MerchantEntity> = merchants
    override fun observeAll() = kotlinx.coroutines.flow.flowOf(merchants.toList())
    override suspend fun getByName(canonicalName: String): MerchantEntity? =
        merchants.firstOrNull { it.canonicalName == canonicalName }

    override suspend fun linkVpa(link: MerchantVpaEntity) = Unit
    override suspend fun getMerchantIdForVpa(vpa: String): Long? = null
    override suspend fun getInPersonLocationsForMerchant(merchantId: Long) =
        emptyList<com.paisetrail.app.data.db.LatLngRow>()
}

class DataImporterTest {
    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var categoryDao: ImportFakeCategoryDao
    private lateinit var merchantDao: ImportFakeMerchantDao
    private lateinit var importer: DataImporter

    @Before
    fun setUp() {
        transactionDao = FakeTransactionDao()
        categoryDao = ImportFakeCategoryDao()
        merchantDao = ImportFakeMerchantDao()
        importer = DataImporter(transactionDao, categoryDao, merchantDao)
    }

    private fun bundleJson(vararg transactions: ExportedTransaction): String {
        val bundle = ExportBundle(exportedAt = 1_000L, transactionCount = transactions.size, transactions = transactions.toList())
        return Json.encodeToString(ExportBundle.serializer(), bundle)
    }

    @Test
    fun `imports a transaction and creates a missing category and merchant`() = runTest {
        val result = importer.importFromJson(
            bundleJson(
                ExportedTransaction(
                    amountPaise = 45000L,
                    direction = "DEBIT",
                    status = "CONFIRMED",
                    payeeName = "Sharma Tea Stall",
                    vpa = "sharmatea@ybl",
                    upiRef = "618712345678",
                    occurredAt = 1_000L,
                    categoryName = "Food",
                    merchantName = "Sharma Tea Stall",
                    placeName = "near Lansdowne",
                    locality = "Lansdowne",
                    note = null,
                ),
            ),
        )

        assertEquals(1, result.importedCount)
        assertEquals(0, result.skippedCount)

        val txn = transactionDao.transactions.value.single()
        assertEquals(45000L, txn.amountPaise)
        assertEquals(TxnDirection.DEBIT, txn.direction)
        assertEquals(TagSource.USER, txn.tagSource)
        assertNotNull(txn.categoryId)
        assertNotNull(txn.merchantId)
        assertEquals("Food", categoryDao.getByName("Food")?.name)
        assertEquals("Sharma Tea Stall", merchantDao.getByName("Sharma Tea Stall")?.canonicalName)
    }

    @Test
    fun `reuses an existing category and merchant instead of duplicating`() = runTest {
        categoryDao.insertAll(listOf(CategoryEntity(name = "Food", colorHex = "#8A8B5C")))
        merchantDao.insert(MerchantEntity(canonicalName = "Sharma Tea Stall"))

        importer.importFromJson(
            bundleJson(
                ExportedTransaction(
                    amountPaise = 45000L,
                    direction = "DEBIT",
                    status = "CONFIRMED",
                    payeeName = "Sharma Tea Stall",
                    vpa = null,
                    upiRef = "618712345678",
                    occurredAt = 1_000L,
                    categoryName = "Food",
                    merchantName = "Sharma Tea Stall",
                    placeName = null,
                    locality = null,
                    note = null,
                ),
            ),
        )

        assertEquals(1, categoryDao.categories.size)
        assertEquals(1, merchantDao.merchants.size)
    }

    @Test
    fun `a transaction whose upiRef already exists is skipped`() = runTest {
        transactionDao.insert(
            TransactionEntity(
                amountPaise = 45000L,
                direction = TxnDirection.DEBIT,
                upiRef = "618712345678",
                occurredAt = 1_000L,
            ),
        )

        val result = importer.importFromJson(
            bundleJson(
                ExportedTransaction(
                    amountPaise = 45000L,
                    direction = "DEBIT",
                    status = "CONFIRMED",
                    payeeName = null,
                    vpa = null,
                    upiRef = "618712345678",
                    occurredAt = 1_000L,
                    categoryName = null,
                    merchantName = null,
                    placeName = null,
                    locality = null,
                    note = null,
                ),
            ),
        )

        assertEquals(0, result.importedCount)
        assertEquals(1, result.skippedCount)
        assertEquals(1, transactionDao.transactions.value.size)
    }

    @Test
    fun `a transaction with no category or merchant name imports with null ids`() = runTest {
        importer.importFromJson(
            bundleJson(
                ExportedTransaction(
                    amountPaise = 10000L,
                    direction = "DEBIT",
                    status = "CONFIRMED",
                    payeeName = null,
                    vpa = null,
                    upiRef = null,
                    occurredAt = 1_000L,
                    categoryName = null,
                    merchantName = null,
                    placeName = null,
                    locality = null,
                    note = null,
                ),
            ),
        )

        val txn = transactionDao.transactions.value.single()
        assertNull(txn.categoryId)
        assertNull(txn.merchantId)
        assertEquals(TagSource.NONE, txn.tagSource)
    }

    @Test
    fun `an empty bundle imports nothing`() = runTest {
        val result = importer.importFromJson(bundleJson())

        assertEquals(0, result.importedCount)
        assertEquals(0, result.skippedCount)
    }
}
