package ru.moycheckup.ui

import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.moycheckup.data.AppState
import ru.moycheckup.data.Entry
import ru.moycheckup.data.Repo
import ru.moycheckup.data.Vital
import ru.moycheckup.data.VitalType
import ru.moycheckup.data.WeekStats
import ru.moycheckup.data.badges
import ru.moycheckup.data.insights
import ru.moycheckup.data.maxSevOn
import ru.moycheckup.data.weekStats
import ru.moycheckup.report.PdfReport
import ru.moycheckup.ui.theme.LocalExtra
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/** Переключатель с «жидким» индикатором. */
@Composable
fun SegmentedTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val ex = LocalExtra.current
    val shape = RoundedCornerShape(26.dp)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape),
    ) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * selected, spring(dampingRatio = 0.72f, stiffness = 420f), label = "seg")
        Box(
            Modifier
                .offset(x = x)
                .width(w)
                .fillMaxHeight()
                .padding(4.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.horizontalGradient(listOf(ex.mint.copy(alpha = 0.30f), ex.violet.copy(alpha = 0.30f))))
                .border(1.dp, ex.mint.copy(alpha = 0.4f), RoundedCornerShape(22.dp)),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, name ->
                Box(Modifier.weight(1f).fillMaxHeight().clickable { onSelect(i) }, contentAlignment = Alignment.Center) {
                    Text(
                        name,
                        color = if (i == selected) Color.White else Color(0xFF9AA8C7),
                        fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatTile(modifier: Modifier, value: String, caption: String) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, fontSize = 30.sp, fontWeight = FontWeight.Light, lineHeight = 34.sp)
        Text(caption, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
}

@Composable
fun HealthScreen(
    state: AppState,
    now: LocalDateTime,
    bottomPad: Dp,
    onAddVital: () -> Unit,
    onDeleteVital: (Vital) -> Unit,
    onDeleteEntry: (Entry) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val side = Modifier.padding(horizontal = 16.dp)

    var section by rememberSaveable { mutableIntStateOf(0) }
    var weekOff by rememberSaveable { mutableIntStateOf(0) }
    var vtIdx by rememberSaveable { mutableIntStateOf(0) }
    var rangeIdx by rememberSaveable { mutableIntStateOf(1) }
    var monthOff by rememberSaveable { mutableIntStateOf(0) }
    var selDay by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Vital?>(null) }

    val w = state.weekStats(weekOff, now)

    fun makePdf(then: (File) -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            val f = try {
                withContext(Dispatchers.Default) { PdfReport.build(ctx, state, weekOff, now) }
            } catch (e: Exception) {
                null
            }
            busy = false
            if (f != null) then(f) else Toast.makeText(ctx, "Не получилось собрать PDF", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(top = top + 12.dp, bottom = bottomPad),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { ScreenTitle("Здоровье") }
        item { Box(side) { SegmentedTabs(listOf("Неделя", "Показатели", "Календарь"), section) { section = it } } }
        when (section) {
            0 -> weekItems(state, now, w, weekOff, { weekOff = it }, busy, { makePdf { f -> PdfReport.share(ctx, f) } }, { makePdf { f -> PdfReport.open(ctx, f) } })
            1 -> vitalItems(state, vtIdx, { vtIdx = it }, rangeIdx, { rangeIdx = it }, now, onAddVital) { toDelete = it }
            else -> calendarItems(state, now, monthOff, { monthOff = it }, selDay, { selDay = it }, onDeleteEntry)
        }
    }

    val del = toDelete
    if (del != null) {
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить измерение?") },
            text = { Text(del.type.ru + ": " + del.display() + " " + del.type.unit) },
            confirmButton = {
                TextButton(onClick = { onDeleteVital(del); toDelete = null }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } },
        )
    }
}

// ---------------------------------------------------------------- неделя

private fun LazyListScope.weekItems(
    state: AppState,
    now: LocalDateTime,
    w: WeekStats,
    offset: Int,
    onOffset: (Int) -> Unit,
    busy: Boolean,
    onShare: () -> Unit,
    onOpen: () -> Unit,
) {
    val side = Modifier.padding(horizontal = 16.dp)

    item(key = "w-nav") {
        Row(side.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { onOffset(offset - 1) }) { Icon(Icons.Rounded.KeyboardArrowLeft, contentDescription = "Предыдущая неделя") }
            Text(weekLabel(w.days), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { if (offset < 0) onOffset(offset + 1) }, enabled = offset < 0) {
                Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = "Следующая неделя")
            }
        }
    }

    item(key = "w-stats") {
        Box(side.enter(0)) {
            Panel {
                val n = countUp(w.pain.size)
                val d = countUp(w.painDays)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(Modifier.weight(1f), n.toString(), plural(w.pain.size, "запись", "записи", "записей") + " о боли")
                    StatTile(Modifier.weight(1f), "$d из 7", "дней с болью")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(Modifier.weight(1f), if (w.pain.isEmpty()) "—" else String.format(RU, "%.1f", w.avg), "средняя сила, из 10")
                    StatTile(Modifier.weight(1f), if (w.pain.isEmpty()) "—" else w.max.toString(), "самая сильная, из 10")
                }
            }
        }
    }

    item(key = "w-body") {
        val cs = MaterialTheme.colorScheme
        val ex = LocalExtra.current
        Box(side.enter(1)) {
            Panel {
                Label("Где болело")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BodyMap(true, emptySet(), 0, w.heat, Modifier.height(220.dp))
                        Label("спереди")
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BodyMap(false, emptySet(), 0, w.heat, Modifier.height(220.dp))
                        Label("сзади")
                    }
                }
                if (w.top.isEmpty()) {
                    Text("За эту неделю записей о боли нет.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                } else {
                    val mx = w.top.first().n
                    w.top.take(6).forEach { z ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(z.label, Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium)
                            AnimatedBar(maxOf(0.08f, z.n.toFloat() / mx), ex.sev(z.max), Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.08f)))
                            Text(z.n.toString(), Modifier.padding(start = 2.dp), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Text(
                        "Цвет показывает самую сильную боль в зоне, число показывает, сколько раз её отмечали.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        }
    }

    item(key = "w-days") {
        val cs = MaterialTheme.colorScheme
        val ex = LocalExtra.current
        Box(side.enter(2)) {
            Panel {
                Label("По дням, самая сильная боль")
                Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    w.perDay.forEach { p ->
                        Column(
                            Modifier.weight(1f).fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(if (p.max > 0) p.max.toString() else if (p.good) "ок" else "", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            AnimatedBar(
                                p.max / 10f, ex.sev(p.max),
                                Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.07f)),
                                vertical = true,
                            )
                            Text(p.date.fmt("EE").cap(), fontSize = 12.sp, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    item(key = "w-meds") {
        val cs = MaterialTheme.colorScheme
        val ex = LocalExtra.current
        Box(side.enter(3)) {
            Panel {
                Label("Лекарства и визиты")
                val done = w.pillsPast.count { state.done.contains(it.key) }
                Text(
                    if (w.pillsPast.isEmpty()) "Приёмов таблеток за прошедшие дни нет." else "Таблетки приняты $done из ${w.pillsPast.size}.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (w.pillsPast.isNotEmpty()) {
                    AnimatedBar(
                        done.toFloat() / w.pillsPast.size, ex.mint,
                        Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.08f)),
                    )
                }
                if (w.visits.isEmpty()) {
                    Text("Визитов и анализов на этой неделе нет.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                w.visits.forEach { o ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(ex.type(o.r.type)))
                        Text(
                            "  " + o.date.fmt("EE, d MMM").cap() + ", " + o.r.time + " · " + o.r.title +
                                (if (state.done.contains(o.key)) " · выполнено" else ""),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }

    val insights = state.insights(now)
    if (insights.isNotEmpty()) {
        item(key = "w-insights") {
            val ex = LocalExtra.current
            Box(side.enter(4)) {
                Panel {
                    Label("Наблюдения")
                    insights.forEach { i ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(i.title, style = MaterialTheme.typography.labelLarge, color = ex.mint)
                            Text(i.text, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Text(
                        "Это подсчёты по вашим записям, а не диагноз.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    item(key = "w-badges") {
        val ex = LocalExtra.current
        val cs = MaterialTheme.colorScheme
        Box(side.enter(5)) {
            Panel {
                Label("Достижения")
                state.badges(now).forEach { b ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (b.unlocked) Brush.linearGradient(listOf(ex.mint, ex.violet))
                                    else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f)))
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (b.unlocked) "✓" else "·", color = if (b.unlocked) Color(0xFF03141F) else cs.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(b.title, style = MaterialTheme.typography.titleMedium, color = if (b.unlocked) cs.onSurface else cs.onSurfaceVariant)
                            Text(b.desc, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    item(key = "w-pdf") {
        val cs = MaterialTheme.colorScheme
        Box(side.enter(6)) {
            Panel {
                Label("Файл для врача")
                Text(
                    "PDF на 1–3 страницы: итоги, схема тела, все записи, показатели, таблетки и визиты, ближайшие планы.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
                GradientButton(if (busy) "Собираю PDF…" else "Отправить или сохранить PDF", onShare, Modifier.fillMaxWidth(), enabled = !busy)
                GlassButton("Открыть PDF", onOpen, Modifier.fillMaxWidth())
            }
        }
    }
}

// ------------------------------------------------------------ показатели

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.vitalItems(
    state: AppState,
    vtIdx: Int,
    onVt: (Int) -> Unit,
    rangeIdx: Int,
    onRange: (Int) -> Unit,
    now: LocalDateTime,
    onAdd: () -> Unit,
    onDelete: (Vital) -> Unit,
) {
    val side = Modifier.padding(horizontal = 16.dp)
    val types = VitalType.values()
    val vt = types[vtIdx.coerceIn(0, types.lastIndex)]
    val ranges = listOf("7 дней" to 7, "30 дней" to 30, "Всё время" to 100000)
    val days = ranges[rangeIdx.coerceIn(0, ranges.lastIndex)].second
    val cutoff = now.toLocalDate().minusDays(days.toLong() - 1)
    val data = state.vitals.filter { it.type == vt && !it.date.isBefore(cutoff) }.sortedBy { it.ts }

    item(key = "v-chips") {
        Column(side.enter(0), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                types.forEachIndexed { i, t -> FilterChip(selected = i == vtIdx, onClick = { onVt(i) }, label = { Text(t.ru) }) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ranges.forEachIndexed { i, r -> FilterChip(selected = i == rangeIdx, onClick = { onRange(i) }, label = { Text(r.first) }) }
            }
        }
    }

    item(key = "v-chart-" + vt.name + rangeIdx) {
        val ex = LocalExtra.current
        val cs = MaterialTheme.colorScheme
        Box(side.enter(1)) {
            Panel {
                Label(vt.ru + ", " + vt.unit)
                val last = data.lastOrNull()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text(last?.display() ?: "—", fontSize = 44.sp, fontWeight = FontWeight.Light, lineHeight = 46.sp)
                    if (data.size > 1) {
                        val avg = data.map { it.a }.average()
                        val avgB = data.map { it.b }.average()
                        Text(
                            "среднее " + (if (vt == VitalType.PRESSURE) "${avg.toInt()}/${avgB.toInt()}" else if (vt == VitalType.PULSE) avg.toInt().toString() else String.format(RU, "%.1f", avg)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
                if (data.isEmpty()) {
                    Text("Измерений за этот период нет. Нажмите «Добавить измерение».", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                } else {
                    val series = when (vt) {
                        VitalType.PRESSURE -> listOf(
                            ChartSeries(data.map { it.ts to it.a }, ex.pink),
                            ChartSeries(data.map { it.ts to it.b }, ex.blue),
                        )
                        else -> listOf(ChartSeries(data.map { it.ts to it.a }, ex.mint))
                    }
                    val bands = when (vt) {
                        VitalType.PRESSURE -> listOf(90.0..129.0, 60.0..84.0)
                        VitalType.PULSE -> listOf(60.0..100.0)
                        VitalType.TEMP -> listOf(36.0..37.0)
                        VitalType.WEIGHT -> emptyList()
                    }
                    LineChart(series, bands, Modifier.fillMaxWidth().height(190.dp), decimals = if (vt == VitalType.TEMP || vt == VitalType.WEIGHT) 1 else 0)
                    if (vt == VitalType.PRESSURE) {
                        Text("Розовая линия: верхнее, синяя: нижнее.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    if (bands.isNotEmpty()) {
                        Text(
                            "Зелёная полоса: общие ориентиры для взрослых. Они не заменяют консультацию врача.",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    item(key = "v-add") {
        Box(side.enter(2)) { GradientButton("Добавить измерение", onAdd, Modifier.fillMaxWidth()) }
    }

    val recent = state.vitals.filter { it.type == vt }.sortedByDescending { it.ts }.take(8)
    if (recent.isNotEmpty()) {
        item(key = "v-list-" + vt.name) {
            val cs = MaterialTheme.colorScheme
            Box(side.enter(3)) {
                Panel {
                    Label("Последние измерения")
                    recent.forEach { v ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(v.display() + " " + v.type.unit, style = MaterialTheme.typography.titleMedium)
                                Text(v.dateTime.fmt("EE, d MMM, HH:mm").cap(), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                            IconButton(onClick = { onDelete(v) }) { Icon(Icons.Rounded.Close, contentDescription = "Удалить", tint = cs.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------- календарь

private fun LazyListScope.calendarItems(
    state: AppState,
    now: LocalDateTime,
    monthOff: Int,
    onMonth: (Int) -> Unit,
    selDay: String?,
    onSelect: (String?) -> Unit,
    onDeleteEntry: (Entry) -> Unit,
) {
    val side = Modifier.padding(horizontal = 16.dp)
    val today = now.toLocalDate()
    val month = YearMonth.from(today).plusMonths(monthOff.toLong())

    item(key = "c-grid-" + month.toString()) {
        val ex = LocalExtra.current
        val cs = MaterialTheme.colorScheme
        Box(side.enter(0)) {
            Panel {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { onMonth(monthOff - 1) }) { Icon(Icons.Rounded.KeyboardArrowLeft, contentDescription = "Предыдущий месяц") }
                    Text(month.atDay(1).fmt("LLLL yyyy").cap(), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { if (monthOff < 0) onMonth(monthOff + 1) }, enabled = monthOff < 0) {
                        Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = "Следующий месяц")
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach {
                        Text(it, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
                val lead = month.atDay(1).dayOfWeek.value - 1
                val len = month.lengthOfMonth()
                val rows = (lead + len + 6) / 7
                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth()) {
                        for (c in 0..6) {
                            val day = r * 7 + c - lead + 1
                            if (day < 1 || day > len) {
                                Box(Modifier.weight(1f).aspectRatio(1f))
                            } else {
                                val d = month.atDay(day)
                                val sev = state.maxSevOn(d)
                                val bg = when {
                                    sev == null -> Color.White.copy(alpha = 0.05f)
                                    sev == 0 -> ex.s0.copy(alpha = 0.35f)
                                    else -> ex.sev(sev).copy(alpha = 0.30f + 0.04f * sev)
                                }
                                val isToday = d == today
                                val isSel = selDay == d.toString()
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(2.5.dp)
                                        .enter(day / 2)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(bg)
                                        .border(
                                            if (isSel) 2.dp else if (isToday) 1.5.dp else 0.dp,
                                            if (isSel) Color.White else ex.mint,
                                            RoundedCornerShape(14.dp),
                                        )
                                        .clickable { onSelect(if (isSel) null else d.toString()) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(day.toString(), fontSize = 14.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf("жалоб нет" to ex.s0, "слабая" to ex.s1, "умеренная" to ex.s2, "сильная" to ex.s3).forEach { (name, col) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(9.dp).clip(CircleShape).background(col))
                            Text(name, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    val sd = selDay?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    if (sd != null) {
        item(key = "c-day-" + sd.toString()) {
            val cs = MaterialTheme.colorScheme
            val es = state.entries.filter { it.date == sd }.sortedBy { it.ts }
            val vs = state.vitals.filter { it.date == sd }.sortedBy { it.ts }
            val mood = state.moods[sd.toString()]
            Column(side.enter(0), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Label(sd.fmt("EEEE, d MMMM").cap())
                if (es.isEmpty() && vs.isEmpty() && mood == null) {
                    Panel { Text("В этот день записей нет.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant) }
                }
                if (mood != null || vs.isNotEmpty()) {
                    Panel {
                        if (mood != null) Text("Настроение: " + moodWord(mood).lowercase(), style = MaterialTheme.typography.bodyLarge)
                        vs.forEach { v ->
                            Text(v.type.ru + ": " + v.display() + " " + v.type.unit + " в " + v.dateTime.fmt("HH:mm"), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                es.forEach { e -> EntryCard(e, onDelete = { onDeleteEntry(e) }) }
            }
        }
    }
}

// ------------------------------------------------------- добавить измерение

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VitalSheet(onDismiss: () -> Unit, onSave: (Vital) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var type by remember { mutableStateOf(VitalType.PRESSURE) }
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var time by remember { mutableStateOf(LocalTime.now().withSecond(0).withNano(0)) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    fun num(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()
    val av = num(a)
    val bv = num(b)
    val ok = when (type) {
        VitalType.PRESSURE -> av != null && bv != null && av in 50.0..300.0 && bv in 30.0..200.0
        VitalType.PULSE -> av != null && av in 20.0..250.0
        VitalType.TEMP -> av != null && av in 30.0..43.0
        VitalType.WEIGHT -> av != null && av in 20.0..400.0
    }
    val round = RoundedCornerShape(16.dp)
    val kb = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = cs.surface) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Новое измерение", style = MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VitalType.values().forEach { t ->
                    FilterChip(selected = type == t, onClick = { type = t; a = ""; b = "" }, label = { Text(t.ru) })
                }
            }
            if (type == VitalType.PRESSURE) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(a, { a = it }, Modifier.weight(1f), label = { Text("Верхнее") }, singleLine = true, shape = round, keyboardOptions = kb)
                    OutlinedTextField(b, { b = it }, Modifier.weight(1f), label = { Text("Нижнее") }, singleLine = true, shape = round, keyboardOptions = kb)
                }
            } else {
                OutlinedTextField(
                    a, { a = it }, Modifier.fillMaxWidth(),
                    label = { Text(type.ru + ", " + type.unit) }, singleLine = true, shape = round, keyboardOptions = kb,
                )
            }
            if ((a.isNotBlank() || b.isNotBlank()) && !ok) {
                Text("Проверьте значения: они выглядят невозможными.", style = MaterialTheme.typography.bodySmall, color = cs.error)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { showDate = true }, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(14.dp)) {
                    Text(dayLabel(date).cap())
                }
                OutlinedButton(onClick = { showTime = true }, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(14.dp)) {
                    Text(time.toString())
                }
            }
            GradientButton(
                "Сохранить",
                onClick = {
                    val ts = LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    onSave(Vital(Repo.newId(), ts, type, av ?: 0.0, if (type == VitalType.PRESSURE) (bv ?: 0.0) else 0.0))
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = ok,
            )
        }
    }
    if (showDate) AppDatePicker(date, onDismiss = { showDate = false }, onPick = { date = it })
    if (showTime) AppTimePicker(time, onDismiss = { showTime = false }, onPick = { time = it })
}
