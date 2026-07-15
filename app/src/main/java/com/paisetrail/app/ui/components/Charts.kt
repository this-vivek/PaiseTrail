package com.paisetrail.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paisetrail.app.ui.theme.PaisaMotion
import com.paisetrail.app.ui.theme.PaisaTheme
import com.paisetrail.app.ui.theme.SpaceGroteskFontFamily
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Naked charts only — no backgrounds, no gridlines except a single hairline baseline, no
 * legends (spec 7.7). Plain Canvas rather than a charting library: fewer moving parts, and the
 * minimalist spec asks for less than most chart libraries render by default anyway. */

data class DonutSlice(val color: Color, val value: Float, val emoji: String?, val key: String)

/** A real pie — solid wedges, not a thin ring — with a modest center hole just big enough for the
 * total label. Each slice carries its own emoji (drawn mid-wedge) and, when [onSliceClick] is set,
 * the whole pie becomes tappable — a tap is mapped from (x, y) to an angle around the center, then
 * to whichever slice's angular sweep contains it, so "tap the Food wedge" jumps straight to that
 * category's transactions rather than requiring the legend row below. Tapping a slice also "pops"
 * it outward with a soft highlight ring, echoing a classic exploded-pie interaction. */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    centerLabel: String? = null,
    onSliceClick: ((DonutSlice) -> Unit)? = null,
) {
    val textMeasurer = rememberTextMeasurer()
    val centerLabelColor = PaisaTheme.colors.ink
    val holeColor = PaisaTheme.colors.bg
    val rimColor = PaisaTheme.colors.hairline
    val total = slices.sumOf { it.value.toDouble() }.toFloat()
    var selectedKey by remember(slices) { mutableStateOf<String?>(null) }
    val reduceMotion = PaisaMotion.reduceMotion(LocalContext.current)
    val sweepProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = if (reduceMotion) spring(stiffness = Spring.StiffnessHigh) else spring(dampingRatio = 1f, stiffness = 90f),
        label = "donutSweepIn",
    )

    Canvas(
        modifier = modifier
            .size(PIE_SIZE_DP.dp)
            .graphicsLayer {
                // A soft drop shadow grounds the pie above the card instead of sitting flush with
                // it — shape has to be explicit since graphicsLayer defaults to a rectangular
                // outline, which would cast a square shadow behind a circular chart.
                shadowElevation = PIE_SHADOW_ELEVATION_DP.dp.toPx()
                shape = CircleShape
                clip = false
            }
            .then(
                if (onSliceClick != null && total > 0f) {
                    Modifier.pointerInput(slices) {
                        detectTapGestures { offset ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val dx = offset.x - center.x
                            val dy = offset.y - center.y
                            val distance = hypot(dx, dy)
                            val outerRadius = minOf(size.width, size.height) / 2f
                            val holeRadius = outerRadius * PIE_HOLE_FRACTION
                            if (distance < holeRadius || distance > outerRadius) return@detectTapGestures
                            // atan2 measures from the +x axis counter-clockwise; arcs are drawn
                            // clockwise starting at -90deg (12 o'clock), so rotate to match.
                            val angle = (Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 90f + 360f) % 360f
                            var cursor = 0f
                            for (slice in slices) {
                                val sweep = 360f * (slice.value / total)
                                if (angle in cursor..(cursor + sweep)) {
                                    selectedKey = if (selectedKey == slice.key) null else slice.key
                                    onSliceClick(slice)
                                    break
                                }
                                cursor += sweep
                            }
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        if (total <= 0f) return@Canvas
        val outerRadius = size.minDimension / 2f
        val holeRadius = outerRadius * PIE_HOLE_FRACTION
        val center = Offset(size.width / 2f, size.height / 2f)
        val explodePx = PIE_EXPLODE_DP.dp.toPx()
        // A small gap between slices (only when there's more than one) reads as far less flat
        // than an unbroken ring.
        val gapDegrees = if (slices.size > 1) Math.toDegrees((PIE_GAP_DP.dp.toPx() / outerRadius).toDouble()).toFloat() else 0f

        var startAngle = -90f
        slices.forEach { slice ->
            val sweep = 360f * (slice.value / total) * sweepProgress
            // Honest proportions: a tiny slice should look tiny, not be inflated to a minimum
            // size just so it stays visible.
            val drawnSweep = (sweep - gapDegrees).coerceAtLeast(0.5f)
            val drawnStart = startAngle + (sweep - drawnSweep) / 2f
            val isSelected = slice.key == selectedKey
            val midAngleRad = Math.toRadians((drawnStart + drawnSweep / 2f).toDouble())
            val explodeOffset = if (isSelected) {
                Offset((explodePx * cos(midAngleRad)).toFloat(), (explodePx * sin(midAngleRad)).toFloat())
            } else {
                Offset.Zero
            }
            val wedgeTopLeft = Offset(center.x - outerRadius, center.y - outerRadius) + explodeOffset
            val wedgeSize = Size(outerRadius * 2, outerRadius * 2)

            // Flat fill in the category's own theme color — the same color used for its dot/chip
            // everywhere else in the app — brightened a touch only when tapped, so the pie always
            // reads as "this app's palette," not an unrelated glossy effect.
            drawArc(
                color = if (isSelected) lerp(slice.color, Color.White, 0.14f) else slice.color,
                startAngle = drawnStart,
                sweepAngle = drawnSweep,
                useCenter = true,
                topLeft = wedgeTopLeft,
                size = wedgeSize,
            )
            if (isSelected) {
                drawArc(
                    color = Color.White.copy(alpha = 0.55f),
                    startAngle = drawnStart,
                    sweepAngle = drawnSweep,
                    useCenter = true,
                    topLeft = wedgeTopLeft,
                    size = wedgeSize,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
            if (slice.emoji != null && sweep > DONUT_MIN_LABEL_SWEEP) {
                // Slivers get a smaller icon rather than none at all — every category should be
                // identifiable on the pie, not just the largest one or two.
                val fontSize = (9 + (sweep / 360f) * 14).coerceIn(9f, 13f).sp
                val labelRadius = (holeRadius + outerRadius) / 2f
                val labelText = "${slice.emoji} ${(100f * slice.value / total).roundToInt()}%"
                val labelSize = textMeasurer.measure(
                    labelText,
                    TextStyle(fontSize = fontSize, fontWeight = FontWeight.SemiBold, color = Color.White),
                )
                val labelCenter = Offset(
                    x = center.x + explodeOffset.x + (labelRadius * cos(midAngleRad)).toFloat(),
                    y = center.y + explodeOffset.y + (labelRadius * sin(midAngleRad)).toFloat(),
                )
                val chipPadding = 5.dp.toPx()
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.34f),
                    topLeft = Offset(
                        x = labelCenter.x - labelSize.size.width / 2f - chipPadding,
                        y = labelCenter.y - labelSize.size.height / 2f - chipPadding / 2f,
                    ),
                    size = Size(labelSize.size.width + chipPadding * 2, labelSize.size.height + chipPadding),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(labelSize.size.height / 2f + chipPadding / 2f),
                )
                drawText(
                    textLayoutResult = labelSize,
                    topLeft = Offset(
                        x = labelCenter.x - labelSize.size.width / 2f,
                        y = labelCenter.y - labelSize.size.height / 2f,
                    ),
                )
            }
            startAngle += 360f * (slice.value / total)
        }

        drawCircle(color = holeColor, radius = holeRadius, center = center)
        drawCircle(color = rimColor.copy(alpha = 0.5f), radius = holeRadius, center = center, style = Stroke(width = 1.dp.toPx()))
        drawCircle(color = rimColor.copy(alpha = 0.35f), radius = outerRadius - 1.dp.toPx() / 2f, center = center, style = Stroke(width = 1.dp.toPx()))

        val selectedSlice = slices.firstOrNull { it.key == selectedKey }
        val label = selectedSlice?.key ?: centerLabel
        if (label != null) {
            val labelSize = textMeasurer.measure(
                label,
                TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = SpaceGroteskFontFamily,
                    color = centerLabelColor,
                ),
            )
            drawText(
                textLayoutResult = labelSize,
                topLeft = Offset(
                    x = center.x - labelSize.size.width / 2f,
                    y = center.y - labelSize.size.height / 2f,
                ),
            )
        }
    }
}

private const val PIE_SIZE_DP = 208f
private const val PIE_SHADOW_ELEVATION_DP = 10f
// Weighted toward solid pie: the hole only needs to be big enough to hold the total label legibly.
private const val PIE_HOLE_FRACTION = 0.42f
private const val PIE_GAP_DP = 3f
private const val PIE_EXPLODE_DP = 10f
// Below this, a slice's arc is shorter than an emoji glyph is wide — showing one anyway just
// overlaps into the neighboring slice instead of reading as "this slice's icon".
private const val DONUT_MIN_LABEL_SWEEP = 22f

/** [dayLabels], one per entry in [values] if provided, renders below the baseline — sparsely
 * (not every bar) since 30 daily labels side by side would just overlap on a phone width, unless
 * [showAllLabels] (a 7-day chart has room to label every bar). Bars are centered in their slot
 * and width-capped ([MAX_BAR_WIDTH_FRACTION] of the chart) so a chart with only one or two data
 * points doesn't stretch a single bar across the whole width. */
@Composable
fun BarChart(
    values: List<Float>,
    barColor: Color,
    baselineColor: Color,
    modifier: Modifier = Modifier,
    dayLabels: List<String> = emptyList(),
    labelColor: Color = baselineColor,
    showAllLabels: Boolean = false,
    highlightLastBar: Boolean = false,
    highlightColor: Color = barColor,
    // The Y axis's "numbers" (spec 5 TODO) — a value label directly above each bar rather than a
    // full gridline scale, which would clash with the deliberately gridline-free minimalist chart
    // style (spec 7.7) everywhere else.
    showValueLabels: Boolean = false,
    formatValue: (Float) -> String = { it.toInt().toString() },
) {
    val reduceMotion = PaisaMotion.reduceMotion(LocalContext.current)
    val textMeasurer = rememberTextMeasurer()
    val growProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = if (reduceMotion) spring(stiffness = Spring.StiffnessHigh) else spring(dampingRatio = 1f, stiffness = 140f),
        label = "barGrowIn",
    )
    Column(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().height(if (showValueLabels) 96.dp else 80.dp)) {
            if (values.isEmpty()) return@Canvas
            val labelSpace = if (showValueLabels) VALUE_LABEL_SPACE_DP.dp.toPx() else 0f
            val chartHeight = size.height - labelSpace
            val max = values.maxOrNull()?.takeIf { it > 0f } ?: 1f
            val slotWidth = size.width / values.size
            val barWidth = minOf(slotWidth * 0.7f, size.width * MAX_BAR_WIDTH_FRACTION)
            val topRadius = BAR_TOP_RADIUS_DP.dp.toPx()
            values.forEachIndexed { index, value ->
                val barHeight = (value / max) * chartHeight * growProgress
                val slotCenter = index * slotWidth + slotWidth / 2f
                val color = if (highlightLastBar && index == values.lastIndex) highlightColor else barColor
                val barTop = labelSpace + chartHeight - barHeight
                drawRoundedTopRect(
                    color = color,
                    topLeft = Offset(slotCenter - barWidth / 2f, barTop),
                    size = Size(barWidth, barHeight),
                    topRadius = topRadius,
                )
                if (showValueLabels && value > 0f) {
                    val label = textMeasurer.measure(
                        formatValue(value),
                        TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, color = labelColor),
                    )
                    drawText(
                        textLayoutResult = label,
                        topLeft = Offset(
                            x = slotCenter - label.size.width / 2f,
                            y = (barTop - label.size.height - 2.dp.toPx()).coerceAtLeast(0f),
                        ),
                    )
                }
            }
            drawLine(
                color = baselineColor,
                start = Offset(0f, labelSpace + chartHeight),
                end = Offset(size.width, labelSpace + chartHeight),
                strokeWidth = 2f,
            )
        }
        DayLabelRow(dayLabels, labelColor, showAllLabels)
    }
}

private const val VALUE_LABEL_SPACE_DP = 18f

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoundedTopRect(
    color: Color,
    topLeft: Offset,
    size: Size,
    topRadius: Float,
) {
    if (size.height <= 0f) return
    val radius = minOf(topRadius, size.height, size.width / 2f)
    val cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
    val path = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = topLeft.x,
                top = topLeft.y,
                right = topLeft.x + size.width,
                bottom = topLeft.y + size.height,
                topLeftCornerRadius = cornerRadius,
                topRightCornerRadius = cornerRadius,
                bottomRightCornerRadius = androidx.compose.ui.geometry.CornerRadius.Zero,
                bottomLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius.Zero,
            ),
        )
    }
    drawPath(path, color = color)
}

private const val BAR_TOP_RADIUS_DP = 6f

/** Each day's total split into category-colored segments stacked bottom-up, so a single bar
 * shows both how much was spent that day and where it went, at a glance. */
@Composable
fun StackedBarChart(
    days: List<List<Pair<Color, Float>>>,
    baselineColor: Color,
    modifier: Modifier = Modifier,
    dayLabels: List<String> = emptyList(),
    labelColor: Color = baselineColor,
    showAllLabels: Boolean = false,
) {
    Column(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
            if (days.isEmpty()) return@Canvas
            val max = days.maxOfOrNull { day -> day.sumOf { it.second.toDouble() } }?.toFloat()?.takeIf { it > 0f } ?: 1f
            val slotWidth = size.width / days.size
            val barWidth = minOf(slotWidth * 0.7f, size.width * MAX_BAR_WIDTH_FRACTION)
            days.forEachIndexed { index, segments ->
                val slotCenter = index * slotWidth + slotWidth / 2f
                var yCursor = size.height
                segments.forEach { (color, value) ->
                    val segmentHeight = (value / max) * size.height
                    drawRect(
                        color = color,
                        topLeft = Offset(slotCenter - barWidth / 2f, yCursor - segmentHeight),
                        size = Size(barWidth, segmentHeight),
                    )
                    yCursor -= segmentHeight
                }
            }
            drawLine(
                color = baselineColor,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 2f,
            )
        }
        DayLabelRow(dayLabels, labelColor, showAllLabels)
    }
}

@Composable
private fun DayLabelRow(dayLabels: List<String>, labelColor: Color, showAllLabels: Boolean) {
    if (dayLabels.isEmpty()) return
    val step = if (showAllLabels) 1 else maxOf(1, dayLabels.size / 6)
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        dayLabels.forEachIndexed { index, label ->
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (index % step == 0 || index == dayLabels.size - 1) {
                    Text(
                        text = label,
                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        color = labelColor,
                        // A narrow per-slot box (30 bars sharing a phone's width) is narrower than
                        // a 2-digit label. softWrap=false alone only stops the digits stacking
                        // into two lines — the box still clips whatever doesn't fit horizontally
                        // (Text's default overflow) unless overflow is explicitly opened up, so
                        // the label can spill into the (empty) neighboring slots instead of being
                        // cut off mid-digit.
                        maxLines = 1,
                        softWrap = false,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Visible,
                    )
                }
            }
        }
    }
}

private const val MAX_BAR_WIDTH_FRACTION = 0.1f

@Composable
fun LineChart(values: List<Float>, lineColor: Color, baselineColor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(80.dp)) {
        drawLine(
            color = baselineColor,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 2f,
        )
        if (values.size < 2) return@Canvas
        val max = values.maxOrNull()?.takeIf { it > 0f } ?: 1f
        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index * stepX
            val y = size.height - (value / max) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = lineColor, style = Stroke(width = 4f))
    }
}

/** Tiny inline trend line (48×20dp) for a merchant/trip row — no axis, no baseline, just the
 * shape of the trend. */
@Composable
fun SparkLine(values: List<Float>, lineColor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 48.dp, height = 20.dp)) {
        if (values.size < 2) return@Canvas
        val max = values.maxOrNull()?.takeIf { it > 0f } ?: 1f
        val min = values.minOrNull() ?: 0f
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index * stepX
            val y = size.height - ((value - min) / range) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = lineColor, style = Stroke(width = 2f, cap = StrokeCap.Round))
    }
}

/** The Dashboard's 30-day trend: a line with a gradient fill fading to transparent, a spring
 * draw-in on first composition, and drag-to-scrub (vertical hairline + a point marker + a small
 * "as of" readout above the chart, with a haptic tick each time the scrubbed day changes). */
@Composable
fun AuroraAreaChart(
    values: List<Float>,
    dayLabels: List<String>,
    lineColor: Color,
    modifier: Modifier = Modifier,
    labelColor: Color = lineColor,
    formatValue: (Float) -> String = { it.toInt().toString() },
) {
    val reduceMotion = PaisaMotion.reduceMotion(LocalContext.current)
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    var scrubIndex by remember(values) { mutableStateOf<Int?>(null) }
    val drawProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = if (reduceMotion) spring(stiffness = Spring.StiffnessHigh) else spring(dampingRatio = 1f, stiffness = 90f),
        label = "areaDrawIn",
    )

    Column(modifier = modifier) {
        val scrubbedText = scrubIndex?.let { idx ->
            "${dayLabels.getOrNull(idx).orEmpty()} · ${formatValue(values.getOrElse(idx) { 0f })}"
        }
        Text(
            text = scrubbedText ?: " ",
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = SpaceGroteskFontFamily),
            color = labelColor,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .pointerInput(values) {
                    fun indexFor(x: Float): Int {
                        if (values.size < 2) return 0
                        val stepX = size.width / (values.size - 1)
                        return (x / stepX).toInt().coerceIn(0, values.lastIndex)
                    }
                    detectDragGestures(
                        onDragStart = { offset ->
                            scrubIndex = indexFor(offset.x)
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        },
                        onDragEnd = { scrubIndex = null },
                        onDragCancel = { scrubIndex = null },
                    ) { change, _ ->
                        val idx = indexFor(change.position.x)
                        if (idx != scrubIndex) {
                            scrubIndex = idx
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        }
                    }
                },
        ) {
            if (values.size < 2) return@Canvas
            val max = values.maxOrNull()?.takeIf { it > 0f } ?: 1f
            val stepX = size.width / (values.size - 1)
            val linePath = Path()
            values.forEachIndexed { index, value ->
                val x = index * stepX
                val y = size.height - (value / max) * size.height * drawProgress
                if (index == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
            }
            val fillPath = Path().apply {
                addPath(linePath)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(
                fillPath,
                brush = Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.12f), Color.Transparent)),
            )
            drawPath(linePath, color = lineColor, style = Stroke(width = 4f, cap = StrokeCap.Round))

            scrubIndex?.let { idx ->
                val x = idx * stepX
                val y = size.height - (values[idx] / max) * size.height * drawProgress
                drawLine(
                    color = lineColor.copy(alpha = 0.4f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.5.dp.toPx(),
                )
                drawCircle(color = lineColor, radius = 5.dp.toPx(), center = Offset(x, y))
            }
        }
        DayLabelRow(dayLabels, labelColor, showAllLabels = false)
    }
}
