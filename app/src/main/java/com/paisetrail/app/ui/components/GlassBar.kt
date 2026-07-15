package com.paisetrail.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.paisetrail.app.ui.theme.PaisaTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.HazeMaterials

/** Real backdrop blur (Haze) for the bottom nav, top bars-when-scrolled, bottom sheets, and map
 * overlay panels — the ONLY places glass is allowed to appear (spec §0). [hazeState] must be
 * shared with a [Modifier.hazeSourceOrNoop]-marked scrolling container behind this bar; on API
 * levels/devices where a real blur isn't available Haze automatically falls back to a translucent
 * scrim, so no separate fallback path is needed here. */
@Composable
fun GlassBar(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    content: @Composable () -> Unit,
) {
    val tint = PaisaTheme.colors.surfaceGlass
    val hairline = PaisaTheme.colors.hairline
    val style = HazeMaterials.thin(tint)

    Box(
        modifier = modifier
            .then(if (shape != null) Modifier.clip(shape) else Modifier)
            .then(if (shape != null) Modifier.border(1.dp, hairline, shape) else Modifier.border(1.dp, hairline))
            .hazeEffect(state = hazeState, style = style),
    ) {
        content()
    }
}
