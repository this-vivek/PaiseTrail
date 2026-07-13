package com.paisetrail.app.enrich

import com.paisetrail.app.data.db.CategoryEntity
import com.paisetrail.app.data.db.TagSource
import com.paisetrail.app.data.db.TransactionEntity
import com.paisetrail.app.data.db.TxnDirection
import com.paisetrail.app.data.db.TxnStatus
import com.paisetrail.app.testutil.FakeCategoryDao
import com.paisetrail.app.testutil.FakeMerchantDao
import com.paisetrail.app.testutil.FakeTransactionDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CategoryAutoTaggerTest {
    private val transactionDao = FakeTransactionDao()
    private val categoryDao = FakeCategoryDao()
    private val merchantDao = FakeMerchantDao()
    private val merchantResolver = MerchantResolver(merchantDao)
    private val tagger = CategoryAutoTagger(transactionDao, categoryDao, merchantResolver)

    @Before
    fun setUp() = runTest {
        categoryDao.insertAll(listOf(CategoryEntity(name = "Food", colorHex = "#FF0000")))
    }

    private val exportJson = """
        {
          "exportedAt": 0,
          "transactionCount": 1,
          "transactions": [
            { "amountPaise": 10000, "direction": "DEBIT", "status": "CONFIRMED",
              "payeeName": "Cafe Coffee Day", "vpa": "ccd@okhdfcbank", "upiRef": "111",
              "occurredAt": 0, "categoryName": "Food", "merchantName": "Cafe Coffee Day",
              "placeName": null, "locality": null, "note": null }
          ]
        }
    """.trimIndent()

    @Test
    fun `learns a merchant category and applies it to a matching uncategorized transaction`() = runTest {
        transactionDao.insert(
            TransactionEntity(
                amountPaise = 15000,
                direction = TxnDirection.DEBIT,
                status = TxnStatus.CONFIRMED,
                payeeNameRaw = "Cafe Coffee Day",
                vpa = "ccd@okhdfcbank",
                occurredAt = 1_000L,
                tagSource = TagSource.NONE,
            ),
        )

        val result = tagger.learnAndApplyFromJson(exportJson)

        assertEquals(1, result.rulesLearned)
        assertEquals(1, result.candidateCount)
        assertEquals(1, result.taggedCount)

        val tagged = transactionDao.getById(1L)!!
        assertEquals(categoryDao.getByName("Food")!!.id, tagged.categoryId)
        assertEquals(TagSource.AUTO_HIGH, tagged.tagSource)
    }

    @Test
    fun `a transaction with no matching merchant is left alone`() = runTest {
        transactionDao.insert(
            TransactionEntity(
                amountPaise = 5000,
                direction = TxnDirection.DEBIT,
                status = TxnStatus.CONFIRMED,
                payeeNameRaw = "Some Random Shop",
                occurredAt = 1_000L,
                tagSource = TagSource.NONE,
            ),
        )

        val result = tagger.learnAndApplyFromJson(exportJson)

        assertEquals(0, result.taggedCount)
        val untouched = transactionDao.getById(1L)!!
        assertEquals(null, untouched.categoryId)
        assertEquals(TagSource.NONE, untouched.tagSource)
    }

    @Test
    fun `a category name not present locally is created`() = runTest {
        val json = exportJson.replace("Food", "Snacks")

        tagger.learnAndApplyFromJson(json)

        assertEquals("Snacks", categoryDao.getByName("Snacks")?.name)
    }
}
