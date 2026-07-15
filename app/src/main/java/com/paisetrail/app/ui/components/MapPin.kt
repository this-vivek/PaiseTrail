package com.paisetrail.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paisetrail.app.ui.theme.PaisaTheme

/** One map pin (spec 7.2/7.4) — a category-colored disc, size proportional to amount, with the
 * category emoji centered when there is one, a soft ring, and a little lift so it reads as a
 * marker sitting on the map. Shared by the main Map screen, each trip's mini-map, and the
 * transaction detail map so a pin looks identical everywhere. [isTripTagged] adds a second outer
 * ring in the accent color (v2 design system) rather than a corner badge, so it reads as
 * trip-related without a second shape competing with the category disc. */
@Composable
fun MapPin(amountPaise: Long, categoryColorHex: String?, categoryEmoji: String?, isTripTagged: Boolean = false) {
    val diameter = remember(amountPaise) { pinDiameter(amountPaise) }
    val color = remember(categoryColorHex) { parseCategoryColor(categoryColorHex) }
    val ringColor = PaisaTheme.colors.surface1
    val accent = PaisaTheme.colors.accent

    Box(contentAlignment = Alignment.Center) {
        if (isTripTagged) {
            Box(
                modifier = Modifier
                    .size(diameter + 8.dp)
                    .border(width = 2.dp, color = accent, shape = CircleShape),
            )
        }
        Box(
            modifier = Modifier
                .size(diameter)
                .shadow(elevation = 3.dp, shape = CircleShape, clip = false)
                .background(color = color, shape = CircleShape)
                .border(width = 2.dp, color = ringColor, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (categoryEmoji != null) {
                Text(text = categoryEmoji, fontSize = (diameter.value * 0.42f).sp)
            }
        }
    }
}

/** A grouped cluster of pins (spec 7.2) — a dark glass-tinted circle sized off the item count,
 * with the count in `ink` and a proportional multi-color ring built from [categorySlices] (each
 * category's color, weighted by however many/much of the cluster it accounts for) so a cluster
 * hints at its actual category mix before you zoom in or tap it — the same "real pie, not a thin
 * sliver" language as [DonutChart]. */
@Composable
fun MapClusterBubble(count: Int, categorySlices: List<Pair<Color, Float>> = emptyList()) {
    val diameter = remember(count) { clusterDiameter(count) }
    val glass = PaisaTheme.colors.surfaceGlass
    val ink = PaisaTheme.colors.ink
    val ringColor = PaisaTheme.colors.surface1

    Box(
        modifier = Modifier
            .size(diameter)
            .shadow(elevation = 4.dp, shape = CircleShape, clip = false)
            .background(color = glass, shape = CircleShape)
            .border(width = 2.dp, color = ringColor, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val total = categorySlices.sumOf { it.second.toDouble() }.toFloat()
        if (total > 0f) {
            Canvas(modifier = Modifier.size(diameter)) {
                val strokeWidth = 3.dp.toPx()
                val gapDegrees = if (categorySlices.size > 1) 5f else 0f
                var startAngle = -90f
                categorySlices.forEach { (color, value) ->
                    val sweep = 360f * (value / total)
                    val drawnSweep = (sweep - gapDegrees).coerceAtLeast(2f)
                    drawArc(
                        color = color,
                        startAngle = startAngle + (sweep - drawnSweep) / 2f,
                        sweepAngle = drawnSweep,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                    startAngle += sweep
                }
            }
        }
        Text(text = count.toString(), color = ink, fontSize = (diameter.value * 0.34f).sp)
    }
}

private fun pinDiameter(amountPaise: Long): Dp {
    val rupees = (amountPaise / 100).coerceAtLeast(1L)
    val scaled = 28 + (kotlin.math.sqrt(rupees.toDouble()) / 3).toInt()
    return scaled.coerceIn(28, 48).dp
}

private fun clusterDiameter(count: Int): Dp {
    val scaled = 34 + (kotlin.math.sqrt(count.toDouble()) * 4).toInt()
    return scaled.coerceIn(34, 58).dp
}
