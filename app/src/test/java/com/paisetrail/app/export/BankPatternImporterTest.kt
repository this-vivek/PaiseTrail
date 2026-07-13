package com.paisetrail.app.export

import com.paisetrail.app.data.db.BankSmsPatternDao
import com.paisetrail.app.data.db.BankSmsPatternEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeBankSmsPatternDao : BankSmsPatternDao {
    val patterns = MutableStateFlow<List<BankSmsPatternEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insertAll(patterns: List<BankSmsPatternEntity>) {
        for (pattern in patterns) {
            this.patterns.value = this.patterns.value + pattern.copy(id = nextId++)
        }
    }

    override suspend fun upsert(pattern: BankSmsPatternEntity) = Unit
    override suspend fun delete(pattern: BankSmsPatternEntity) = Unit
    override fun observeAll() = patterns
    override suspend fun getAll(): List<BankSmsPatternEntity> = patterns.value
    override suspend fun getEnabled(): List<BankSmsPatternEntity> = patterns.value.filter { it.enabled }
    override suspend fun count(): Int = patterns.value.size
}

class BankPatternImporterTest {
    private val dao = FakeBankSmsPatternDao()
    private val importer = BankPatternImporter(dao)

    private val exportJson = """
        {
          "exportedAt": 0,
          "patternCount": 2,
          "patterns": [
            { "bankId": "HDFC", "senderSuffix": "HDFCBK", "regex": "Sent Rs.(?<amount>[0-9.]+)", "enabled": true },
            { "bankId": "ICICI", "senderSuffix": "ICICIB", "regex": "debited with Rs.(?<amount>[0-9.]+)", "enabled": false }
          ]
        }
    """.trimIndent()

    @Test
    fun `imports patterns not already present`() = runTest {
        val result = importer.importFromJson(exportJson)

        assertEquals(2, result.importedCount)
        assertEquals(0, result.skippedCount)
        assertEquals(2, dao.getAll().size)
        assertEquals(false, dao.getAll().first { it.bankId == "ICICI" }.enabled)
    }

    @Test
    fun `skips a pattern already present with the same bankId and regex`() = runTest {
        dao.insertAll(listOf(BankSmsPatternEntity("HDFC", "HDFCBK", "Sent Rs.(?<amount>[0-9.]+)", true)))

        val result = importer.importFromJson(exportJson)

        assertEquals(1, result.importedCount)
        assertEquals(1, result.skippedCount)
        assertEquals(2, dao.getAll().size)
    }
}
