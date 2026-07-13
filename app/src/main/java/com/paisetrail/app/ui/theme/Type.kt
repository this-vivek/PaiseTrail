@file:OptIn(ExperimentalTextApi::class)

package com.paisetrail.app.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.paisetrail.app.R

/**
 * Numerals/dates/ref numbers only. Tabular figures so columns of money align digit-for-digit —
 * this is a functional choice per the design system, not decoration. Never format money outside
 * this family (see AmountText, added in the UI phase).
 */
val MonoFontFamily = FontFamily(
    Font(R.font.ibm_plex_mono_regular, weight = FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, weight = FontWeight.Medium),
)

/**
 * Labels, merchant names, settings. Inter is shipped as a variable font; static weights are
 * synthesized via the `wght` axis rather than bundling one file per weight.
 */
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

/**
 * Hierarchy comes from size + weight + ink/inkMuted, never color or italic. Sizes are fixed to
 * the three roles the design system defines — do not add ad hoc sizes.
 */
data class PaisaTypography(
    val dashboardTotal: TextStyle,
    val amountListHeader: TextStyle,
    val amountRow: TextStyle,
    val body: TextStyle,
    val bodySecondary: TextStyle,
    val overline: TextStyle,
)

val DefaultPaisaTypography = PaisaTypography(
    dashboardTotal = TextStyle(
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 44.sp,
    ),
    amountListHeader = TextStyle(
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
    ),
    amountRow = TextStyle(
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
    ),
    body = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
    ),
    bodySecondary = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
    ),
    overline = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        letterSpacing = 0.08.em,
    ),
)
