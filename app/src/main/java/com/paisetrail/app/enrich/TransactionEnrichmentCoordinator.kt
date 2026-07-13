package com.paisetrail.app.enrich

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.paisetrail.app.data.db.CategoryDao
import com.paisetrail.app.data.db.LocationQuality
import com.paisetrail.app.data.db.TagSource
import com.paisetrail.app.data.db.TransactionDao
import com.paisetrail.app.data.db.TxnDirection
import com.paisetrail.app.data.db.TxnStatus
import com.paisetrail.app.interaction.TagPromptNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fans a new transaction out to every enrichment step (spec architecture diagram): category
 * guess, location fix, tag popup. Each step is independent and wrapped so one failing never
 * blocks the others — enrichment must never take capture down with it (spec 3 key principle).
 */
@Singleton
class TransactionEnrichmentCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val merchantResolver: MerchantResolver,
    private val refundMatcher: RefundMatcher,
    private val tagPromptNotifier: TagPromptNotifier,
) : TransactionEnrichmentTrigger {
    override suspend fun onNewTransaction(txnId: Long) {
        val txn = transactionDao.getById(txnId) ?: return

        // A credit isn't something the user tags or gets a location fix for — it's either a
        // refund of an existing debit or just noise; either way the DEBIT enrichment flow below
        // doesn't apply to it.
        if (txn.direction == TxnDirection.CREDIT) {
            try {
                refundMatcher.tryMatchRefund(txnId)
            } catch (e: Exception) {
                Log.e(TAG, "refund match failed for txn $txnId", e)
            }
            return
        }

        // A self-transfer (money moving between the user's own accounts) doesn't need a
        // category, a location fix, or a tag popup — it's already excluded from spend by status.
        if (txn.status == TxnStatus.SELF_TRANSFER) return

        var merchantId: Long? = null
        try {
            merchantId = merchantResolver.resolve(txn.payeeNameRaw, txn.vpa)
            transactionDao.update(txn.copy(merchantId = merchantId))
        } catch (e: Exception) {
            Log.e(TAG, "merchant resolution failed for txn $txnId", e)
        }

        try {
            applyCategoryGuess(txn.id, merchantId, txn.payeeNameRaw, txn.vpa)
        } catch (e: Exception) {
            Log.e(TAG, "category guess failed for txn $txnId", e)
        }

        try {
            // The manifest declares LocationStamper as foregroundServiceType="location" — Android
            // throws a SecurityException from startForeground for that type without the
            // permission already granted, so this has to be checked before the service starts,
            // not inside it.
            if (hasLocationPermission()) {
                LocationStamper.enqueue(context, txnId)
            } else {
                transactionDao.getById(txnId)?.let {
                    transactionDao.update(it.copy(locationQuality = LocationQuality.MISSING))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "failed to start LocationStamper for txn $txnId", e)
        }

        try {
            tagPromptNotifier.postTagPrompt(txn)
        } catch (e: Exception) {
            Log.e(TAG, "failed to post tag prompt for txn $txnId", e)
        }
    }

    /** Rule cascade (spec 4.3): a merchant-learned category (step 1) beats the keyword cascade
     * (step 2) beats Uncategorized (NONE) — the same rows CategoryGuesser alone produced in
     * Phase 2, now with the merchant-learned step actually wired in. */
    private suspend fun applyCategoryGuess(txnId: Long, merchantId: Long?, payeeName: String?, vpa: String?) {
        val txn = transactionDao.getById(txnId) ?: return

        val learnedCategoryId = merchantId?.let { merchantResolver.getDefaultCategoryId(it) }
        if (learnedCategoryId != null) {
            transactionDao.update(txn.copy(categoryId = learnedCategoryId, tagSource = TagSource.AUTO_HIGH))
            return
        }

        val guessedName = CategoryGuesser.guess(payeeName, vpa)
        val category = categoryDao.getByName(guessedName)
        val tagSource = if (guessedName == CategoryGuesser.UNCATEGORIZED) TagSource.NONE else TagSource.AUTO_LOW
        transactionDao.update(txn.copy(categoryId = category?.id, tagSource = tagSource))
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "EnrichmentCoordinator"
    }
}
