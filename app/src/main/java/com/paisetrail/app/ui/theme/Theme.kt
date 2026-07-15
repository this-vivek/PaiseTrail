package com.paisetrail.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalPaisaColors = staticCompositionLocalOf { DarkPaisaColors }
val LocalPaisaTypography = staticCompositionLocalOf { DefaultPaisaTypography }

/**
 * "Premium Fintech" v2 (see `paisetrail-ui-redesign-spec.md`): deep layered dark surfaces, one
 * signature living gradient (the Aurora, on the Dashboard hero only), frosted-glass chrome, jewel
 * category color, confident spring motion. Debit amounts still render in `ink`, never `negative`
 * — spending isn't an error state. Dark-first; light is derived from the same token set.
 */
@Composable
fun PaisaTrailTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val paisaColors = if (darkTheme) DarkPaisaColors else LightPaisaColors
    val typography = DefaultPaisaTypography

    val materialColorScheme = if (darkTheme) {
        darkColorScheme(
            primary = paisaColors.accent,
            onPrimary = paisaColors.bg,
            background = paisaColors.bg,
            onBackground = paisaColors.ink,
            surface = paisaColors.surface1,
            onSurface = paisaColors.ink,
            surfaceVariant = paisaColors.surface1,
            onSurfaceVariant = paisaColors.inkMuted,
            outline = paisaColors.hairline,
            error = paisaColors.negative,
            onError = paisaColors.bg,
        )
    } else {
        lightColorScheme(
            primary = paisaColors.accent,
            onPrimary = paisaColors.surface1,
            background = paisaColors.bg,
            onBackground = paisaColors.ink,
            surface = paisaColors.surface1,
            onSurface = paisaColors.ink,
            surfaceVariant = paisaColors.surface1,
            onSurfaceVariant = paisaColors.inkMuted,
            outline = paisaColors.hairline,
            error = paisaColors.negative,
            onError = paisaColors.surface1,
        )
    }

    CompositionLocalProvider(
        LocalPaisaColors provides paisaColors,
        LocalPaisaTypography provides typography,
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            shapes = MaterialTheme.shapes.copy(medium = CardShape),
            content = content,
        )
    }
}

/** Shorthand accessors so screens read `PaisaTheme.colors.ink` instead of consuming locals directly. */
object PaisaTheme {
    val colors: PaisaColors
        @Composable get() = LocalPaisaColors.current

    val typography: PaisaTypography
        @Composable get() = LocalPaisaTypography.current
}
