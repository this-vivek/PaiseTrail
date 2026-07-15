package com.paisetrail.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Design system v2 ("Premium Fintech" — see `paisetrail-ui-redesign-spec.md`). Dark is the
 * flagship; light is derived and must keep compiling/working but isn't the design target.
 * Never introduce a new color outside this file or [CategoryPalette].
 *
 * [surface] is a deprecated alias for [surface1], kept only so screens not yet migrated to the
 * layered surface1/surface2 model keep compiling — remove once every call site is migrated.
 */
data class PaisaColors(
    // Layered depth
    val bg: Color,
    val surface1: Color,
    val surface2: Color,
    val surfaceGlass: Color,
    // Ink
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val hairline: Color,
    // Brand
    val accent: Color,
    val accentAlt: Color,
    val positive: Color,
    val negative: Color,
    val warning: Color,
    // Aurora endpoints (hero gradient interpolates across these by budget health)
    val auroraCalm: Color,
    val auroraMid: Color,
    val auroraHot: Color,
    val isDark: Boolean,
) {
    @Deprecated("Use surface1 (or surface2 for nested content)", ReplaceWith("surface1"))
    val surface: Color get() = surface1
}

val DarkPaisaColors = PaisaColors(
    bg = Color(0xFF0A0C12),
    surface1 = Color(0xFF12151E),
    surface2 = Color(0xFF1A1E2A),
    surfaceGlass = Color(0x8C12151E),
    ink = Color(0xFFEDEEF2),
    inkMuted = Color(0xFF9AA0B0),
    inkFaint = Color(0xFF5D6373),
    hairline = Color(0xFF232838),
    accent = Color(0xFF6E7BFF),
    accentAlt = Color(0xFF54D6C8),
    positive = Color(0xFF4CC38A),
    negative = Color(0xFFE5645A),
    warning = Color(0xFFE8A33D),
    auroraCalm = Color(0xFF2B4C8C),
    auroraMid = Color(0xFF6E4FA3),
    auroraHot = Color(0xFF9C4A3C),
    isDark = true,
)

val LightPaisaColors = PaisaColors(
    bg = Color(0xFFF6F6FA),
    surface1 = Color(0xFFFFFFFF),
    surface2 = Color(0xFFEFF0F6),
    surfaceGlass = Color(0xE0FFFFFF),
    ink = Color(0xFF171A22),
    inkMuted = Color(0xFF6B6E76),
    inkFaint = Color(0xFF9BA0AC),
    hairline = Color(0xFFE2E3EA),
    accent = Color(0xFF4A58E8),
    accentAlt = Color(0xFF2FA79A),
    positive = Color(0xFF2E9A67),
    negative = Color(0xFFD14840),
    warning = Color(0xFFC07E1E),
    auroraCalm = Color(0xFF6E8FD4),
    auroraMid = Color(0xFF9E85C9),
    auroraHot = Color(0xFFC98577),
    isDark = false,
)

/**
 * The sole permitted color moment outside bg/surface/ink/accent/negative: map pins, donut
 * segments, and category dots. Jewel tones at matched lightness, seeded 1:1 against the default
 * category list (Food, Travel, Fuel, Stay, Shopping, Groceries, Bills, Entertainment) with
 * Health/P2P/Uncategorized reusing grey. Standard treatment: dot/pin/donut slice at 100% alpha,
 * backgrounds at 14% alpha (`color.copy(alpha = 0.14f)`), never as text color for amounts.
 */
object CategoryPalette {
    val amber = Color(0xFFE0A458)
    val coral = Color(0xFFE07A5F)
    val azure = Color(0xFF5B8DEF)
    val gold = Color(0xFFCDB04E)
    val emerald = Color(0xFF57B894)
    val lilac = Color(0xFFA78BDB)
    val cyan = Color(0xFF4FBBD1)
    val rose = Color(0xFFD46A9B)
    val grey = Color(0xFF9AA0B0)

    val all = listOf(amber, coral, azure, gold, emerald, lilac, cyan, rose, grey)
}
