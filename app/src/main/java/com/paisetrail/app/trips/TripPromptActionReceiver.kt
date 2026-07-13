package com.paisetrail.app.trips

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Handles "Start trip" on the auto-detect prompt (spec 7.4) without opening the app, same
 * pattern as [com.paisetrail.app.interaction.TagActionReceiver]. */
@AndroidEntryPoint
class TripPromptActionReceiver : BroadcastReceiver() {
    @Inject lateinit var tripManager: TripManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_START_TRIP) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                tripManager.startTrip("Trip", System.currentTimeMillis(), autoDetected = true)
            } catch (e: Exception) {
                Log.e(TAG, "failed to auto-start trip", e)
            } finally {
                NotificationManagerCompat.from(context).cancel(TripPromptNotifier.TRIP_PROMPT_NOTIFICATION_ID)
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "TripPromptActionReceiver"
        const val ACTION_START_TRIP = "com.paisetrail.app.action.START_TRIP"
    }
}
