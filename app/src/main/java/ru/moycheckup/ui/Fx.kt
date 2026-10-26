package ru.moycheckup.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.moycheckup.ui.theme.LocalExtra
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Тёмный фон с медленно плывущим сиянием и мерцающими точками. */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val ex = LocalExtra.current
    val t = rememberInfiniteTransition(label = "aurora")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(17000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(0f, 1f, infiniteRepeatable(tween(23000, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    val c by t.animateFloat(0f, 1f, infiniteRepeatable(tween(29000, easing = LinearEasing), RepeatMode.Reverse), label = "c")
    val stars = remember {
        val r = Random(7)
        List(46) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) }
    }
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(ex.deep, ex.mid, ex.deep)))
        fun blob(color: Color, cx: Float, cy: Float, r: Float, alpha: Float) {
            drawCircle(
                Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center = Offset(cx, cy), radius = r),
                radius = r,
                center = Offset(cx, cy),
            )
        }
        val w = size.width
        val h = size.height
        blob(ex.mint, w * (0.10f + 0.65f * a), h * (0.06f + 0.14f * b), w * 0.95f, 0.30f)
        blob(ex.violet, w * (0.95f - 0.65f * b), h * (0.34f + 0.18f * c), w * 1.05f, 0.34f)
        blob(ex.pink, w * (0.15f + 0.55f * c), h * (0.74f + 0.12f * a), w * 0.9f, 0.24f)
        blob(ex.blue, w * (0.8f - 0.5f * a), h * (0.95f - 0.1f * b), w * 0.8f, 0.2f)
        stars.forEach { (x, y, p) ->
            val tw = 0.25f + 0.5f * abs(sin((a + p) * 6.28f))
            drawCircle(Color.White.copy(alpha = tw * 0.45f), radius = (0.5f + p * 1.1f) * density, center = Offset(x * w, y * h))
        }
    }
}

/** Плавное появление снизу с задержкой по номеру элемента. */
fun Modifier.enter(index: Int = 0): Modifier = composed {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index * 70L).coerceAtMost(560L))
        p.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * 56f
        val s = 0.94f + 0.06f * p.value
        scaleX = s
        scaleY = s
    }
}

/** Число, которое «набегает» от нуля до target. */
@Composable
fun countUp(target: Int, ms: Int = 1400): Int {
    val a = remember { Animatable(0f) }
    LaunchedEffect(target) { a.animateTo(target.toFloat(), tween(ms, easing = FastOutSlowInEasing)) }
    return a.value.roundToInt()
}

// ---------- ЭКГ ----------

private fun ecgShape(ph: Float): Float = when {
    ph < 0.10f -> 0f
    ph < 0.18f -> 0.16f * sin((ph - 0.10f) / 0.08f * PI.toFloat())
    ph < 0.28f -> 0f
    ph < 0.31f -> -0.18f * ((ph - 0.28f) / 0.03f)
    ph < 0.36f -> -0.18f + 1.18f * ((ph - 0.31f) / 0.05f)
    ph < 0.42f -> 1.0f - 1.38f * ((ph - 0.36f) / 0.06f)
    ph < 0.47f -> -0.38f + 0.38f * ((ph - 0.42f) / 0.05f)
    ph < 0.60f -> 0f
    ph < 0.74f -> 0.26f * sin((ph - 0.60f) / 0.14f * PI.toFloat())
    else -> 0f
}

private fun ecgPath(w: Float, h: Float, beats: Float): Path {
    val p = Path()
    var x = 0f
    var first = true
    while (x <= w) {
        val ph = ((x / w) * beats) % 1f
        val y = h / 2f - ecgShape(ph) * h * 0.45f
        if (first) {
            p.moveTo(x, y)
            first = false
        } else {
            p.lineTo(x, y)
        }
        x += 3f
    }
    return p
}

/** Бегущая линия ЭКГ со светящейся головой. */
@Composable
fun EcgLine(modifier: Modifier = Modifier, color: Color, beats: Float = 2.4f) {
    val t = rememberInfiniteTransition(label = "ecg")
    val head by t.animateFloat(0f, 1.35f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "head")
    Canvas(modifier) {
        val path = ecgPath(size.width, size.height, beats)
        val sw = 2.2f * density
        drawPath(path, color.copy(alpha = 0.18f), style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val right = size.width * head
        val left = right - size.width * 0.42f
        clipRect(left, 0f, right, size.height) {
            drawPath(path, color.copy(alpha = 0.35f), style = Stroke(sw * 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, color, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

// ---------- кольцо самочувствия ----------

@Composable
fun WellnessRing(score: Int?, caption: String, modifier: Modifier = Modifier) {
    val ex = LocalExtra.current
    val prog = remember { Animatable(0f) }
    LaunchedEffect(score) { prog.animateTo((score ?: 0) / 100f, tween(1700, easing = FastOutSlowInEasing)) }
    val t = rememberInfiniteTransition(label = "ring")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "spin")
    val breathe by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "br")
    val shown = countUp(score ?: 0)

    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = size.minDimension * 0.07f
            val pad = sw * 1.5f
            val tl = Offset(pad, pad)
            val arc = Size(size.width - pad * 2, size.height - pad * 2)
            val colors = listOf(ex.mint, ex.blue, ex.violet, ex.pink, ex.mint)

            rotate(spin) {
                drawCircle(
                    Color.White.copy(alpha = 0.14f),
                    radius = size.minDimension / 2f - 2f,
                    style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 16f))),
                )
            }
            drawArc(Color.White.copy(alpha = 0.08f), 0f, 360f, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
            val sweep = 360f * prog.value
            if (sweep > 0.5f) {
                rotate(-90f) {
                    drawArc(
                        brush = Brush.sweepGradient(colors), startAngle = 0f, sweepAngle = sweep, useCenter = false,
                        topLeft = tl, size = arc, alpha = 0.30f * breathe, style = Stroke(sw * 2.4f, cap = StrokeCap.Round),
                    )
                    drawArc(
                        brush = Brush.sweepGradient(colors), startAngle = 0f, sweepAngle = sweep, useCenter = false,
                        topLeft = tl, size = arc, style = Stroke(sw, cap = StrokeCap.Round),
                    )
                }
                // светящаяся точка на конце дуги
                val ang = ((-90f + sweep) * PI / 180.0).toFloat()
                val r = arc.width / 2f
                val c = Offset(size.width / 2f + r * cos(ang), size.height / 2f + r * sin(ang))
                drawCircle(Color.White.copy(alpha = 0.25f * breathe), radius = sw * 1.3f, center = c)
                drawCircle(Color.White, radius = sw * 0.42f, center = c)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (score == null) "—" else shown.toString(),
                fontSize = 60.sp,
                lineHeight = 62.sp,
                fontWeight = FontWeight.ExtraLight,
                color = Color.White,
            )
            Text(caption, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

// ---------- настроение ----------

@Composable
fun MoodFace(level: Int, selected: Boolean, modifier: Modifier = Modifier) {
    val ex = LocalExtra.current
    val col = when (level) {
        1 -> ex.s3
        2 -> ex.s2
        3 -> ex.s1
        4 -> Color(0xFF9BE564)
        else -> ex.mint
    }
    val sc by animateFloatAsState(if (selected) 1.2f else 1f, spring(dampingRatio = 0.4f, stiffness = 300f), label = "face")
    Canvas(modifier.graphicsLayer { scaleX = sc; scaleY = sc }) {
        val r = size.minDimension / 2f
        val c = center
        if (selected) drawCircle(col.copy(alpha = 0.30f), radius = r * 1.18f, center = c)
        drawCircle(if (selected) col else Color.White.copy(alpha = 0.08f), radius = r * 0.92f, center = c)
        drawCircle(col, radius = r * 0.92f, center = c, style = Stroke(width = r * 0.08f))
        val ink = if (selected) Color(0xFF10131F) else col
        drawCircle(ink, r * 0.09f, Offset(c.x - r * 0.3f, c.y - r * 0.2f))
        drawCircle(ink, r * 0.09f, Offset(c.x + r * 0.3f, c.y - r * 0.2f))
        val mw = r * 0.6f
        val y0 = c.y + r * 0.32f
        val curve = (level - 3) * 0.17f * r
        val mouth = Path().apply {
            moveTo(c.x - mw / 2f, y0)
            cubicTo(c.x - mw / 6f, y0 + curve * 1.9f, c.x + mw / 6f, y0 + curve * 1.9f, c.x + mw / 2f, y0)
        }
        drawPath(mouth, ink, style = Stroke(width = r * 0.09f, cap = StrokeCap.Round))
    }
}

// ---------- конфетти ----------

private class Particle(val angle: Float, val speed: Float, val color: Color, val size: Float, val spin: Float)

/** Праздничный залп. Запускается каждый раз, когда trigger увеличивается. */
@Composable
fun Confetti(trigger: Int, modifier: Modifier = Modifier) {
    val ex = LocalExtra.current
    val progress = remember { Animatable(1f) }
    var particles by remember { mutableStateOf(emptyList<Particle>()) }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            val palette = listOf(ex.mint, ex.violet, ex.pink, ex.blue, ex.s1, Color.White)
            particles = List(110) {
                Particle(
                    angle = (-PI.toFloat()) * (0.12f + 0.76f * Random.nextFloat()),
                    speed = 0.35f + 0.85f * Random.nextFloat(),
                    color = palette[Random.nextInt(palette.size)],
                    size = 5f + 7f * Random.nextFloat(),
                    spin = 6f * (Random.nextFloat() - 0.5f),
                )
            }
            progress.snapTo(0f)
            progress.animateTo(1f, tween(2300, easing = LinearOutSlowInEasing))
        }
    }
    Canvas(modifier.fillMaxSize()) {
        val p = progress.value
        if (p < 0.999f) {
            val cx = size.width / 2f
            val cy = size.height * 0.62f
            for (q in particles) {
                val x = cx + cos(q.angle) * q.speed * p * size.width * 0.9f
                val y = cy + sin(q.angle) * q.speed * p * size.height * 0.75f + p * p * size.height * 0.55f
                val a = (1f - p).coerceIn(0f, 1f)
                rotate(q.spin * p * 360f, pivot = Offset(x, y)) {
                    drawRect(q.color.copy(alpha = a), topLeft = Offset(x - q.size / 2f, y - q.size / 2f), size = Size(q.size, q.size * 0.55f))
                }
            }
        }
    }
}

// ---------- заставка ----------

@Composable
fun Splash(onDone: () -> Unit) {
    val ex = LocalExtra.current
    val draw = remember { Animatable(0f) }
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        draw.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 170f))
        delay(420)
        onDone()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AuroraBackground()
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(170.dp)) {
                val sw = 5f * density
                val pad = sw * 1.5f
                rotate(-90f) {
                    drawArc(
                        brush = Brush.sweepGradient(listOf(ex.mint, ex.blue, ex.violet, ex.pink, ex.mint)),
                        startAngle = 0f, sweepAngle = 360f * draw.value, useCenter = false,
                        topLeft = Offset(pad, pad), size = Size(size.width - pad * 2, size.height - pad * 2),
                        style = Stroke(sw, cap = StrokeCap.Round),
                    )
                }
                val path = ecgPath(size.width * 0.6f, size.height * 0.5f, 1.2f)
                val ox = size.width * 0.2f
                val oy = size.height * 0.25f
                clipRect(ox, oy, ox + size.width * 0.6f * draw.value, oy + size.height * 0.5f) {
                    translate(ox, oy) {
                        drawPath(path, ex.mint, style = Stroke(sw * 0.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
            Text(
                "Мой чекап",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.graphicsLayer {
                    alpha = pop.value
                    val s = 0.8f + 0.2f * pop.value
                    scaleX = s
                    scaleY = s
                },
            )
        }
    }
}
