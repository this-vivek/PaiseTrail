package com.paisetrail.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.paisetrail.app.ui.theme.PaisaSpacing
import com.paisetrail.app.ui.theme.PaisaTheme

/** The v2 transaction row: 64dp min height, a 40dp category-tinted tile (icon or initial letter)
 * instead of a bare dot, merchant name in `bodyBold` + a place/time caption line, and the amount
 * right-aligned in `amountM`. Credits render in `positive` with a small ↓ badge — debits still
 * render in `ink`, never `negative` (spending isn't an error state). No hairline between rows —
 * screens space these with 4dp gaps and a sticky date eyebrow instead (spec §3.5). */
@Composable
fun TxnRow(
    amountPaise: Long,
    merchantName: String,
    placeText: String?,
    categoryColorHex: String?,
    modifier: Modifier = Modifier,
    categoryEmoji: String? = null,
    isCredit: Boolean = false,
    sourceGlyph: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val tileColor = parseCategoryColor(categoryColorHex)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = PaisaSpacing.gutter, vertical = PaisaSpacing.tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(tileColor.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = categoryEmoji ?: merchantName.take(1).uppercase(),
                style = PaisaTheme.typography.bodyBold,
                color = tileColor,
            )
        }
        Spacer(Modifier.width(PaisaSpacing.tight))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = merchantName, style = PaisaTheme.typography.bodyBold, color = PaisaTheme.colors.ink)
            PlaceLine(placeText)
        }
        Spacer(Modifier.width(PaisaSpacing.tight))
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isCredit) {
                    Text(
                        text = "↓",
                        style = PaisaTheme.typography.caption,
                        color = PaisaTheme.colors.positive,
                        modifier = Modifier.padding(end = 2.dp),
                    )
                }
                AmountText(
                    amountPaise = amountPaise,
                    style = PaisaTheme.typography.amountM,
                    color = if (isCredit) PaisaTheme.colors.positive else PaisaTheme.colors.ink,
                    modifier = Modifier.wrapContentWidth(),
                )
            }
            if (sourceGlyph != null) {
                Text(text = sourceGlyph, style = PaisaTheme.typography.caption, color = PaisaTheme.colors.inkFaint)
            }
        }
    }
}
