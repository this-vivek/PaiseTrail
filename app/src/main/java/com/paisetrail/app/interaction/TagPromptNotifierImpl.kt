package com.paisetrail.app.interaction

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.paisetrail.app.data.db.CategoryDao
import com.paisetrail.app.data.db.TransactionDao
import com.paisetrail.app.data.db.TransactionEntity
import com.paisetrail.app.data.db.TripDao
import com.paisetrail.app.enrich.CategoryGuesser
import com.paisetrail.app.enrich.MerchantResolver
import com.paisetrail.app.notif.NotificationChannels
import com.paisetrail.app.util.formatRupees
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Fires immediately on transaction creation rather than waiting on the location fix (spec 5
 * calls this an "instant popup") — place info is a nice-to-have the row can pick up later once
 * reverse geocoding finishes, not something worth delaying the popup for.
 *
 * Prefers the on-screen overlay (spec 5's optional "forced popup" mode) over the notification
 * whenever the user has granted SYSTEM_ALERT_WINDOW — a notification depends on the OS choosing
 * to actually surface it as heads-up, which OEMs that aggressively background-kill this app make
 * unreliable; an overlay window is drawn directly on screen regardless. */
@Singleton
class TagPromptNotifierImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val overlay: TagPromptOverlay,
    private val tripDao: TripDao,
    private val merchantResolver: MerchantResolver,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
) : TagPromptNotifier {
    override suspend fun postTagPrompt(txn: TransactionEntity) {
        // Trip mode (spec 7.4/5): the tag popup names the active trip so tagging a payment while
        // travelling reads as "this is going into the Ladakh trip," not just "tag this payment."
        val tripName = txn.tripId?.let { tripDao.getById(it)?.name }
        val predictions = computePredictions(txn)

        if (overlay.canShow()) {
            overlay.show(txn, tripName, predictions)
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val title = buildString {
            append(formatRupees(txn.amountPaise))
            txn.payeeNameRaw?.let { append(" → $it") }
        }
        val text = if (tripName != null) "Tag this payment · $tripName" else "Tag this payment"

        val builder = NotificationCompat.Builder(context, NotificationChannels.TAG_PROMPT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setTimeoutAfter(AUTO_DISMISS_MS)

        // Android caps notification actions at 3 total — 2 categories plus Delete, so a
        // false-positive capture (a promo that slipped through, a duplicate) can be cleared right
        // from the popup without ever opening the app.
        predictions.take(2).forEach { categoryName ->
            builder.addAction(0, categoryName, tagActionPendingIntent(txn.id, categoryName))
        }
        builder.addAction(0, "Delete", deleteActionPendingIntent(txn.id))

        NotificationManagerCompat.from(context).notify(notificationIdFor(txn.id), builder.build())
    }

    /** Merchant-learned category (spec 4.2's learning loop) leads when this merchant has one;
     * this user's own most-tagged categories overall fill any remaining slots instead of a fixed,
     * merchant-irrelevant default list (TODO: "dynamic to existing taggings"). */
    private suspend fun computePredictions(txn: TransactionEntity): List<String> {
        val learnedCategoryName = txn.merchantId
            ?.let { merchantResolver.getDefaultCategoryId(it) }
            ?.let { categoryDao.getById(it)?.name }
        val fallbackCategories = transactionDao.getCategoryUsageFrequency()
            .mapNotNull { categoryDao.getById(it.categoryId)?.name }
        return CategoryGuesser.topPredictions(
            txn.payeeNameRaw,
            txn.vpa,
            learnedCategoryName = learnedCategoryName,
            fallbackCategories = fallbackCategories,
        )
    }

    private fun tagActionPendingIntent(txnId: Long, categoryName: String): PendingIntent {
        val intent = Intent(context, TagActionReceiver::class.java).apply {
            action = TagActionReceiver.ACTION_TAG
            putExtra(TagActionReceiver.EXTRA_TXN_ID, txnId)
            putExtra(TagActionReceiver.EXTRA_CATEGORY_NAME, categoryName)
            putExtra(TagActionReceiver.EXTRA_NOTIFICATION_ID, notificationIdFor(txnId))
        }
        return PendingIntent.getBroadcast(
            context,
            // Request code must be unique per (txnId, categoryName) pair or the three action
            // PendingIntents for the same transaction collapse into one.
            "$txnId$categoryName".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun deleteActionPendingIntent(txnId: Long): PendingIntent {
        val intent = Intent(context, TagActionReceiver::class.java).apply {
            action = TagActionReceiver.ACTION_DELETE
            putExtra(TagActionReceiver.EXTRA_TXN_ID, txnId)
            putExtra(TagActionReceiver.EXTRA_NOTIFICATION_ID, notificationIdFor(txnId))
        }
        return PendingIntent.getBroadcast(
            context,
            "$txnId-delete".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val AUTO_DISMISS_MS = 10 * 60 * 1000L

        fun notificationIdFor(txnId: Long): Int = txnId.toInt()
    }
}
