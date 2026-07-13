package com.paisetrail.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One map pin (spec 7.2/7.4) — color = category, size proportional to amount, category emoji
 * centered when the category has one, with a soft white ring and a little lift so it reads as a
 * marker sitting on the map rather than a flat dot painted onto it. Shared by the main Map screen,
 * each trip's mini-map, and the transaction detail map so a pin looks identical everywhere.
 * [isTripTagged] overlays a small star badge (same icon as the Trips tab) for a payment tagged to
 * a trip, so it reads as trip-related without losing its category color/icon underneath. */
@Composable
fun MapPin(amountPaise: Long, categoryColorHex: String?, categoryEmoji: String?, isTripTagged: Boolean = false) {
    val diameter = remember(amountPaise) { pinDiameter(amountPaise) }
    val color = remember(categoryColorHex) { parseCategoryColor(categoryColorHex) }
    Box {
        Box(
            modifier = Modifier
                .size(diameter)
                .shadow(elevation = 3.dp, shape = CircleShape, clip = false)
                .background(color = color, shape = CircleShape)
                .border(width = 2.dp, color = PIN_RING_COLOR, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (categoryEmoji != null) {
                Text(text = categoryEmoji, fontSize = (diameter.value * 0.42f).sp)
            }
        }
        if (isTripTagged) {
            val badgeSize = (diameter.value * 0.48f).dp
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = badgeSize * 0.3f, y = -badgeSize * 0.3f)
                    .size(badgeSize)
                    .shadow(elevation = 2.dp, shape = CircleShape, clip = false)
                    .background(color = ACCENT_COLOR, shape = CircleShape)
                    .border(width = 1.dp, color = PIN_RING_COLOR, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "Part of a trip",
                    tint = Color.White,
                    modifier = Modifier.size(badgeSize * 0.65f),
                )
            }
        }
    }
}

/** A grouped cluster of pins (spec 7.2) — same ring-and-shadow treatment as [MapPin] but sized
 * off the item count instead of an amount, and always in the accent color so a cluster reads as
 * distinct from any single category's pin. */
@Composable
fun MapClusterBubble(count: Int) {
    val diameter = remember(count) { clusterDiameter(count) }
    Box(
        modifier = Modifier
            .size(diameter)
            .shadow(elevation = 4.dp, shape = CircleShape, clip = false)
            .background(color = ACCENT_COLOR, shape = CircleShape)
            .border(width = 2.dp, color = PIN_RING_COLOR, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = count.toString(), color = Color.White, fontSize = (diameter.value * 0.34f).sp)
    }
}

private val PIN_RING_COLOR = Color(0xFFE8E8E4)
private val ACCENT_COLOR = Color(0xFF4C8DFF)

private fun pinDiameter(amountPaise: Long): Dp {
    val rupees = (amountPaise / 100).coerceAtLeast(1L)
    val scaled = 16 + (kotlin.math.sqrt(rupees.toDouble()) / 4).toInt()
    return scaled.coerceIn(16, 36).dp
}

private fun clusterDiameter(count: Int): Dp {
    val scaled = 28 + (kotlin.math.sqrt(count.toDouble()) * 4).toInt()
    return scaled.coerceIn(28, 52).dp
}
