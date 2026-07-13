package com.paisetrail.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paisetrail.app.ui.theme.PaisaSpacing
import com.paisetrail.app.ui.theme.PaisaTheme

/** A round icon button that fills with the accent color when active — the "Filter"/"Trips" toggle
 * style shared by the Map screen and the Transactions filter button. */
@Composable
fun IconPillButton(icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(
                color = if (active) PaisaTheme.colors.accent else PaisaTheme.colors.surface,
                shape = CircleShape,
            )
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

/** A filter chip that keeps its own palette color as a tinted background/border when selected,
 * rather than every chip turning the same flat accent blue — makes a filter row read at a glance
 * instead of requiring you to read every label. [colorHex] null (e.g. an "All" chip) falls back to
 * the accent color. */
@Composable
fun FancyChip(label: String, emoji: String?, colorHex: String?, selected: Boolean, onClick: () -> Unit) {
    val color = if (colorHex != null) parseCategoryColor(colorHex) else PaisaTheme.colors.accent
    Box(
        modifier = Modifier
            .background(
                color = if (selected) color.copy(alpha = 0.22f) else PaisaTheme.colors.bg,
                shape = RoundedCornerShape(50),
            )
            .border(1.dp, if (selected) color else PaisaTheme.colors.hairline, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = PaisaSpacing.tight, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (emoji != null) {
                Text(text = emoji, fontSize = 14.sp)
                Box(modifier = Modifier.size(4.dp))
            }
            Text(
                text = label,
                style = PaisaTheme.typography.bodySecondary,
                color = PaisaTheme.colors.ink,
            )
        }
    }
}
