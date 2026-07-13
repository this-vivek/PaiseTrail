package com.paisetrail.app.capture.notification

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** SharedPreferences-backed — a single running count doesn't need a database table. */
@Singleton
class RedactedNotificationCounterImpl @Inject constructor(
    @ApplicationContext context: Context,
) : RedactedNotificationCounter {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun increment() {
        prefs.edit().putInt(KEY_COUNT, get() + 1).apply()
    }

    override fun get(): Int = prefs.getInt(KEY_COUNT, 0)

    companion object {
        private const val PREFS_NAME = "redacted_notification_counter"
        private const val KEY_COUNT = "count"
    }
}
