package ru.moycheckup.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.moycheckup.data.Body
import ru.moycheckup.data.BodyShape
import ru.moycheckup.data.Entry
import ru.moycheckup.data.Occurrence
import ru.moycheckup.data.Zone
import ru.moycheckup.ui.theme.LocalExtra

/** Стеклянная карточка: полупрозрачная заливка и светящаяся рамка. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val ex = LocalExtra.current
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.05f))))
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.30f), ex.glassLine, Color.White.copy(alpha = 0.10f))),
                shape,
            )
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun SevBadge(sev: Int, modifier: Modifier = Modifier) {
    val ex = LocalExtra.current
    val c = ex.sev(sev)
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(c, c.copy(alpha = 0.7f))))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (sev > 0) "$sev/10" else "ок",
            color = if (sev > 0) ex.onSev(sev) else Color(0xFF062018),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun CheckCircle(done: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ex = LocalExtra.current
    val pop = remember { Animatable(1f) }
    LaunchedEffect(done) {
        if (done) {
            pop.snapTo(0.6f)
            pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 380f))
        }
    }
    val fill by animateColorAsState(if (done) ex.mint else Color.Transparent, tween(180), label = "chk")
    Box(
        Modifier
            .size(32.dp)
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .clip(CircleShape)
            .background(fill)
            .border(2.dp, if (done) ex.mint else cs.onSurfaceVariant.copy(alpha = 0.5f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (done) Icon(Icons.Rounded.Check, contentDescription = "Выполнено", tint = cs.onPrimary, modifier = Modifier.size(19.dp))
    }
}

/** Строка напоминания с галочкой. */
@Composable
fun OccRow(o: Occurrence, done: Boolean, late: Boolean, showDay: Boolean, onToggle: () -> Unit) {
    val ex = LocalExtra.current
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CheckCircle(done, onToggle)
        Column(Modifier.weight(1f)) {
            Text(
                o.r.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (done) cs.onSurfaceVariant else cs.onSurface,
                textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
            )
            val detail = listOf(o.r.dose, o.r.how).filter { it.isNotBlank() }.joinToString(" · ")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(ex.type(o.r.type)))
                val whenText = (if (showDay) dayLabel(o.date) + ", " else "") + o.r.time.toString()
                Text(
                    "  ${o.r.type.ru} · $whenText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (late) cs.error else cs.onSurfaceVariant,
                    fontWeight = if (late) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

/** Карточка одной записи дневника. */
@Composable
fun EntryCard(e: Entry, onDelete: () -> Unit, showDate: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val ex = LocalExtra.current
    var confirm by remember { mutableStateOf(false) }
    Panel(padding = 16.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.width(52.dp)) {
                if (showDate) Text(e.date.fmt("d MMM"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Text(e.dateTime.fmt("HH:mm"), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (e.zones.isEmpty()) "Без жалоб" else e.zones.joinToString(", ") { it.label },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (e.kinds.isNotEmpty()) Text(e.kinds.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = ex.sev(e.sev))
                val details = (e.triggers + e.extras + listOfNotNull(e.since.takeIf { it.isNotBlank() }?.let { "болит: " + it.lowercase() }))
                if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                if (e.note.isNotBlank()) Text(e.note, style = MaterialTheme.typography.bodyMedium)
            }
            SevBadge(e.sev)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { confirm = true }) {
                Text("Удалить", color = cs.onSurfaceVariant)
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Удалить запись?") },
            text = { Text("Её не получится вернуть.") },
            confirmButton = {
                TextButton(onClick = { confirm = false; onDelete() }) { Text("Удалить", color = cs.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Отмена") }
            },
        )
    }
}

private fun DrawScope.drawShape(s: BodyShape, k: Float, fill: Color, edge: Color, stroke: Stroke, glow: Float) {
    when (s) {
        is BodyShape.Rect -> {
            val tl = Offset(s.x * k, s.y * k)
            val sz = Size(s.w * k, s.h * k)
            if (glow > 0f) {
                drawRoundRect(fill.copy(alpha = glow * 0.30f), Offset(tl.x - 9 * k, tl.y - 9 * k), Size(sz.width + 18 * k, sz.height + 18 * k), CornerRadius((s.r + 9) * k))
                drawRoundRect(fill.copy(alpha = glow * 0.45f), Offset(tl.x - 4 * k, tl.y - 4 * k), Size(sz.width + 8 * k, sz.height + 8 * k), CornerRadius((s.r + 4) * k))
            }
            drawRoundRect(fill, tl, sz, CornerRadius(s.r * k))
            drawRoundRect(edge, tl, sz, CornerRadius(s.r * k), style = stroke)
        }
        is BodyShape.Oval -> {
            val tl = Offset((s.cx - s.rx) * k, (s.cy - s.ry) * k)
            val sz = Size(s.rx * 2 * k, s.ry * 2 * k)
            if (glow > 0f) {
                drawOval(fill.copy(alpha = glow * 0.30f), Offset(tl.x - 9 * k, tl.y - 9 * k), Size(sz.width + 18 * k, sz.height + 18 * k))
                drawOval(fill.copy(alpha = glow * 0.45f), Offset(tl.x - 4 * k, tl.y - 4 * k), Size(sz.width + 8 * k, sz.height + 8 * k))
            }
            drawOval(fill, tl, sz)
            drawOval(edge, tl, sz, style = stroke)
        }
    }
}

/**
 * Универсальная карта зон: тело или голова. heat: подпись зоны -> самая сильная боль.
 * Выбранные и «горячие» зоны светятся и пульсируют, при нажатии расходится волна.
 * scan = true включает бегущую линию сканера.
 * onTap == null: карта только для показа.
 */
@Composable
fun ZoneMap(
    zones: List<Zone>,
    vw: Float,
    vh: Float,
    base: List<BodyShape>,
    selected: Set<String>,
    sev: Int,
    heat: Map<String, Int>,
    modifier: Modifier = Modifier,
    onTap: ((Zone) -> Unit)? = null,
    scan: Boolean = false,
) {
    val ex = LocalExtra.current
    val colors = zones.map { z ->
        val h = Body.heatFor(z, heat)
        val target = when {
            z.id in selected -> ex.sev(sev).copy(alpha = 0.85f)
            h > 0 -> ex.sev(h).copy(alpha = 0.75f)
            else -> ex.bodyFill
        }
        animateColorAsState(target, tween(260), label = "zone").value
    }
    val tap by rememberUpdatedState(onTap)
    val scope = rememberCoroutineScope()
    val ripple = remember { Animatable(1f) }
    var rippleAt by remember { mutableStateOf(Offset.Zero) }

    val t = rememberInfiniteTransition(label = "zonemap")
    val pulse by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val scanY by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3800, easing = LinearEasing), RepeatMode.Reverse), label = "scan")

    val tapModifier = if (onTap != null) {
        Modifier.pointerInput(zones) {
            detectTapGestures { off ->
                rippleAt = off
                scope.launch {
                    ripple.snapTo(0f)
                    ripple.animateTo(1f, tween(650, easing = LinearOutSlowInEasing))
                }
                val k = size.width / vw
                val z = Body.hit(zones, off.x / k, off.y / k, if (vw < 150f) 1.5f else 3f)
                if (z != null) tap?.invoke(z)
            }
        }
    } else Modifier

    Canvas(modifier.aspectRatio(vw / vh).then(tapModifier)) {
        val k = size.width / vw
        val stroke = Stroke(width = 1.3f * k)
        base.forEach { drawShape(it, k, ex.bodyFill, ex.bodyLine, stroke, 0f) }
        zones.forEachIndexed { i, z ->
            val isSel = z.id in selected
            val h = Body.heatFor(z, heat)
            val glow = when {
                isSel -> 0.4f + 0.6f * pulse
                h > 0 -> 0.25f + 0.55f * pulse * (h / 10f)
                else -> 0f
            }
            val edge = when {
                isSel -> Color.White
                h > 0 -> colors[i].copy(alpha = 0.95f)
                else -> ex.bodyLine
            }
            drawShape(z.shape, k, colors[i], edge, stroke, glow)
        }
        if (scan) {
            val y = scanY * size.height
            val band = 46f * density
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Transparent, ex.mint.copy(alpha = 0.24f), Color.Transparent),
                    startY = y - band, endY = y + band,
                ),
                topLeft = Offset(0f, y - band),
                size = Size(size.width, band * 2f),
            )
            drawLine(ex.mint.copy(alpha = 0.85f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f * density)
        }
        if (ripple.value < 0.999f) {
            drawCircle(
                ex.mint.copy(alpha = (1f - ripple.value) * 0.6f),
                radius = ripple.value * size.width * 0.3f,
                center = rippleAt,
                style = Stroke(width = 2.5f * density),
            )
        }
    }
}

/** Схема тела спереди или сзади. */
@Composable
fun BodyMap(
    front: Boolean,
    selected: Set<String>,
    sev: Int,
    heat: Map<String, Int>,
    modifier: Modifier = Modifier,
    onTap: ((Zone) -> Unit)? = null,
    scan: Boolean = false,
) = ZoneMap(Body.zones(front), Body.W, Body.H, emptyList(), selected, sev, heat, modifier, onTap, scan)

/** Подробная схема головы: лицо или затылок. */
@Composable
fun HeadMap(
    front: Boolean,
    selected: Set<String>,
    sev: Int,
    heat: Map<String, Int>,
    modifier: Modifier = Modifier,
    onTap: ((Zone) -> Unit)? = null,
    scan: Boolean = false,
) = ZoneMap(Body.headZones(front), Body.HW, Body.HH, Body.headBase, selected, sev, heat, modifier, onTap, scan)
