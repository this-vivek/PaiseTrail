package com.paisetrail.app.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring

/** Spring/timing tokens for the v2 design system (see `paisetrail-ui-redesign-spec.md` §2.5).
 * Every animation in the app should reach for one of these rather than inventing a new curve.
 * Functions (not `val`s) so the same curve shape can drive Float, Color, Dp, or IntOffset
 * animations — `animateColorAsState(target, PaisaMotion.springDefault())`, etc. */
object PaisaMotion {
    fun <T> springDefault(): AnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)
    fun <T> springBouncy(): AnimationSpec<T> = spring(dampingRatio = 0.65f, stiffness = 300f)
    fun <T> springGentle(): AnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 200f)

    const val CHART_DRAW_MS = 700
    const val STAGGER_MS = 40
    const val MAX_STAGGERED_ITEMS = 8

    /** True when the system's animator duration scale is 0 (Settings > Accessibility > Remove
     * animations, or a developer-options override) — staggers/count-ups should jump straight to
     * their end state instead of animating. */
    fun reduceMotion(context: Context): Boolean =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}
