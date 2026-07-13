package com.paisetrail.app.enrich.ai

import com.paisetrail.app.data.db.CategoryEntity
import com.paisetrail.app.data.db.TagSource
import com.paisetrail.app.data.db.TransactionEntity
import com.paisetrail.app.data.db.TxnDirection
import com.paisetrail.app.data.db.TxnStatus
import com.paisetrail.app.testutil.FakeCategoryDao
import com.paisetrail.app.testutil.FakeTransactionDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class LocalCategorySuggesterTest {
    private val transactionDao = FakeTransactionDao()
    private val categoryDao = FakeCategoryDao()
    private val suggester = LocalCategorySuggester(transactionDao, categoryDao)

    @Before
    fun setUp() = runTest {
        categoryDao.insertAll(
            listOf(
                CategoryEntity(name = "Food", colorHex = "#FF0000"),
                CategoryEntity(name = "Travel", colorHex = "#00FF00"),
            ),
        )
    }

    private suspend fun taggedTxn(payeeName: String, categoryName: String) {
        val category = categoryDao.getByName(categoryName)!!
        transactionDao.insert(
            TransactionEntity(
                amountPaise = 10000,
                direction = TxnDirection.DEBIT,
                status = TxnStatus.CONFIRMED,
                payeeNameRaw = payeeName,
                occurredAt = 1_000L,
                categoryId = category.id,
                tagSource = TagSource.USER,
            ),
        )
    }

    @Test
    fun `matches a new transaction to the category of a similarly-named tagged one`() = runTest {
        taggedTxn("Cafe Coffee Day", "Food")
        taggedTxn("IRCTC Booking", "Travel")

        val suggestion = suggester.suggestCategory("Cafe Coffee Day Koramangala", null, listOf("Food", "Travel"))

        assertEquals("Food", suggestion)
    }

    @Test
    fun `no similar history returns no opinion`() = runTest {
        taggedTxn("Cafe Coffee Day", "Food")

        val suggestion = suggester.suggestCategory("Totally Unrelated Merchant Xyz", null, listOf("Food", "Travel"))

        assertNull(suggestion)
    }

    @Test
    fun `blank payee and vpa returns no opinion`() = runTest {
        val suggestion = suggester.suggestCategory(null, null, listOf("Food"))

        assertNull(suggestion)
    }

    @Test
    fun `is always ready`() = runTest {
        assertEquals(true, suggester.ensureReady())
    }
}
