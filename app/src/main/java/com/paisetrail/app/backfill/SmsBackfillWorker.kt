package com.paisetrail.app.backfill

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.paisetrail.app.capture.RawEventIngestor
import com.paisetrail.app.capture.sms.BankSmsPatternRegistry
import com.paisetrail.app.data.db.RawEventSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Historical backfill (spec 3.4): every SMS from the last [BACKFILL_WINDOW_DAYS] days goes
 * through the same [BankSmsPatternRegistry] pipeline live capture uses, reconstructing months of
 * transaction history in one pass — just without location (no fix exists for the past, and
 * firing one tag popup per historical row would be unusable). Reuses [RawEventIngestor]'s dedup
 * so re-running backfill, or backfill overlapping something already captured live, stays
 * idempotent rather than doubling transactions.
 */
@HiltWorker
class SmsBackfillWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val patternRegistry: BankSmsPatternRegistry,
    private val ingestor: RawEventIngestor,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Result.failure(workDataOf(KEY_ERROR to "READ_SMS not granted"))
        }

        var processed = 0
        try {
            val cutoffMillis = System.currentTimeMillis() - BACKFILL_WINDOW_MS
            applicationContext.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} >= ?",
                arrayOf(cutoffMillis.toString()),
                "${Telephony.Sms.DATE} ASC",
            )?.use { cursor ->
                val addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

                while (cursor.moveToNext()) {
                    val sender = cursor.getString(addressIdx) ?: continue
                    val body = cursor.getString(bodyIdx) ?: continue
                    val postedAt = cursor.getLong(dateIdx)

                    val match = try {
                        patternRegistry.match(sender, body)
                    } catch (e: Exception) {
                        Log.e(TAG, "pattern match crashed for backfilled sender $sender", e)
                        null
                    }
                    if (match == null || !match.senderRecognized) continue

                    ingestor.ingest(
                        source = RawEventSource.BACKFILL,
                        packageOrSender = sender,
                        fullText = body,
                        postedAt = postedAt,
                        parsed = match.parsed,
                        triggerEnrichment = false,
                    )
                    processed++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "backfill query failed", e)
            return Result.failure(workDataOf(KEY_ERROR to e.message, KEY_PROCESSED_COUNT to processed))
        }

        return Result.success(workDataOf(KEY_PROCESSED_COUNT to processed))
    }

    companion object {
        private const val TAG = "SmsBackfillWorker"
        private val BACKFILL_WINDOW_MS = TimeUnit.DAYS.toMillis(180)
        private const val WORK_NAME = "sms_backfill"
        const val KEY_PROCESSED_COUNT = "processed_count"
        const val KEY_ERROR = "error"

        /** KEEP, not REPLACE — backfill is a one-shot catch-up; a second tap while one is still
         * running shouldn't start a redundant duplicate pass over the same SMS history. */
        fun enqueue(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<SmsBackfillWorker>().build()
            workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        fun workName(): String = WORK_NAME
    }
}
