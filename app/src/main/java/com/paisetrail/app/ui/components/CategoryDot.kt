package com.paisetrail.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The category "icon" (spec 7.7): a soft-tinted circular badge with the category's emoji
 * centered in it, or — for a category with no emoji set — a plain flat dot in its color. Renders
 * nothing (just reserves the space) when there's no category at all: several seeded category
 * colors (e.g. Entertainment's grey, #8A8D93) are visually indistinguishable from a plain
 * fallback grey, so drawing a dot for "no category" made a tagged transaction look untagged. */
@Composable
fun CategoryDot(colorHex: String?, emoji: String? = null, modifier: Modifier = Modifier) {
    if (colorHex == null && emoji == null) {
        Box(modifier = modifier.size(DOT_SIZE))
        return
    }
    val color = remember(colorHex) { parseCategoryColor(colorHex) }

    if (emoji != null) {
        Box(
            modifier = modifier
                .size(BADGE_SIZE)
                .background(color = color.copy(alpha = BADGE_TINT_ALPHA), shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = emoji, fontSize = 12.sp)
        }
        return
    }

    Box(
        modifier = modifier
            .size(DOT_SIZE)
            .background(color = color, shape = CircleShape),
    )
}

private val DOT_SIZE = 8.dp
private val BADGE_SIZE = 22.dp
private const val BADGE_TINT_ALPHA = 0.22f
