package com.paisetrail.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.paisetrail.app.ui.theme.PaisaMotion
import com.paisetrail.app.ui.theme.PaisaTheme

/** [AmountText] that counts up from zero on first composition (spec §2.5 "numbers tick") —
 * the Dashboard hero total and any other headline stat. Counts in whole rupees only (paise
 * ticking would be imperceptible at animation speed); [AmountText]'s own paise treatment still
 * renders once the count-up settles on the final value. Respects reduced-motion. */
@Composable
fun TickerAmount(
    amountPaise: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = PaisaTheme.typography.heroAmount,
    color: Color = PaisaTheme.colors.ink,
    textAlign: TextAlign? = null,
    masked: Boolean = false,
) {
    val reduceMotion = PaisaMotion.reduceMotion(LocalContext.current)
    val targetRupees = (amountPaise / 100).toInt()
    val animatedRupees by animateIntAsState(
        targetValue = targetRupees,
        animationSpec = if (reduceMotion) {
            spring(stiffness = Spring.StiffnessHigh)
        } else {
            spring(dampingRatio = 1f, stiffness = 200f)
        },
        label = "tickerAmount",
    )
    AmountText(
        amountPaise = if (reduceMotion) amountPaise else animatedRupees.toLong() * 100,
        modifier = modifier,
        style = style,
        color = color,
        textAlign = textAlign,
        masked = masked,
    )
}
