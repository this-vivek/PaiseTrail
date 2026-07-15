@file:OptIn(ExperimentalTextApi::class)

package com.paisetrail.app.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.paisetrail.app.R

/**
 * Numerals/dates/ref numbers only, historically — superseded by [SpaceGroteskFontFamily] for the
 * v2 design system's display/amount roles, but still used by a few not-yet-migrated call sites.
 */
val MonoFontFamily = FontFamily(
    Font(R.font.ibm_plex_mono_regular, weight = FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, weight = FontWeight.Medium),
)

/** Display, titles, and every money amount (v2 design system) — geometric, distinctive numerals. */
val SpaceGroteskFontFamily = FontFamily(
    Font(
        R.font.space_grotesk_variable,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.space_grotesk_variable,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
)

/** Labels, merchant names, settings. Inter is shipped as a variable font; static weights are
 * synthesized via the `wght` axis rather than bundling one file per weight. */
val InterFontFamily = FontFamily(
    Font(
        R.font.inter_variable,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.inter_variable,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.inter_variable,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
)

private const val TABULAR_FIGURES = "tnum"

/**
 * v2 type scale ("Premium Fintech" — see `paisetrail-ui-redesign-spec.md` §2.3). [dashboardTotal],
 * [amountListHeader], [amountRow], [bodySecondary], and [overline] are deprecated aliases kept
 * only so screens not yet migrated to the new names keep compiling — migrate call sites to
 * [heroAmount]/[amountL]/[amountM]/[caption]/[label] as each screen is rebuilt, then delete these.
 */
data class PaisaTypography(
    val display: TextStyle,
    val title: TextStyle,
    val label: TextStyle,
    val body: TextStyle,
    val bodyBold: TextStyle,
    val caption: TextStyle,
    val heroAmount: TextStyle,
    val amountL: TextStyle,
    val amountM: TextStyle,
    val amountS: TextStyle,
) {
    @Deprecated("Use heroAmount", ReplaceWith("heroAmount"))
    val dashboardTotal: TextStyle get() = heroAmount

    @Deprecated("Use amountL", ReplaceWith("amountL"))
    val amountListHeader: TextStyle get() = amountL

    @Deprecated("Use amountM", ReplaceWith("amountM"))
    val amountRow: TextStyle get() = amountM

    @Deprecated("Use caption", ReplaceWith("caption"))
    val bodySecondary: TextStyle get() = caption

    @Deprecated("Use label", ReplaceWith("label"))
    val overline: TextStyle get() = label
}

val DefaultPaisaTypography = PaisaTypography(
    display = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
    ),
    title = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    label = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.6.sp,
    ),
    body = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyBold = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    caption = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    heroAmount = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    amountL = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    amountM = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    amountS = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
)
