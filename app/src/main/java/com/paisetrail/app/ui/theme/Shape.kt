package com.paisetrail.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** v2 radius scale (see `paisetrail-ui-redesign-spec.md` §2.4). Depth comes from surface
 * layering + a 1px inner top-edge highlight, not Material elevation shadows — see [PaisaCard]. */
object PaisaRadius {
    val card = 24.dp
    val sheet = 28.dp
    val chip = 12.dp
    val button = 16.dp
    val pill = 999.dp
}

val CardShape = RoundedCornerShape(PaisaRadius.card)
val SheetShape = RoundedCornerShape(topStart = PaisaRadius.sheet, topEnd = PaisaRadius.sheet)
val ChipShape = RoundedCornerShape(PaisaRadius.chip)
val ButtonShape = RoundedCornerShape(PaisaRadius.button)
val PillShape = RoundedCornerShape(PaisaRadius.pill)

@Deprecated("Use ChipShape, CardShape, or another v2 shape token", ReplaceWith("ChipShape"))
val PaisaCornerRadius = PaisaRadius.chip

@Deprecated("Use ChipShape, CardShape, or another v2 shape token", ReplaceWith("ChipShape"))
val PaisaShape = ChipShape

/** 4dp grid; screen gutter 20dp; vertical rhythm picks from {12, 20, 32}dp, nothing ad hoc. */
object PaisaSpacing {
    val gutter = 20.dp
    val tight = 12.dp
    val normal = 20.dp
    val loose = 32.dp
}
