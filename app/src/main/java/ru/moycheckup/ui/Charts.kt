package ru.moycheckup.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId

data class ChartSeries(val points: List<Pair<Long, Double>>, val color: Color)

/** Линейный график. bands: полосы «ориентиров», закрашиваются слабо. Линия прорисовывается слева направо. */
@Composable
fun LineChart(
    series: List<ChartSeries>,
    bands: List<ClosedFloatingPointRange<Double>>,
    modifier: Modifier = Modifier,
    decimals: Int = 0,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(series) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1200, easing = FastOutSlowInEasing))
    }
    val tm = rememberTextMeasurer()
    val muted = Color(0xFF9AA8C7)
    val all = series.flatMap { it.points }

    Canvas(modifier) {
        if (all.isEmpty()) return@Canvas
        val padL = 40.dp.toPx()
        val padR = 14.dp.toPx()
        val padT = 12.dp.toPx()
        val padB = 24.dp.toPx()
        val w = size.width - padL - padR
        val h = size.height - padT - padB

        var minV = all.minOf { it.second }
        var maxV = all.maxOf { it.second }
        for (b in bands) {
            minV = minOf(minV, b.start)
            maxV = maxOf(maxV, b.endInclusive)
        }
        val span = (maxV - minV).coerceAtLeast(1.0)
        minV -= span * 0.12
        maxV += span * 0.12
        val minT = all.minOf { it.first }
        val maxT = all.maxOf { it.first }
        val tSpan = (maxT - minT).coerceAtLeast(1L)

        fun px(t: Long): Float = if (maxT == minT) padL + w / 2f else padL + ((t - minT).toFloat() / tSpan.toFloat()) * w
        fun py(v: Double): Float = padT + ((1.0 - (v - minV) / (maxV - minV)).toFloat()) * h
        fun fmtV(v: Double): String = if (decimals == 0) v.toInt().toString() else String.format(RU, "%.${decimals}f", v)

        // сетка и подписи по вертикали
        val label = TextStyle(fontSize = 10.sp, color = muted)
        for (i in 0..3) {
            val v = minV + (maxV - minV) * i / 3.0
            val y = py(v)
            drawLine(Color.White.copy(alpha = 0.07f), Offset(padL, y), Offset(padL + w, y), strokeWidth = 1f)
            drawText(tm, fmtV(v), Offset(2.dp.toPx(), y - 7.dp.toPx()), label)
        }
        // полосы ориентиров
        for (b in bands) {
            val y1 = py(b.endInclusive)
            val y2 = py(b.start)
            drawRect(Color(0xFF3DF2C4).copy(alpha = 0.07f), Offset(padL, y1), Size(w, y2 - y1))
        }
        // подписи дат
        val fmtD = { t: Long -> Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalDate().fmt("d MMM") }
        drawText(tm, fmtD(minT), Offset(padL, size.height - 16.dp.toPx()), label)
        if (maxT != minT) {
            val txt = fmtD(maxT)
            val m = tm.measure(txt, label)
            drawText(tm, txt, Offset(padL + w - m.size.width, size.height - 16.dp.toPx()), label)
        }

        for (s in series) {
            val pts = s.points.sortedBy { it.first }.map { Offset(px(it.first), py(it.second)) }
            if (pts.isEmpty()) continue
            val path = Path()
            path.moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) {
                val a = pts[i - 1]
                val b = pts[i]
                val mx = (a.x + b.x) / 2f
                path.cubicTo(mx, a.y, mx, b.y, b.x, b.y)
            }
            clipRect(0f, 0f, padL + (size.width - padL) * progress.value, size.height) {
                if (pts.size > 1) {
                    val area = Path()
                    area.addPath(path)
                    area.lineTo(pts.last().x, padT + h)
                    area.lineTo(pts.first().x, padT + h)
                    area.close()
                    drawPath(area, Brush.verticalGradient(listOf(s.color.copy(alpha = 0.30f), Color.Transparent), startY = padT, endY = padT + h))
                    drawPath(path, s.color.copy(alpha = 0.30f), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(path, s.color, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                pts.forEach {
                    drawCircle(Color(0xFF070B16), 4.6.dp.toPx(), it)
                    drawCircle(s.color, 3.2.dp.toPx(), it)
                }
            }
        }
    }
}

/** Полоска, которая плавно «заливается» до fraction. */
@Composable
fun AnimatedBar(fraction: Float, color: Color, modifier: Modifier = Modifier, vertical: Boolean = false) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(fraction) { a.animateTo(fraction.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing)) }
    Box(modifier, contentAlignment = if (vertical) Alignment.BottomCenter else Alignment.CenterStart) {
        if (vertical) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(a.value).background(color))
        } else {
            Box(Modifier.fillMaxHeight().fillMaxWidth(a.value).clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(color))
        }
    }
}
