package com.paisetrail.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.paisetrail.app.ui.theme.PaisaSpacing
import com.paisetrail.app.ui.theme.PaisaTheme

/** One of the 5-6 primitives that cover 90% of the UI (spec 7.7): category dot, merchant, place
 * line, amount right-aligned mono. Full-bleed — no card background, separated by
 * [HairlineDivider] between rows, not by its own elevation or border. */
@Composable
fun TxnRow(
    amountPaise: Long,
    merchantName: String,
    placeText: String?,
    categoryColorHex: String?,
    modifier: Modifier = Modifier,
    categoryEmoji: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = PaisaSpacing.gutter, vertical = PaisaSpacing.tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryDot(categoryColorHex, categoryEmoji)
        Spacer(Modifier.width(PaisaSpacing.tight))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = merchantName, style = PaisaTheme.typography.body, color = PaisaTheme.colors.ink)
            PlaceLine(placeText)
        }
        Spacer(Modifier.width(PaisaSpacing.tight))
        AmountText(
            amountPaise = amountPaise,
            style = PaisaTheme.typography.amountRow,
            modifier = Modifier.wrapContentWidth(),
        )
    }
}
