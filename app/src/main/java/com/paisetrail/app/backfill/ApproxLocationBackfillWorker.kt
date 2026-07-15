package com.paisetrail.app.backfill

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.paisetrail.app.data.db.TransactionDao
import com.paisetrail.app.enrich.ForwardGeocodeWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** One-shot sweep for transactions that already have a place name/locality but no coordinates —
 * chiefly backups imported before [com.paisetrail.app.export.ExportedTransaction] carried
 * lat/lng. Fans each row out to its own [ForwardGeocodeWorker] rather than geocoding inline, so
 * one bad lookup can't fail the whole sweep and WorkManager's retry/network-constraint handling
 * applies per row. */
@HiltWorker
class ApproxLocationBackfillWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val transactionDao: TransactionDao,
    private val workManager: WorkManager,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ids = transactionDao.getIdsMissingCoordinatesWithPlace()
        ids.forEach { ForwardGeocodeWorker.enqueue(workManager, it) }
        return Result.success(workDataOf(KEY_QUEUED_COUNT to ids.size))
    }

    companion object {
        private const val WORK_NAME = "approx_location_backfill"
        const val KEY_QUEUED_COUNT = "queued_count"

        /** KEEP, not REPLACE — same one-shot-catch-up reasoning as [SmsBackfillWorker]. */
        fun enqueue(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<ApproxLocationBackfillWorker>().build()
            workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        fun workName(): String = WORK_NAME
    }
}
