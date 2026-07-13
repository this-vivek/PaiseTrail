package com.paisetrail.app.export

import com.paisetrail.app.data.db.BankSmsPatternDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Singleton
class BankPatternExporter @Inject constructor(
    private val bankSmsPatternDao: BankSmsPatternDao,
) {
    private val json = Json { prettyPrint = true }

    suspend fun buildExportJson(): String {
        val patterns = bankSmsPatternDao.getAll().map {
            ExportedBankPattern(bankId = it.bankId, senderSuffix = it.senderSuffix, regex = it.regex, enabled = it.enabled)
        }
        val bundle = BankPatternExportBundle(
            exportedAt = System.currentTimeMillis(),
            patternCount = patterns.size,
            patterns = patterns,
        )
        return json.encodeToString(BankPatternExportBundle.serializer(), bundle)
    }
}
