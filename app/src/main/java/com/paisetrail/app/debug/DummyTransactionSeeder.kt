package com.paisetrail.app.debug

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.paisetrail.app.capture.SelfTransferDetector
import com.paisetrail.app.data.db.CategoryDao
import com.paisetrail.app.data.db.LocationQuality
import com.paisetrail.app.data.db.PaymentContext
import com.paisetrail.app.data.db.TagSource
import com.paisetrail.app.data.db.TransactionDao
import com.paisetrail.app.data.db.TransactionEntity
import com.paisetrail.app.data.db.TripDao
import com.paisetrail.app.data.db.TxnDirection
import com.paisetrail.app.data.db.TxnStatus
import com.paisetrail.app.enrich.MerchantResolver
import com.paisetrail.app.enrich.OnlineMerchantHeuristic
import com.paisetrail.app.util.awaitOrNull
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/** A randomly generated but not-yet-inserted transaction — shown as a preview on the "add random
 * transaction" screen so a tap can re-roll it before committing (spec 7.6 debug tools). */
data class DummyDraft(
    val payeeName: String,
    val categoryName: String,
    val amountPaise: Long,
    val occurredAt: Long,
)

/** Debug-only test data generator (spec 7.6 debug tools) — lets the app be exercised (Dashboard,
 * Transactions, Map, Trips) without waiting on real UPI activity. Also backs manual/custom
 * transaction entry, since both paths need the same location-stamping / trip-tagging / self-
 * transfer handling real capture gets. */
@Singleton
class DummyTransactionSeeder @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val tripDao: TripDao,
    private val merchantResolver: MerchantResolver,
    private val fusedLocationClient: FusedLocationProviderClient,
    @ApplicationContext private val context: Context,
) {
    /** Spread across the last 24h rather than months so it always lands in "this month" on the
     * Dashboard and at the top of the Transactions list — the whole point of a dummy transaction
     * is to see it show up immediately. */
    fun randomDraft(): DummyDraft {
        val merchant = SAMPLE_MERCHANTS.random()
        return DummyDraft(
            payeeName = merchant.name,
            categoryName = merchant.categoryName,
            amountPaise = Random.nextLong(merchant.minPaise, merchant.maxPaise + 1),
            occurredAt = System.currentTimeMillis() - Random.nextLong(0L, RECENT_SPREAD_MS),
        )
    }

    suspend fun insertOne(): Long = insertDraft(randomDraft())

    suspend fun insertDraft(draft: DummyDraft): Long {
        val category = categoryDao.getByName(draft.categoryName)
        return insert(
            payeeName = draft.payeeName,
            categoryId = category?.id,
            amountPaise = draft.amountPaise,
            occurredAt = draft.occurredAt,
        )
    }

    /** Shared by the random-draft path and manual/custom entry — same location stamp, trip tag,
     * merchant resolution, and self-transfer handling real capture applies via
     * [com.paisetrail.app.capture.RawEventIngestor] / [com.paisetrail.app.enrich.TransactionEnrichmentCoordinator].
     * Without this, dummy/manual transactions never got a merchantId and could never show up in
     * the Dashboard's Top Merchants (that query only counts rows with a resolved merchant). */
    suspend fun insert(payeeName: String, categoryId: Long?, amountPaise: Long, occurredAt: Long): Long {
        val location = getLastLocationOrNull()
        val paymentContext = if (OnlineMerchantHeuristic.isOnlineMerchant(payeeName, null)) {
            PaymentContext.ONLINE
        } else {
            PaymentContext.IN_PERSON
        }
        val status = if (SelfTransferDetector.isSelfTransfer(payeeName)) TxnStatus.SELF_TRANSFER else TxnStatus.CONFIRMED
        // Not routed through maybeUpdateHomeLocation — every dummy transaction shares the same
        // current device location, and feeding that into a real merchant's learned home location
        // would bias it toward wherever this phone happens to be, not the merchant's real spot.
        val merchantId = merchantResolver.resolve(payeeName, vpa = null)

        return transactionDao.insert(
            TransactionEntity(
                amountPaise = amountPaise,
                direction = TxnDirection.DEBIT,
                status = status,
                payeeNameRaw = payeeName,
                occurredAt = occurredAt,
                categoryId = categoryId,
                merchantId = merchantId,
                // AUTO_HIGH, not AUTO_LOW — this is a definite pick, not a guess needing
                // confirmation, and dummy data cluttering the real Review Queue (and shifting the
                // Dashboard's "needs review" banner mid-tap) makes it look like navigation is broken.
                tagSource = if (categoryId != null) TagSource.AUTO_HIGH else TagSource.NONE,
                paymentContext = paymentContext,
                lat = location?.latitude,
                lng = location?.longitude,
                accuracyM = location?.accuracy?.toDouble(),
                locationQuality = if (location != null) LocationQuality.GOOD else null,
                // Trip mode (spec 7.4/5): a real payment during an active trip is auto-tagged to
                // it (see RawEventIngestor) — a dummy/manual transaction should behave the same way.
                tripId = tripDao.getActiveTrip()?.id,
            ),
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLastLocationOrNull(): android.location.Location? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        return fusedLocationClient.lastLocation.awaitOrNull()
    }

    private data class SampleMerchant(
        val name: String,
        val categoryName: String,
        val minPaise: Long,
        val maxPaise: Long,
    )

    companion object {
        private const val RECENT_SPREAD_MS = 24L * 60 * 60 * 1000

        private val SAMPLE_MERCHANTS = listOf(
            SampleMerchant("Sharma Tea Stall", "Food", 2_000L, 15_000L),
            SampleMerchant("Zomato", "Food", 15_000L, 60_000L),
            SampleMerchant("HPCL Petrol Pump", "Fuel", 30_000L, 200_000L),
            SampleMerchant("BigBasket", "Groceries", 20_000L, 150_000L),
            SampleMerchant("Ola", "Travel", 5_000L, 40_000L),
            SampleMerchant("Netflix", "Entertainment", 19_900L, 19_900L),
            SampleMerchant("Apollo Pharmacy", "Health", 10_000L, 80_000L),
            SampleMerchant("Amazon", "Shopping", 30_000L, 300_000L),
            SampleMerchant("Electricity Board", "Bills", 50_000L, 250_000L),
            SampleMerchant("OYO Rooms", "Stay", 100_000L, 400_000L),
        )
    }
}
