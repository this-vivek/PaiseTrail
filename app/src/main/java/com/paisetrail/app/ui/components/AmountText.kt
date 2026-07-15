package com.paisetrail.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import com.paisetrail.app.ui.theme.PaisaTheme
import kotlin.math.abs

/** The canonical amount renderer, forbidden to format money any other way. Indian digit grouping
 * (₹1,45,300.50) — the ₹ symbol and the paise are rendered at 0.62x size in [mutedColor], baseline
 * aligned with the whole-rupee digits (v2 design system's "paise treatment"). */
@Composable
fun AmountText(
    amountPaise: Long,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = PaisaTheme.typography.amountM,
    color: Color = PaisaTheme.colors.ink,
    mutedColor: Color = PaisaTheme.colors.inkMuted,
    textAlign: TextAlign? = null,
    // Home screen amount-privacy toggle (spec 5 TODO) — masks in place rather than the caller
    // branching between two composables, so every call site gets it for free.
    masked: Boolean = false,
) {
    if (masked) {
        Text(text = maskedRupees(), modifier = modifier, style = style, color = color, textAlign = textAlign)
        return
    }
    val smallSize = if (style.fontSize.isSpecified) {
        TextUnit(style.fontSize.value * PAISE_SIZE_FRACTION, style.fontSize.type)
    } else {
        style.fontSize
    }
    Text(
        text = buildAnnotatedString {
            val rupees = amountPaise / 100
            val paise = abs(amountPaise % 100)
            val isNegative = amountPaise < 0
            withStyle(SpanStyle(color = mutedColor, fontSize = smallSize)) { append("₹") }
            append((if (isNegative) "-" else "") + groupIndianDigits(abs(rupees).toString()))
            if (paise > 0) {
                withStyle(SpanStyle(color = mutedColor, fontSize = smallSize)) {
                    append(".")
                    append(paise.toString().padStart(2, '0'))
                }
            }
        },
        modifier = modifier,
        style = style,
        color = color,
        textAlign = textAlign,
    )
}

private const val PAISE_SIZE_FRACTION = 0.62f

/** Plain-string whole-rupee formatter for non-Compose-Text contexts (notification text, JSON
 * export summaries, Settings status lines) — [AmountText] is the only place paise are ever shown. */
fun formatIndianRupees(amountPaise: Long): String {
    val rupees = amountPaise / 100
    val isNegative = rupees < 0
    return (if (isNegative) "-₹" else "₹") + groupIndianDigits(abs(rupees).toString())
}

/** [formatIndianRupees]'s masked counterpart for the amount-privacy toggle — a fixed placeholder
 * rather than dots matching the real digit count, since the digit count itself is information. */
fun maskedRupees(): String = "₹ ••••"

private fun groupIndianDigits(digits: String): String {
    if (digits.length <= 3) return digits
    val lastThree = digits.substring(digits.length - 3)
    val remaining = digits.substring(0, digits.length - 3)
    val groups = mutableListOf<String>()
    var end = remaining.length
    while (end > 0) {
        val start = maxOf(0, end - 2)
        groups.add(0, remaining.substring(start, end))
        end = start
    }
    return groups.joinToString(",") + "," + lastThree
}
