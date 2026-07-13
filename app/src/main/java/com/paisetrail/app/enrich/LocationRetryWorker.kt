package com.paisetrail.app.enrich

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** The single MISSING retry from spec 4.1 point 3 — payments made at home usually resolve via
 * last-known location once the device has had a few minutes to get a fix. */
@HiltWorker
class LocationRetryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val locationStampingUseCase: LocationStampingUseCase,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val txnId = inputData.getLong(KEY_TXN_ID, -1L)
        if (txnId <= 0) return Result.failure()
        locationStampingUseCase.stamp(txnId, isRetry = true)
        return Result.success()
    }

    companion object {
        private const val KEY_TXN_ID = "txn_id"
        private const val RETRY_DELAY_MINUTES = 5L

        fun enqueue(workManager: WorkManager, txnId: Long) {
            val request = OneTimeWorkRequestBuilder<LocationRetryWorker>()
                .setInputData(workDataOf(KEY_TXN_ID to txnId))
                .setInitialDelay(RETRY_DELAY_MINUTES, TimeUnit.MINUTES)
                .build()
            workManager.enqueue(request)
        }
    }
}
