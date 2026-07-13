package com.paisetrail.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** One radius value, no exceptions. */
val PaisaCornerRadius = 12.dp
val PaisaShape = RoundedCornerShape(PaisaCornerRadius)

/** 4dp grid; screen gutter 20dp; vertical rhythm picks from {12, 20, 32}dp, nothing ad hoc. */
object PaisaSpacing {
    val gutter = 20.dp
    val tight = 12.dp
    val normal = 20.dp
    val loose = 32.dp
}
