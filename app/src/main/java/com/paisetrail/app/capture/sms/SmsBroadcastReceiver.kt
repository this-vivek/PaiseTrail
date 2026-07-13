package com.paisetrail.app.capture.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.paisetrail.app.capture.RawEventIngestor
import com.paisetrail.app.data.db.RawEventSource
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Fallback + backfill source (spec 3.2). Bank transactional SMS is the most reliable signal —
 * RBI mandates it for UPI debits — so this receiver is what makes capture resilient to a
 * swiped-away or OEM-killed notification listener. */
@AndroidEntryPoint
class SmsBroadcastReceiver : BroadcastReceiver() {
    @Inject lateinit var patternRegistry: BankSmsPatternRegistry
    @Inject lateinit var ingestor: RawEventIngestor

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (e: Exception) {
            Log.e(TAG, "failed to extract SMS messages from intent", e)
            return
        }
        if (messages.isNullOrEmpty()) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                messages.forEach { handleMessage(it) }
            } catch (e: Exception) {
                Log.e(TAG, "SMS handling crashed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleMessage(message: SmsMessage) {
        val sender = message.originatingAddress ?: return
        val body = message.messageBody ?: return

        val match = try {
            patternRegistry.match(sender, body)
        } catch (e: Exception) {
            Log.e(TAG, "pattern match crashed for sender $sender", e)
            null
        }
        // SMS from a sender we don't recognize as any seeded bank (OTPs, promos, personal texts)
        // never enters the pipeline. A recognized bank sender whose body didn't match still gets
        // a raw_event with parsedOk=false — otherwise a regex drifting out of date is invisible,
        // defeating the whole point of the parser debug screen (spec 7.6 / 11).
        if (match == null || !match.senderRecognized) return

        ingestor.ingest(
            source = RawEventSource.SMS,
            packageOrSender = sender,
            fullText = body,
            postedAt = message.timestampMillis,
            parsed = match.parsed,
        )
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
