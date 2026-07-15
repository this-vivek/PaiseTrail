package com.paisetrail.app.onboarding

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore by preferencesDataStore(name = "onboarding")
private val ONBOARDING_DONE_KEY = booleanPreferencesKey("onboarding_done")

/** First-launch flag (spec §5.1) — the app is otherwise fully inert until notification/SMS access
 * is granted, which is the #1 UX gap the onboarding flow exists to close. */
@Singleton
class OnboardingPreferences @Inject constructor(@ApplicationContext private val context: Context) {
    val isOnboardingDone: Flow<Boolean> = context.onboardingDataStore.data
        .map { prefs -> prefs[ONBOARDING_DONE_KEY] ?: false }

    suspend fun setOnboardingDone() {
        context.onboardingDataStore.edit { prefs -> prefs[ONBOARDING_DONE_KEY] = true }
    }
}
