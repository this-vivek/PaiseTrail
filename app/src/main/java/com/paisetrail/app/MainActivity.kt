package com.paisetrail.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

@Composable
private fun PaisaTrailRoot() {
    PaisaTrailTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = PaisaTheme.colors.bg) {
            PaisaTrailNavHost()
        }
    }
}
