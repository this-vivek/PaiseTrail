package com.paisetrail.app.capture.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.paisetrail.app.capture.RawEventIngestor
import com.paisetrail.app.data.db.RawEventSource
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Primary capture source (spec 3.1). Capture must never block on enrichment or crash the
 * listener — every notification is handled defensively and handed off to a coroutine so a slow
 * or failing parse can't stall the system's notification pipeline.
 */
@AndroidEntryPoint
class UpiNotificationListenerService : NotificationListenerService() {
    @Inject lateinit var registry: NotificationParserRegistry
    @Inject lateinit var ingestor: RawEventIngestor
    @Inject lateinit var redactedCounter: RedactedNotificationCounter

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            handle(sbn)
        } catch (e: Exception) {
            Log.e(TAG, "onNotificationPosted crashed for ${sbn.packageName}", e)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handle(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        if (!registry.isAllowed(packageName)) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()

        val fullText = listOfNotNull(title, text, bigText, subText).joinToString(" | ")
        // Redacted content on some OEMs (Android 15+) shows up as blank extras, or literally as
        // "Sensitive notification content hidden" — either way the SMS twin covers the
        // transaction, so there's nothing worth persisting here (spec 8 #8). Counted rather than
        // silently dropped so a spike in redaction rate is visible in the parser debug screen.
        if (NotificationRedaction.isRedacted(fullText)) {
            redactedCounter.increment()
            return
        }

        val parser = registry.parserFor(packageName)
        val parsed = try {
            parser?.parse(title, text, bigText, subText)
        } catch (e: Exception) {
            Log.e(TAG, "parser crashed for $packageName", e)
            null
        }

        serviceScope.launch {
            ingestor.ingest(
                source = RawEventSource.NOTIFICATION,
                packageOrSender = packageName,
                fullText = fullText,
                postedAt = sbn.postTime,
                parsed = parsed,
            )
        }
    }

    companion object {
        private const val TAG = "UpiNotifListener"
    }
}
