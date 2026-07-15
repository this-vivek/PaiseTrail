package com.paisetrail.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.paisetrail.app.ui.theme.CardShape
import com.paisetrail.app.ui.theme.PaisaMotion
import com.paisetrail.app.ui.theme.PaisaSpacing
import com.paisetrail.app.ui.theme.PaisaTheme

/** The one card shape the whole v2 app uses: surface1, 24dp radius, a 1px inner top-edge
 * highlight standing in for elevation shadow (see spec §2.4 — no Material elevation stacks here),
 * 20dp inner padding. [onClick] adds a press-scale-to-0.98 spring, matching every tappable row in
 * the redesign. */
@Composable
fun PaisaCard(
    modifier: Modifier = Modifier,
    padding: Dp = PaisaSpacing.normal,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, PaisaMotion.springDefault(), label = "cardPress")
    val hairline = PaisaTheme.colors.hairline

    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CardShape)
            .background(PaisaTheme.colors.surface1, CardShape)
            .drawWithContent {
                drawContent()
                drawLine(
                    color = hairline.copy(alpha = 0.4f),
                    start = Offset(0f, 0.5f),
                    end = Offset(size.width, 0.5f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .then(
                if (onClick != null) {
                    Modifier.pointerInput(onClick) {
                        detectTapGestures(
                            onPress = {
                                pressed = true
                                tryAwaitRelease()
                                pressed = false
                            },
                            onTap = { onClick() },
                        )
                    }
                } else {
                    Modifier
                },
            )
            .padding(padding),
    ) {
        content()
    }
}
