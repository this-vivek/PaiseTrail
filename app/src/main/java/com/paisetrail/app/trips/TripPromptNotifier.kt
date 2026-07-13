package com.paisetrail.app.trips

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.paisetrail.app.notif.NotificationChannels
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The "Looks like you're on a trip — track it?" prompt (spec 7.4) — a plain notification, not
 * an overlay; unlike the tag popup this is rare and doesn't need the forced-on-screen treatment. */
@Singleton
class TripPromptNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun promptTripDetected() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val intent = Intent(context, TripPromptActionReceiver::class.java).apply {
            action = TripPromptActionReceiver.ACTION_START_TRIP
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            TRIP_PROMPT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.TAG_PROMPT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Looks like you're on a trip")
            .setContentText("3+ payments far from home in the last 24h. Track it?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, "Start trip", pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(TRIP_PROMPT_NOTIFICATION_ID, notification)
    }

    companion object {
        const val TRIP_PROMPT_NOTIFICATION_ID = 9001
        private const val TRIP_PROMPT_REQUEST_CODE = 9001
    }
}
