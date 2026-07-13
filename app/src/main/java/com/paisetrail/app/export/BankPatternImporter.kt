package com.paisetrail.app.export

import com.paisetrail.app.data.db.BankSmsPatternDao
import com.paisetrail.app.data.db.BankSmsPatternEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

data class BankPatternImportResult(val importedCount: Int, val skippedCount: Int)

/** Restores a [BankPatternExportBundle] produced by [BankPatternExporter] — lets a pattern set
 * built up (and hand-tuned) on one device seed another, or a fresh install after the schema-wipe
 * migrations this app has had a few of. Dedup is by (bankId, regex), same key as the unique index
 * on [BankSmsPatternEntity] — a pattern already present (same bank, same regex text) is skipped
 * rather than duplicated, but a second, genuinely different regex for the same bank is kept. */
@Singleton
class BankPatternImporter @Inject constructor(
    private val bankSmsPatternDao: BankSmsPatternDao,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun importFromJson(jsonString: String): BankPatternImportResult {
        val bundle = json.decodeFromString(BankPatternExportBundle.serializer(), jsonString)
        val existingKeys = bankSmsPatternDao.getAll().map { it.bankId to it.regex }.toSet()

        var imported = 0
        var skipped = 0
        for (pattern in bundle.patterns) {
            if ((pattern.bankId to pattern.regex) in existingKeys) {
                skipped++
                continue
            }
            bankSmsPatternDao.insertAll(
                listOf(BankSmsPatternEntity(pattern.bankId, pattern.senderSuffix, pattern.regex, pattern.enabled)),
            )
            imported++
        }

        return BankPatternImportResult(imported, skipped)
    }
}
