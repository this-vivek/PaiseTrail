package com.paisetrail.app.capture.notification

import com.paisetrail.app.capture.ParsedTxn

/** One implementation per allowlisted app (spec 3.1) — registered by package in
 * [NotificationParserRegistry]. Returns null when the text doesn't look like a parseable debit. */
interface NotificationParser {
    fun parse(title: String?, text: String?, bigText: String?, subText: String?): ParsedTxn?
}
