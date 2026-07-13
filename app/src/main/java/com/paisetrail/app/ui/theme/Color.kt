package com.paisetrail.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Token set from the implementation plan, section 7.7. Dark is the default; light is derived.
 * Never introduce a new color outside this file or the category palette below.
 */
data class PaisaColors(
    val bg: Color,
    val surface: Color,
    val ink: Color,
    val inkMuted: Color,
    val hairline: Color,
    val accent: Color,
    val negative: Color,
    val isDark: Boolean,
)

val DarkPaisaColors = PaisaColors(
    bg = Color(0xFF0E0F11),
    surface = Color(0xFF16181B),
    ink = Color(0xFFE8E8E4),
    inkMuted = Color(0xFF8A8D93),
    hairline = Color(0xFF26282C),
    accent = Color(0xFF4C8DFF),
    negative = Color(0xFFE5645A),
    isDark = true,
)

val LightPaisaColors = PaisaColors(
    bg = Color(0xFFFAFAF8),
    surface = Color(0xFFFFFFFF),
    ink = Color(0xFF1A1B1E),
    inkMuted = Color(0xFF6B6E76),
    hairline = Color(0xFFE7E7E3),
    accent = Color(0xFF2F6FE0),
    negative = Color(0xFFD14840),
    isDark = false,
)

/**
 * The sole permitted color moment outside bg/surface/ink/accent/negative: map pins, donut
 * segments, and category dots. Eight desaturated hues at similar lightness so none screams.
 * Seeded 1:1 against the default category list (Food, Travel, Fuel, Stay, Shopping, Groceries,
 * Bills, Entertainment) with Health/P2P/Uncategorized reusing grey/slate.
 */
object CategoryPalette {
    val olive = Color(0xFF8A8B5C)
    val clay = Color(0xFFB4715B)
    val slateBlue = Color(0xFF5C7291)
    val sand = Color(0xFFBBA36A)
    val sage = Color(0xFF7C9473)
    val plum = Color(0xFF8B6A8E)
    val teal = Color(0xFF5C8F8A)
    val grey = Color(0xFF8A8D93)

    val all = listOf(olive, clay, slateBlue, sand, sage, plum, teal, grey)
}
