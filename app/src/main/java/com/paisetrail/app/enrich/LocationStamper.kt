package com.paisetrail.app.enrich

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.paisetrail.app.notif.NotificationChannels
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Short-lived foreground service (spec 4.1) — the officially sanctioned way for a background
 * capture path (notification listener / SMS receiver, neither of which is a foreground UI) to
 * get a location fix reliably on modern Android. Stops itself as soon as the fix attempt (fresh
 * or fallback) finishes.
 */
@AndroidEntryPoint
class LocationStamper : Service() {
    @Inject lateinit var locationStampingUseCase: LocationStampingUseCase

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val txnId = intent?.getLongExtra(EXTRA_TXN_ID, -1L) ?: -1L
        if (txnId <= 0) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildAmbientNotification())

        serviceScope.launch {
            try {
                locationStampingUseCase.stamp(txnId)
            } finally {
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    private fun buildAmbientNotification() =
        NotificationCompat.Builder(this, NotificationChannels.LOCATION_STAMPER)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Locating payment")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_TXN_ID = "txn_id"
        private const val NOTIFICATION_ID = 4201

        /** Never call this without [android.Manifest.permission.ACCESS_FINE_LOCATION] already
         * granted — the manifest declares `foregroundServiceType="location"`, and Android throws
         * a SecurityException from `startForeground` for that type without the permission. */
        fun enqueue(context: Context, txnId: Long) {
            val intent = Intent(context, LocationStamper::class.java).putExtra(EXTRA_TXN_ID, txnId)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
