package com.paisetrail.app.notif

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService

/** Two channels for two very different jobs: the location fix is ambient/silent plumbing, the
 * tag prompt is the one thing in this app that should interrupt the user (spec 5). */
object NotificationChannels {
    const val LOCATION_STAMPER = "location_stamper"
    const val TAG_PROMPT = "tag_prompt"

    fun ensureCreated(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                LOCATION_STAMPER,
                "Location capture",
                NotificationManager.IMPORTANCE_MIN,
            ).apply { setShowBadge(false) },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                TAG_PROMPT,
                "Tag a payment",
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }
}
