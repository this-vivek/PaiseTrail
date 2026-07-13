package com.paisetrail.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.paisetrail.app.ui.theme.PaisaSpacing
import com.paisetrail.app.ui.theme.PaisaTheme

/**
 * Stand-in for screens not yet built (see task list, Phase 4 replaces these one by one).
 * Empty-state copy follows the design system: one line of inkMuted text, sentence case.
 */
@Composable
fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(PaisaSpacing.gutter),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$title — coming in a later phase",
            style = PaisaTheme.typography.bodySecondary,
            color = PaisaTheme.colors.inkMuted,
        )
    }
}
