package com.paisetrail.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.paisetrail.app.ui.theme.PaisaTheme
import kotlin.math.abs

/** The canonical mono renderer (spec 7.7) — forbidden to format money any other way. Indian
 * digit grouping (₹1,45,300), whole rupees only (every spec mockup shows amounts with no paise). */
@Composable
fun AmountText(
    amountPaise: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = PaisaTheme.typography.amountRow,
    color: androidx.compose.ui.graphics.Color = PaisaTheme.colors.ink,
    textAlign: TextAlign? = null,
) {
    androidx.compose.material3.Text(
        text = formatIndianRupees(amountPaise),
        modifier = modifier,
        style = style,
        color = color,
        textAlign = textAlign,
    )
}

fun formatIndianRupees(amountPaise: Long): String {
    val rupees = amountPaise / 100
    val isNegative = rupees < 0
    val digits = abs(rupees).toString()

    val grouped = if (digits.length <= 3) {
        digits
    } else {
        val lastThree = digits.substring(digits.length - 3)
        val remaining = digits.substring(0, digits.length - 3)
        val groups = mutableListOf<String>()
        var end = remaining.length
        while (end > 0) {
            val start = maxOf(0, end - 2)
            groups.add(0, remaining.substring(start, end))
            end = start
        }
        groups.joinToString(",") + "," + lastThree
    }

    return (if (isNegative) "-₹" else "₹") + grouped
}
