package com.paisetrail.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paisetrail.app.ui.theme.MonoFontFamily
import kotlin.math.atan2
import kotlin.math.hypot

/** Naked charts only — no backgrounds, no gridlines except a single hairline baseline, no
 * legends (spec 7.7). Plain Canvas rather than a charting library: fewer moving parts, and the
 * minimalist spec asks for less than most chart libraries render by default anyway. */

data class DonutSlice(val color: Color, val value: Float, val emoji: String?, val key: String)

/** Each slice carries its own emoji (drawn mid-arc, on the ring) and, when [onSliceClick] is set,
 * the whole donut becomes tappable — a tap is mapped from (x, y) to an angle around the center,
 * then to whichever slice's angular sweep contains it, so "tap the Food wedge" jumps straight to
 * that category's transactions rather than requiring the legend row below. */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    centerLabel: String? = null,
    onSliceClick: ((DonutSlice) -> Unit)? = null,
) {
    val textMeasurer = rememberTextMeasurer()
    val total = slices.sumOf { it.value.toDouble() }.toFloat()

    Canvas(
        modifier = modifier
            .size(160.dp)
            .then(
                if (onSliceClick != null && total > 0f) {
                    Modifier.pointerInput(slices) {
                        detectTapGestures { offset ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val dx = offset.x - center.x
                            val dy = offset.y - center.y
                            val distance = hypot(dx, dy)
                            val outerRadius = minOf(size.width, size.height) / 2f
                            val strokeWidth = outerRadius * DONUT_STROKE_FRACTION
                            val innerRadius = outerRadius - strokeWidth
                            if (distance < innerRadius || distance > outerRadius) return@detectTapGestures
                            // atan2 measures from the +x axis counter-clockwise; arcs are drawn
                            // clockwise starting at -90deg (12 o'clock), so rotate to match.
                            val angle = (Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 90f + 360f) % 360f
                            var cursor = 0f
                            for (slice in slices) {
                                val sweep = 360f * (slice.value / total)
                                if (angle in cursor..(cursor + sweep)) {
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
        val strokeWidth = size.minDimension * DONUT_STROKE_FRACTION
        val radius = size.minDimension / 2f
        val midRadius = radius - strokeWidth / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        // A small gap between slices (only when there's more than one) reads as far less flat
        // than an unbroken ring — rounded caps on each slice reinforce the same "pill" look.
        val gapDegrees = if (slices.size > 1) DONUT_GAP_DEGREES else 0f

        var startAngle = -90f
        slices.forEach { slice ->
            val sweep = 360f * (slice.value / total)
            val drawnSweep = (sweep - gapDegrees).coerceAtLeast(sweep * 0.4f)
            val drawnStart = startAngle + (sweep - drawnSweep) / 2f
            drawArc(
                color = slice.color,
                startAngle = drawnStart,
                sweepAngle = drawnSweep,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            if (slice.emoji != null && sweep > DONUT_MIN_LABEL_SWEEP) {
                // Slivers get a smaller icon rather than none at all — every category should be
                // identifiable on the ring, not just the largest one or two.
                val fontSize = (10 + (sweep / 360f) * 18).coerceIn(10f, 15f).sp
                val midAngleRad = Math.toRadians((startAngle + sweep / 2f).toDouble())
                val labelSize = textMeasurer.measure(slice.emoji, TextStyle(fontSize = fontSize))
                val labelCenter = Offset(
                    x = center.x + (midRadius * kotlin.math.cos(midAngleRad)).toFloat(),
                    y = center.y + (midRadius * kotlin.math.sin(midAngleRad)).toFloat(),
                )
                drawText(
                    textLayoutResult = labelSize,
                    topLeft = Offset(
                        x = labelCenter.x - labelSize.size.width / 2f,
                        y = labelCenter.y - labelSize.size.height / 2f,
                    ),
                )
            }
            startAngle += sweep
        }

        if (centerLabel != null) {
            val labelSize = textMeasurer.measure(
                centerLabel,
                TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = MonoFontFamily),
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

private const val DONUT_STROKE_FRACTION = 0.18f
private const val DONUT_MIN_LABEL_SWEEP = 5f
private const val DONUT_GAP_DEGREES = 3f

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
) {
    Column(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
            if (values.isEmpty()) return@Canvas
            val max = values.maxOrNull()?.takeIf { it > 0f } ?: 1f
            val slotWidth = size.width / values.size
            val barWidth = minOf(slotWidth * 0.7f, size.width * MAX_BAR_WIDTH_FRACTION)
            values.forEachIndexed { index, value ->
                val barHeight = (value / max) * size.height
                val slotCenter = index * slotWidth + slotWidth / 2f
                drawRect(
                    color = barColor,
                    topLeft = Offset(slotCenter - barWidth / 2f, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                )
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
                        // a 2-digit label, so default wrapping stacks the digits vertically —
                        // force one line and let it overflow into the (empty) neighboring slots.
                        maxLines = 1,
                        softWrap = false,
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
