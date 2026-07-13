package com.paisetrail.app.ui.screens.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paisetrail.app.capture.notification.RedactedNotificationCounter
import com.paisetrail.app.data.db.RawEventDao
import com.paisetrail.app.data.db.RawEventEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Backs the parser debug screen (spec 7.6 / 10 Phase 1) — the tool for tuning regexes against
 * what the capture layer actually saw, without waiting for a rebuild. */
@HiltViewModel
class RawEventsDebugViewModel @Inject constructor(
    rawEventDao: RawEventDao,
    redactedNotificationCounter: RedactedNotificationCounter,
) : ViewModel() {
    val events: StateFlow<List<RawEventEntity>> = rawEventDao.observeRecent(limit = 200)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // A plain snapshot, not a Flow — SharedPreferences has no natural change notification here,
    // and this screen is re-created fresh on every navigation into it (spec 8 #8).
    val redactedNotificationCount: Int = redactedNotificationCounter.get()
}
