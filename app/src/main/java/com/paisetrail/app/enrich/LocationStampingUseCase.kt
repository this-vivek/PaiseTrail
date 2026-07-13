package com.paisetrail.app.enrich

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.paisetrail.app.data.db.LocationQuality
import com.paisetrail.app.data.db.PaymentContext
import com.paisetrail.app.data.db.TransactionDao
import com.paisetrail.app.data.db.TransactionEntity
import com.paisetrail.app.trips.TripAutoDetector
import com.paisetrail.app.util.awaitOrNull
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The actual location-fix logic (spec 4.1), shared by [LocationStamper] (first attempt, from a
 * foreground service) and [LocationRetryWorker] (the single MISSING retry). A fresh fix always
 * wins; a cached last-known fix is accepted but marked STALE past 10 minutes; nothing at all is
 * MISSING, which triggers exactly one retry.
 */
@Singleton
class LocationStampingUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient,
    private val transactionDao: TransactionDao,
    private val workManager: WorkManager,
    private val merchantResolver: MerchantResolver,
    private val tripAutoDetector: TripAutoDetector,
) {
    /** Returns true if a usable fix (GOOD or STALE) was stored, false if still MISSING. */
    suspend fun stamp(txnId: Long, isRetry: Boolean = false): Boolean {
        val txn = transactionDao.getById(txnId) ?: return false

        if (!hasLocationPermission()) {
            markMissing(txn, isRetry)
            return false
        }

        val fresh = getCurrentLocationWithTimeout()
        if (fresh != null) {
            applyLocation(txn, fresh, LocationQuality.GOOD)
            ReverseGeocodeWorker.enqueue(workManager, txnId)
            return true
        }

        val last = getLastLocationOrNull()
        if (last != null) {
            val ageMs = System.currentTimeMillis() - last.time
            val quality = if (ageMs > STALE_THRESHOLD_MS) LocationQuality.STALE else LocationQuality.GOOD
            applyLocation(txn, last, quality)
            ReverseGeocodeWorker.enqueue(workManager, txnId)
            return true
        }

        markMissing(txn, isRetry)
        return false
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocationWithTimeout(): Location? = withTimeoutOrNull(FIX_TIMEOUT_MS) {
        val cts = CancellationTokenSource()
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token).awaitOrNull()
        } finally {
            cts.cancel()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLastLocationOrNull(): Location? = fusedLocationClient.lastLocation.awaitOrNull()

    private suspend fun applyLocation(txn: TransactionEntity, location: Location, quality: LocationQuality) {
        val paymentContext = if (OnlineMerchantHeuristic.isOnlineMerchant(txn.payeeNameRaw, txn.vpa)) {
            PaymentContext.ONLINE
        } else {
            PaymentContext.IN_PERSON
        }
        transactionDao.update(
            txn.copy(
                lat = location.latitude,
                lng = location.longitude,
                accuracyM = location.accuracy.toDouble(),
                locationQuality = quality,
                paymentContext = paymentContext,
            ),
        )

        // Home-location learning only makes sense for in-person payments — an ONLINE payment's
        // coordinates are "where I was," not "where the merchant is" (spec 4.1/4.2).
        if (paymentContext == PaymentContext.IN_PERSON) {
            txn.merchantId?.let { merchantResolver.maybeUpdateHomeLocation(it) }
            tripAutoDetector.checkAfterLocationStamp(txn.id)
        }
    }

    private suspend fun markMissing(txn: TransactionEntity, isRetry: Boolean) {
        transactionDao.update(txn.copy(locationQuality = LocationQuality.MISSING))
        if (!isRetry) {
            LocationRetryWorker.enqueue(workManager, txn.id)
        }
    }

    companion object {
        private const val FIX_TIMEOUT_MS = 15_000L
        private const val STALE_THRESHOLD_MS = 10 * 60 * 1000L
    }
}
