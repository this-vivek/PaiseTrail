package com.paisetrail.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.paisetrail.app.ui.theme.CardShape
import com.paisetrail.app.ui.theme.PaisaMotion
import com.paisetrail.app.ui.theme.PaisaSpacing
import com.paisetrail.app.ui.theme.PaisaTheme
import kotlin.math.cos
import kotlin.math.sin

/** Budget-health hue state driving the Aurora — calm/comfortably-under, mid/approaching, or
 * hot/over. [ratio] is month-to-date spend as a fraction of total budget, or null when no budget
 * is configured (defaults to calm — nothing to judge against yet). */
enum class AuroraHealth {
    CALM, MID, HOT;

    companion object {
        fun fromRatio(ratio: Float?): AuroraHealth = when {
            ratio == null -> CALM
            ratio < 0.7f -> CALM
            ratio <= 1f -> MID
            else -> HOT
        }
    }
}

/**
 * The Dashboard hero (spec §0/§4.2) — a large rounded card whose background is an animated,
 * two-center radial gradient mesh, hue-shifted by [health]. The gradient drifts slowly (70–90s
 * loops) via [rememberInfiniteTransition] so it never looks static but is never distracting.
 */
@Composable
fun AuroraHeroCard(
    health: AuroraHealth,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp? = 200.dp,
    shape: androidx.compose.ui.graphics.Shape = CardShape,
    content: ColumnScopeContent,
) {
    val reduceMotion = PaisaMotion.reduceMotion(LocalContext.current)
    val transition = rememberInfiniteTransition(label = "aurora")
    val angle1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (reduceMotion) 0f else 360f,
        animationSpec = infiniteRepeatable(tween(70_000, easing = LinearEasing)),
        label = "auroraAngle1",
    )
    val angle2 by transition.animateFloat(
        initialValue = 90f,
        targetValue = if (reduceMotion) 90f else 450f,
        animationSpec = infiniteRepeatable(tween(85_000, easing = LinearEasing)),
        label = "auroraAngle2",
    )

    val colors = PaisaTheme.colors
    val (colorA, colorB) = when (health) {
        AuroraHealth.CALM -> colors.auroraCalm to colors.accentAlt
        AuroraHealth.MID -> colors.auroraMid to colors.auroraCalm
        AuroraHealth.HOT -> colors.auroraHot to colors.auroraMid
    }
    val animatedColorA by animateColorAsState(colorA, PaisaMotion.springGentle(), label = "auroraColorA")
    val animatedColorB by animateColorAsState(colorB, PaisaMotion.springGentle(), label = "auroraColorB")
    val surface1 = colors.surface1

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (height != null) Modifier.height(height) else Modifier.fillMaxHeight())
            .clip(shape),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(color = surface1)
            val orbitRadius = size.minDimension * 0.35f
            val cx = size.width / 2f
            val cy = size.height / 2f
            val center1 = Offset(
                cx + cos(Math.toRadians(angle1.toDouble())).toFloat() * orbitRadius,
                cy + sin(Math.toRadians(angle1.toDouble())).toFloat() * orbitRadius * 0.6f,
            )
            val center2 = Offset(
                cx + cos(Math.toRadians(angle2.toDouble())).toFloat() * orbitRadius,
                cy + sin(Math.toRadians(angle2.toDouble())).toFloat() * orbitRadius * 0.6f,
            )
            val blobRadius = size.minDimension * 0.9f
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(animatedColorA, Color.Transparent),
                    center = center1,
                    radius = blobRadius,
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(animatedColorB, Color.Transparent),
                    center = center2,
                    radius = blobRadius,
                ),
            )
            // A bottom scrim keeps hero text legible regardless of where the gradient centers land.
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f)),
                ),
            )
        }
        Column(modifier = Modifier.fillMaxSize().padding(PaisaSpacing.normal)) {
            content()
        }
    }
}

private typealias ColumnScopeContent = @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit

/** The slim gradient budget-progress bar shown inside the hero when a budget exists. */
@Composable
fun AuroraProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(50)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .background(
                    Brush.horizontalGradient(listOf(PaisaTheme.colors.accent, PaisaTheme.colors.accentAlt)),
                    RoundedCornerShape(50),
                ),
        )
    }
}
