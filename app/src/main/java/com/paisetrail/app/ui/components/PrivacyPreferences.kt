package com.paisetrail.app.ui.components

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.privacyDataStore by preferencesDataStore(name = "privacy")
private val AMOUNTS_HIDDEN_KEY = booleanPreferencesKey("amounts_hidden")

/** Home screen amount-privacy toggle — hidden by default (a shoulder-surfing glance at the phone
 * shouldn't reveal how much was spent) until the user explicitly reveals it, same "opt into
 * exposure, not opt out of privacy" default as the rest of the app's permission asks. */
@Singleton
class PrivacyPreferences @Inject constructor(@ApplicationContext private val context: Context) {
    val amountsHidden: Flow<Boolean> = context.privacyDataStore.data
        .map { prefs -> prefs[AMOUNTS_HIDDEN_KEY] ?: true }

    suspend fun setAmountsHidden(hidden: Boolean) {
        context.privacyDataStore.edit { prefs -> prefs[AMOUNTS_HIDDEN_KEY] = hidden }
    }
}
