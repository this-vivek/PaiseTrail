package com.paisetrail.app.enrich

import android.content.Context
import android.location.Geocoder
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.paisetrail.app.data.db.TransactionDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Locale

/** Lazy, batched-by-WorkManager reverse geocode (spec 4.1 point 4) using Android's free,
 * offline-capable-on-many-devices Geocoder — never blocks capture or the location fix itself. */
@HiltWorker
class ReverseGeocodeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val transactionDao: TransactionDao,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val txnId = inputData.getLong(KEY_TXN_ID, -1L)
        if (txnId <= 0) return Result.failure()
        val txn = transactionDao.getById(txnId) ?: return Result.failure()
        val lat = txn.lat ?: return Result.failure()
        val lng = txn.lng ?: return Result.failure()

        return try {
            @Suppress("DEPRECATION")
            val address = Geocoder(applicationContext, Locale.getDefault())
                .getFromLocation(lat, lng, 1)
                ?.firstOrNull()
            if (address != null) {
                transactionDao.update(
                    txn.copy(
                        placeName = address.featureName ?: address.thoroughfare,
                        locality = address.locality ?: address.subAdminArea,
                    ),
                )
            }
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    companion object {
        private const val KEY_TXN_ID = "txn_id"

        fun enqueue(workManager: WorkManager, txnId: Long) {
            val request = OneTimeWorkRequestBuilder<ReverseGeocodeWorker>()
                .setInputData(workDataOf(KEY_TXN_ID to txnId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            workManager.enqueue(request)
        }
    }
}
