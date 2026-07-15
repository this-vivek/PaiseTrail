package com.paisetrail.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.paisetrail.app.onboarding.AppRootViewModel
import com.paisetrail.app.onboarding.OnboardingScreen
import com.paisetrail.app.ui.navigation.PaisaTrailNavHost
import com.paisetrail.app.ui.theme.PaisaTheme
import com.paisetrail.app.ui.theme.PaisaTrailTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PaisaTrailRoot()
        }
    }
}

/** Shows onboarding on first launch (spec §5.1) instead of an inert app until permissions are
 * found deep in Settings — [AppRootViewModel.onboardingDone] is null while the DataStore read is
 * still in flight, so nothing renders for that one frame rather than flashing onboarding. */
@Composable
private fun PaisaTrailRoot(rootViewModel: AppRootViewModel = hiltViewModel()) {
    val onboardingDone by rootViewModel.onboardingDone.collectAsState()

    PaisaTrailTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = PaisaTheme.colors.bg) {
            when (onboardingDone) {
                null -> Unit
                // No manual state flip needed here — completeOnboarding() writes DataStore, and
                // rootViewModel.onboardingDone reactively emits true, swapping to the nav host.
                false -> OnboardingScreen(onDone = {})
                true -> PaisaTrailNavHost()
            }
        }
    }
}
