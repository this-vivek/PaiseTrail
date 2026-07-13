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
import org.junit.Before
import org.junit.Test

private class FakeCategorySuggester(
    private val ready: Boolean,
    private val answer: String?,
) : CategorySuggester {
    override suspend fun ensureReady(): Boolean = ready
    override suspend fun suggestCategory(payeeName: String?, vpa: String?, categoryNames: List<String>): String? = answer
    override fun statusDescription(): String = "fake status"
}

class AiCategoryTaggerTest {
    private val transactionDao = FakeTransactionDao()
    private val categoryDao = FakeCategoryDao()
    private val localSuggester = LocalCategorySuggester(transactionDao, categoryDao)

    @Before
    fun setUp() = runTest {
        categoryDao.insertAll(listOf(CategoryEntity(name = "Food", colorHex = "#FF0000")))
    }

    private suspend fun untaggedTxn(payeeName: String): Long =
        transactionDao.insert(
            TransactionEntity(
                amountPaise = 10000,
                direction = TxnDirection.DEBIT,
                status = TxnStatus.CONFIRMED,
                payeeNameRaw = payeeName,
                occurredAt = 1_000L,
                tagSource = TagSource.NONE,
            ),
        )

    @Test
    fun `tags via the AI suggester when it is ready and has an answer`() = runTest {
        val txnId = untaggedTxn("Cafe Coffee Day")
        val tagger = AiCategoryTagger(transactionDao, categoryDao, FakeCategorySuggester(ready = true, answer = "Food"), localSuggester)

        val result = tagger.autoTagAll()

        assertEquals(1, result.taggedByAi)
        assertEquals(0, result.taggedByLocal)
        assertEquals("Food", categoryDao.getById(transactionDao.getById(txnId)!!.categoryId!!)?.name)
        assertEquals(TagSource.AUTO_AI, transactionDao.getById(txnId)!!.tagSource)
    }

    @Test
    fun `falls back to the local suggester when AI is not ready`() = runTest {
        untaggedTxn("Cafe Coffee Day")
        transactionDao.insert(
            TransactionEntity(
                amountPaise = 5000,
                direction = TxnDirection.DEBIT,
                status = TxnStatus.CONFIRMED,
                payeeNameRaw = "Cafe Coffee Day Whitefield",
                occurredAt = 500L,
                categoryId = categoryDao.getByName("Food")!!.id,
                tagSource = TagSource.USER,
            ),
        )
        val tagger = AiCategoryTagger(transactionDao, categoryDao, FakeCategorySuggester(ready = false, answer = "Food"), localSuggester)

        val result = tagger.autoTagAll()

        assertEquals(0, result.taggedByAi)
        assertEquals(1, result.taggedByLocal)
    }

    @Test
    fun `falls back to the local suggester when AI is ready but gives no usable answer`() = runTest {
        untaggedTxn("Cafe Coffee Day")
        transactionDao.insert(
            TransactionEntity(
                amountPaise = 5000,
                direction = TxnDirection.DEBIT,
                status = TxnStatus.CONFIRMED,
                payeeNameRaw = "Cafe Coffee Day Whitefield",
                occurredAt = 500L,
                categoryId = categoryDao.getByName("Food")!!.id,
                tagSource = TagSource.USER,
            ),
        )
        val tagger = AiCategoryTagger(transactionDao, categoryDao, FakeCategorySuggester(ready = true, answer = null), localSuggester)

        val result = tagger.autoTagAll()

        assertEquals(0, result.taggedByAi)
        assertEquals(1, result.taggedByLocal)
    }

    @Test
    fun `neither suggester having an opinion leaves the transaction untouched`() = runTest {
        val txnId = untaggedTxn("Totally Unknown Merchant")
        val tagger = AiCategoryTagger(transactionDao, categoryDao, FakeCategorySuggester(ready = true, answer = null), localSuggester)

        val result = tagger.autoTagAll()

        assertEquals(0, result.taggedByAi)
        assertEquals(0, result.taggedByLocal)
        assertEquals(TagSource.NONE, transactionDao.getById(txnId)!!.tagSource)
    }
}
