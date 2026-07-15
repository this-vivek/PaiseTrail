package com.paisetrail.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import com.paisetrail.app.ui.theme.PaisaMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Fade+rise entry for the first screenful of a list (spec §2.5 "lists stagger") — only the first
 * [PaisaMotion.MAX_STAGGERED_ITEMS] by [index] animate; anything composed later (scrolled into
 * view) just appears, so scrolling itself never re-triggers the stagger. */
@Composable
fun StaggeredEntry(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduceMotion = PaisaMotion.reduceMotion(LocalContext.current)
    val shouldAnimate = index < PaisaMotion.MAX_STAGGERED_ITEMS && !reduceMotion
    val alpha = remember { Animatable(if (shouldAnimate) 0f else 1f) }
    val offsetY = remember { Animatable(if (shouldAnimate) 12f else 0f) }

    LaunchedEffect(Unit) {
        if (!shouldAnimate) return@LaunchedEffect
        delay(index * PaisaMotion.STAGGER_MS.toLong())
        launch { alpha.animateTo(1f, tween(220)) }
        launch { offsetY.animateTo(0f, tween(220)) }
    }

    Box(
        modifier = modifier.graphicsLayer {
            this.alpha = alpha.value
            translationY = offsetY.value
        },
    ) {
        content()
    }
}
