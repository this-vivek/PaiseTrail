package com.paisetrail.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paisetrail.app.ui.theme.PaisaMotion
import com.paisetrail.app.ui.theme.PaisaTheme
import com.paisetrail.app.ui.theme.PillShape

/** A round icon button that fills with the accent color when active — the "Filter"/"Trips" toggle
 * style shared by the Map screen and the Transactions filter button. */
@Composable
fun IconPillButton(icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (active) PaisaTheme.colors.accent else PaisaTheme.colors.surface2,
        PaisaMotion.springDefault(),
        label = "iconPillBg",
    )
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(color = bg, shape = CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) PaisaTheme.colors.bg else PaisaTheme.colors.ink,
        )
    }
}

/** Selected = inverted (ink background, bg-colored text/icon) — unselected = surface2. A category
 * chip keeps its own hue as a tinted ring instead of the flat inversion so a filter row still
 * reads at a glance which category is which. */
@Composable
fun FancyChip(label: String, emoji: String?, colorHex: String?, selected: Boolean, onClick: () -> Unit) {
    val categoryColor = colorHex?.let { parseCategoryColor(it) }
    val bg by animateColorAsState(
        when {
            selected && categoryColor != null -> categoryColor.copy(alpha = 0.22f)
            selected -> PaisaTheme.colors.ink
            else -> PaisaTheme.colors.surface2
        },
        PaisaMotion.springDefault(),
        label = "chipBg",
    )
    val textColor = when {
        selected && categoryColor != null -> PaisaTheme.colors.ink
        selected -> PaisaTheme.colors.bg
        else -> PaisaTheme.colors.ink
    }

    Box(
        modifier = Modifier
            .background(color = bg, shape = PillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (emoji != null) {
                Text(text = emoji, fontSize = 14.sp)
                Box(modifier = Modifier.size(4.dp))
            }
            Text(
                text = label,
                style = PaisaTheme.typography.caption,
                color = textColor,
            )
        }
    }
}
