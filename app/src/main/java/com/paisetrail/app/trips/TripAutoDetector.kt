package com.paisetrail.app.trips

import com.paisetrail.app.data.db.PaymentContext
import com.paisetrail.app.data.db.TransactionDao
import com.paisetrail.app.data.db.TripDao
import com.paisetrail.app.util.distanceMeters
import javax.inject.Inject
import javax.inject.Singleton

/** "Looks like you're on a trip — track it?" (spec 7.4): 3+ in-person payments over 80km from
 * home within 24h. Only prompts when no trip is already active — an active trip already covers
 * whatever's happening. */
@Singleton
class TripAutoDetector @Inject constructor(
    private val transactionDao: TransactionDao,
    private val tripDao: TripDao,
    private val homeLocationStore: HomeLocationStore,
    private val tripPromptNotifier: TripPromptNotifier,
) {
    suspend fun checkAfterLocationStamp(txnId: Long) {
        val home = homeLocationStore.get() ?: return
        if (tripDao.getActiveTrip() != null) return

        val txn = transactionDao.getById(txnId) ?: return
        if (txn.paymentContext != PaymentContext.IN_PERSON) return
        val lat = txn.lat ?: return
        val lng = txn.lng ?: return
        if (distanceMeters(lat, lng, home.lat, home.lng) < FAR_THRESHOLD_M) return

        val since = txn.occurredAt - WINDOW_MS
        val farCount = transactionDao.getRecentUntripped(since)
            .count { candidate ->
                val cLat = candidate.lat ?: return@count false
                val cLng = candidate.lng ?: return@count false
                distanceMeters(cLat, cLng, home.lat, home.lng) >= FAR_THRESHOLD_M
            }

        if (farCount >= MIN_TRIP_PAYMENTS) {
            tripPromptNotifier.promptTripDetected()
        }
    }

    companion object {
        private const val FAR_THRESHOLD_M = 80_000.0
        private const val MIN_TRIP_PAYMENTS = 3
        private const val WINDOW_MS = 24L * 60 * 60 * 1000
    }
}
