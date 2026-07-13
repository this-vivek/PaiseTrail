package com.paisetrail.app.capture.notification

import javax.inject.Inject
import javax.inject.Singleton

/** Package allowlist + parser lookup (spec 3.1). Reject anything not in this map before doing
 * any text extraction — capture must stay cheap for the notifications we don't care about. */
@Singleton
class NotificationParserRegistry @Inject constructor() {
    private val parsers: Map<String, NotificationParser> = mapOf(
        "com.google.android.apps.nbu.paisa.user" to GPayNotificationParser(),
        "com.phonepe.app" to PhonePeNotificationParser(),
        "com.dreamplug.androidapp" to CredNotificationParser(),
        "net.one97.paytm" to PaytmNotificationParser(),
    )

    fun isAllowed(packageName: String): Boolean = parsers.containsKey(packageName)

    fun parserFor(packageName: String): NotificationParser? = parsers[packageName]
}
