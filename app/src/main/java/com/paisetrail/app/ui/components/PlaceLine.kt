package com.paisetrail.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.paisetrail.app.ui.theme.PaisaTheme

/** The small muted location line beneath an amount ("near Lansdowne") — half of the
 * amount+place signature element (spec 7.7). Renders nothing when there's no place yet
 * (location still pending, or MISSING) rather than an empty line. */
@Composable
fun PlaceLine(placeText: String?, modifier: Modifier = Modifier) {
    if (placeText.isNullOrBlank()) return
    Text(
        text = placeText,
        style = PaisaTheme.typography.bodySecondary,
        color = PaisaTheme.colors.inkMuted,
        modifier = modifier,
    )
}
