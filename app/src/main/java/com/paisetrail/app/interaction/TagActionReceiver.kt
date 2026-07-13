package com.paisetrail.app.interaction

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.paisetrail.app.enrich.TagConfirmationUseCase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Handles a tap on one of the tag popup's inline category buttons — tags the transaction and
 * dismisses the notification without ever opening the app (spec 5). */
@AndroidEntryPoint
class TagActionReceiver : BroadcastReceiver() {
    @Inject lateinit var tagConfirmationUseCase: TagConfirmationUseCase

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TAG) return

        val txnId = intent.getLongExtra(EXTRA_TXN_ID, -1L)
        val categoryName = intent.getStringExtra(EXTRA_CATEGORY_NAME)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (txnId <= 0 || categoryName == null) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                tagConfirmationUseCase.confirmCategory(txnId, categoryName)
            } catch (e: Exception) {
                Log.e(TAG, "failed to apply tag for txn $txnId", e)
            } finally {
                if (notificationId != -1) {
                    NotificationManagerCompat.from(context).cancel(notificationId)
                }
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "TagActionReceiver"
        const val ACTION_TAG = "com.paisetrail.app.action.TAG_TRANSACTION"
        const val EXTRA_TXN_ID = "txn_id"
        const val EXTRA_CATEGORY_NAME = "category_name"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
