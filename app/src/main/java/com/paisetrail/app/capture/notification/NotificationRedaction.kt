package com.paisetrail.app.capture.notification

/** Pure predicate so the Android 15+ redaction check (spec 8 #8) is unit-testable without a real
 * [android.service.notification.StatusBarNotification]. */
object NotificationRedaction {
    private const val REDACTED_PLACEHOLDER = "Sensitive notification content hidden"

    fun isRedacted(fullText: String): Boolean =
        fullText.isBlank() || fullText.contains(REDACTED_PLACEHOLDER, ignoreCase = true)
}
