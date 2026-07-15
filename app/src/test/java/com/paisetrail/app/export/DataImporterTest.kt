package com.paisetrail.app.export

import com.paisetrail.app.data.db.CategoryDao
import com.paisetrail.app.data.db.CategoryEntity
import com.paisetrail.app.data.db.LocationQuality
import com.paisetrail.app.data.db.MerchantDao
import com.paisetrail.app.data.db.MerchantEntity
import com.paisetrail.app.data.db.MerchantVpaEntity
import com.paisetrail.app.data.db.TagSource
import com.paisetrail.app.data.db.TransactionEntity
import com.paisetrail.app.data.db.TxnDirection
import com.paisetrail.app.enrich.ApproxLocationTrigger
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

    override suspend fun backfillColorIfDefault(name: String, colorHex: String) {
        val index = categories.indexOfFirst { it.name == name && it.colorHex == "#9AA0B0" }
        if (index >= 0) categories[index] = categories[index].copy(colorHex = colorHex)
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

private class FakeApproxLocationTrigger : ApproxLocationTrigger {
    val triggeredTxnIds = mutableListOf<Long>()
    override fun onMissingCoordinates(txnId: Long) {
        triggeredTxnIds.add(txnId)
    }
}

class DataImporterTest {
    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var categoryDao: ImportFakeCategoryDao
    private lateinit var merchantDao: ImportFakeMerchantDao
    private lateinit var approxLocationTrigger: FakeApproxLocationTrigger
    private lateinit var importer: DataImporter

    @Before
    fun setUp() {
        transactionDao = FakeTransactionDao()
        categoryDao = ImportFakeCategoryDao()
        merchantDao = ImportFakeMerchantDao()
        approxLocationTrigger = FakeApproxLocationTrigger()
        importer = DataImporter(transactionDao, categoryDao, merchantDao, approxLocationTrigger)
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
    fun `restores raw coordinates directly when the export carried them, without triggering approx geocoding`() = runTest {
        importer.importFromJson(
            bundleJson(
                ExportedTransaction(
                    amountPaise = 45000L,
                    direction = "DEBIT",
                    status = "CONFIRMED",
                    payeeName = null,
                    vpa = null,
                    upiRef = null,
                    occurredAt = 1_000L,
                    categoryName = null,
                    merchantName = null,
                    placeName = "Connaught Place",
                    locality = "New Delhi",
                    note = null,
                    lat = 28.7041,
                    lng = 77.1025,
                    accuracyM = 12.5,
                    locationQuality = "GOOD",
                ),
            ),
        )

        val txn = transactionDao.transactions.value.single()
        assertEquals(28.7041, txn.lat)
        assertEquals(77.1025, txn.lng)
        assertEquals(12.5, txn.accuracyM)
        assertEquals(LocationQuality.GOOD, txn.locationQuality)
        assertEquals(emptyList<Long>(), approxLocationTrigger.triggeredTxnIds)
    }

    @Test
    fun `triggers approx geocoding when only place text survived, no coordinates`() = runTest {
        importer.importFromJson(
            bundleJson(
                ExportedTransaction(
                    amountPaise = 45000L,
                    direction = "DEBIT",
                    status = "CONFIRMED",
                    payeeName = null,
                    vpa = null,
                    upiRef = null,
                    occurredAt = 1_000L,
                    categoryName = null,
                    merchantName = null,
                    placeName = "Connaught Place",
                    locality = "New Delhi",
                    note = null,
                ),
            ),
        )

        val txn = transactionDao.transactions.value.single()
        assertNull(txn.lat)
        assertEquals(listOf(txn.id), approxLocationTrigger.triggeredTxnIds)
    }

    @Test
    fun `does not trigger approx geocoding when there is no place text either`() = runTest {
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

        assertEquals(emptyList<Long>(), approxLocationTrigger.triggeredTxnIds)
    }

    @Test
    fun `an empty bundle imports nothing`() = runTest {
        val result = importer.importFromJson(bundleJson())

        assertEquals(0, result.importedCount)
        assertEquals(0, result.skippedCount)
    }
}
