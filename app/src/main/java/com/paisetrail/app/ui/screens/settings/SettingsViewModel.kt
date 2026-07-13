package com.paisetrail.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.paisetrail.app.backfill.SmsBackfillWorker
import com.paisetrail.app.data.db.TransactionDao
import com.paisetrail.app.enrich.AutoTagResult
import com.paisetrail.app.enrich.CategoryAutoTagger
import com.paisetrail.app.enrich.ai.AiCategoryTagger
import com.paisetrail.app.enrich.ai.AiTagResult
import com.paisetrail.app.export.BankPatternExporter
import com.paisetrail.app.export.BankPatternImportResult
import com.paisetrail.app.export.BankPatternImporter
import com.paisetrail.app.export.DataExporter
import com.paisetrail.app.export.DataImporter
import com.paisetrail.app.export.ImportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val workManager: WorkManager,
    private val dataExporter: DataExporter,
    private val dataImporter: DataImporter,
    private val transactionDao: TransactionDao,
    private val categoryAutoTagger: CategoryAutoTagger,
    private val bankPatternExporter: BankPatternExporter,
    private val bankPatternImporter: BankPatternImporter,
    private val aiCategoryTagger: AiCategoryTagger,
) : ViewModel() {
    val backfillState: StateFlow<WorkInfo.State?> = workManager
        .getWorkInfosForUniqueWorkFlow(SmsBackfillWorker.workName())
        .map { infos -> infos.firstOrNull()?.state }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun startBackfill() {
        SmsBackfillWorker.enqueue(workManager)
    }

    suspend fun buildExportJson(): String = dataExporter.buildExportJson()

    suspend fun importFromJson(jsonString: String): ImportResult = dataImporter.importFromJson(jsonString)

    suspend fun autoTagFromJson(jsonString: String): AutoTagResult = categoryAutoTagger.learnAndApplyFromJson(jsonString)

    suspend fun buildBankPatternExportJson(): String = bankPatternExporter.buildExportJson()

    suspend fun importBankPatternsFromJson(jsonString: String): BankPatternImportResult =
        bankPatternImporter.importFromJson(jsonString)

    suspend fun autoTagWithAi(): AiTagResult = aiCategoryTagger.autoTagAll()

    suspend fun clearAllTransactions() {
        transactionDao.deleteAll()
    }
}
