package com.paisetrail.app.enrich

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.paisetrail.app.data.db.LocationQuality
import com.paisetrail.app.data.db.TransactionDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Fills in coordinates for a transaction that has a place name/locality but no GPS fix, via
 * [ForwardGeocoder] — used for rows restored from a backup whose export predated
 * [com.paisetrail.app.export.ExportedTransaction] carrying lat/lng, since only the derived text
 * survived that round trip. */
@HiltWorker
class ForwardGeocodeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val transactionDao: TransactionDao,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val txnId = inputData.getLong(KEY_TXN_ID, -1L)
        if (txnId <= 0) return Result.failure()
        val txn = transactionDao.getById(txnId) ?: return Result.failure()
        if (txn.lat != null && txn.lng != null) return Result.success()

        val coords = ForwardGeocoder.resolve(applicationContext, txn.placeName, txn.locality)
            ?: return Result.failure()

        transactionDao.update(
            txn.copy(
                lat = coords.first,
                lng = coords.second,
                accuracyM = APPROX_ACCURACY_M,
                locationQuality = LocationQuality.APPROXIMATE,
            ),
        )
        return Result.success()
    }

    companion object {
        private const val KEY_TXN_ID = "txn_id"
        private const val APPROX_ACCURACY_M = 3000.0

        fun enqueue(workManager: WorkManager, txnId: Long) {
            val request = OneTimeWorkRequestBuilder<ForwardGeocodeWorker>()
                .setInputData(workDataOf(KEY_TXN_ID to txnId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            workManager.enqueue(request)
        }
    }
}
