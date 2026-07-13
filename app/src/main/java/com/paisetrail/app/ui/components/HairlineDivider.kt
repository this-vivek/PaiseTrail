package com.paisetrail.app.ui.components

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.paisetrail.app.ui.theme.PaisaTheme

/** The only "border" in the app (spec 7.7) — 1dp, hairline color, nothing else. */
@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, color = PaisaTheme.colors.hairline, thickness = 1.dp)
}
