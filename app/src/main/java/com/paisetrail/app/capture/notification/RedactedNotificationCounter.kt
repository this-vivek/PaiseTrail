package com.paisetrail.app.capture.notification

/** Tracks how often a notification arrives with its content redacted (spec 8 #8) — an interface
 * so [com.paisetrail.app.ui.screens.debug.RawEventsDebugViewModel] can be tested without a real
 * Android Context, same pattern as [com.paisetrail.app.trips.HomeLocationStore]. */
interface RedactedNotificationCounter {
    fun increment()
    fun get(): Int
}
