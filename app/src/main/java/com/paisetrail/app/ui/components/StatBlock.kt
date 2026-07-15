package com.paisetrail.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.paisetrail.app.ui.theme.PaisaTheme
import kotlin.math.abs

/** Eyebrow label (`label` style) + large value + an optional ▲/▼ delta chip (v2 design system) —
 * the dashboard's month total, and any other single-number stat that needs the same treatment. */
@Composable
fun StatBlock(
    label: String,
    modifier: Modifier = Modifier,
    deltaPercent: Int? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = PaisaTheme.typography.label,
            color = PaisaTheme.colors.inkMuted,
        )
        content()
        if (deltaPercent != null) {
            val positive = deltaPercent >= 0
            val color = if (positive) PaisaTheme.colors.positive else PaisaTheme.colors.negative
            Row(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .background(color.copy(alpha = 0.14f), RoundedCornerShape(50)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${if (positive) "▲" else "▼"} ${abs(deltaPercent)}%",
                    style = PaisaTheme.typography.caption,
                    color = color,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}
