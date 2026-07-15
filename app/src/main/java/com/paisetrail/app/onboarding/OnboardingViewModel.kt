package com.paisetrail.app.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Null while the DataStore read is still in flight — the app root waits for a real answer
 * before deciding whether to show onboarding, rather than flashing it on a fast device. */
@HiltViewModel
class AppRootViewModel @Inject constructor(
    private val onboardingPreferences: OnboardingPreferences,
) : ViewModel() {
    val onboardingDone: StateFlow<Boolean?> = onboardingPreferences.isOnboardingDone
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboardingPreferences: OnboardingPreferences,
) : ViewModel() {
    fun completeOnboarding() {
        viewModelScope.launch { onboardingPreferences.setOnboardingDone() }
    }
}
