package com.paisetrail.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.paisetrail.app.ui.theme.PaisaTheme

/** Overline label + large mono value (spec 7.7) — the dashboard's month total, and any other
 * single-number stat that needs the same treatment. */
@Composable
fun StatBlock(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = PaisaTheme.typography.overline,
            color = PaisaTheme.colors.inkMuted,
        )
        content()
    }
}
