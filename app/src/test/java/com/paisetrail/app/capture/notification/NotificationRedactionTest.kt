package com.paisetrail.app.capture.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRedactionTest {
    @Test
    fun `blank text is redacted`() {
        assertTrue(NotificationRedaction.isRedacted(""))
        assertTrue(NotificationRedaction.isRedacted("   "))
    }

    @Test
    fun `the Android 15 redaction placeholder is redacted regardless of case`() {
        assertTrue(NotificationRedaction.isRedacted("Sensitive notification content hidden"))
        assertTrue(NotificationRedaction.isRedacted("sensitive notification content hidden"))
        assertTrue(NotificationRedaction.isRedacted("Title | Sensitive Notification Content Hidden"))
    }

    @Test
    fun `real payment text is not redacted`() {
        assertFalse(NotificationRedaction.isRedacted("You paid ₹450 to Sharma Tea Stall"))
    }
}
