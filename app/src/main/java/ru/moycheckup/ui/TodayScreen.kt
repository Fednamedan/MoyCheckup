package ru.moycheckup.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.moycheckup.data.AppState
import ru.moycheckup.data.Entry
import ru.moycheckup.data.Occurrence
import ru.moycheckup.data.VitalType
import ru.moycheckup.data.insights
import ru.moycheckup.data.isDone
import ru.moycheckup.data.occurrences
import ru.moycheckup.data.streak
import ru.moycheckup.data.weekStats
import ru.moycheckup.data.wellness
import ru.moycheckup.data.wellnessWord
import ru.moycheckup.ui.theme.LocalExtra
import java.time.LocalDateTime

/** Крупная кнопка с переливающимся градиентом. */
@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val ex = LocalExtra.current
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .height(56.dp)
            .clip(shape)
            .background(
                if (enabled) Brush.horizontalGradient(listOf(ex.mint, ex.blue, ex.violet))
                else Brush.horizontalGradient(listOf(Color(0x33FFFFFF), Color(0x22FFFFFF)))
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) Color(0xFF03141F) else Color(0x80FFFFFF),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
    }
}

/** Стеклянная кнопка с рамкой. */
@Composable
fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .height(56.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.28f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
fun TodayScreen(
    state: AppState,
    now: LocalDateTime,
    bottomPad: Dp,
    onLog: () -> Unit,
    onGood: () -> Unit,
    onToggle: (Occurrence) -> Unit,
    onDeleteEntry: (Entry) -> Unit,
    onOpenPlans: () -> Unit,
    onMood: (Int) -> Unit,
    onAddVital: () -> Unit,
    onOpenHealth: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ex = LocalExtra.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val today = now.toLocalDate()
    val todays = state.entries.filter { it.date == today }.sortedByDescending { it.ts }
    val pain = todays.filter { it.sev > 0 }
    val occ = state.occurrences(today)
    val late = occ.filter { !state.isDone(it) && !it.r.time.isAfter(now.toLocalTime()) }
    val week = state.weekStats(0, now)
    val score = state.wellness(now)
    val streak = state.streak(today)
    val insights = state.insights(now)
    val mood = state.moods[today.toString()]

    val places = pain.flatMap { it.zones }.distinctBy { it.label }.size
    val headline = when {
        todays.isEmpty() -> "Как вы себя чувствуете?"
        pain.isEmpty() -> "Сегодня без жалоб"
        else -> "Болит в $places " + (if (places % 10 == 1 && places % 100 != 11) "месте" else "местах")
    }
    val worst = pain.maxByOrNull { it.sev }
    val sub = when {
        worst != null -> "Сильнее всего: " + worst.zones.joinToString(", ") { it.label } + ", ${worst.sev} из 10"
        todays.isEmpty() -> "Отметьте, что болит, или скажите, что всё в порядке."
        else -> "Отлично, запись есть."
    }
    val side = Modifier.padding(horizontal = 16.dp)

    LazyColumn(
        contentPadding = PaddingValues(top = top + 12.dp, bottom = bottomPad),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).enter(0),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        "Мой чекап",
                        style = TextStyle(
                            brush = Brush.horizontalGradient(listOf(ex.mint, ex.blue, ex.violet)),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(now.fmt("EEEE, d MMMM").cap(), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                if (streak > 0) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, ex.pink.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Rounded.Favorite, contentDescription = null, tint = ex.pink, modifier = Modifier.size(16.dp))
                        Text("$streak " + plural(streak, "день", "дня", "дней") + " подряд", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        item {
            Box(side.enter(1)) {
                Panel(padding = 20.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        WellnessRing(score, wellnessWord(score), Modifier.size(150.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Label(greeting(now.hour))
                            Text(headline, style = MaterialTheme.typography.titleLarge)
                            Text(sub, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        }
                    }
                    EcgLine(Modifier.fillMaxWidth().height(44.dp), ex.mint)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GradientButton("Записать, что болит", onLog, Modifier.weight(1f))
                        GlassButton("Жалоб нет", onGood)
                    }
                }
            }
        }

        if (late.isNotEmpty()) {
            item {
                val t = rememberInfiniteTransition(label = "late")
                val glow by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "glow")
                Row(
                    side
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(ex.pink.copy(alpha = 0.10f + 0.08f * glow))
                        .border(1.dp, ex.pink.copy(alpha = 0.35f + 0.4f * glow), RoundedCornerShape(24.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Canvas(Modifier.size(14.dp)) {
                        drawCircle(ex.pink.copy(alpha = 0.3f * glow), radius = size.minDimension / 2f)
                        drawCircle(ex.pink, radius = size.minDimension / 4f)
                    }
                    Column {
                        Text("Пора: " + late.first().r.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            late.first().r.time.toString() + (if (late.size > 1) " · и ещё ${late.size - 1}" else "") + ". Отметьте ниже, когда сделаете.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            Box(side.enter(2)) {
                Panel {
                    Label("Настроение сегодня")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        for (lvl in 1..5) {
                            Box(Modifier.size(54.dp).clickable { onMood(lvl) }, contentAlignment = Alignment.Center) {
                                MoodFace(lvl, mood == lvl, Modifier.size(44.dp))
                            }
                        }
                    }
                    Text(moodWord(mood), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
            }
        }

        item {
            Box(side.enter(3)) {
                Panel {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Label("Показатели")
                        TextButton(onClick = onAddVital) { Text("Добавить", color = ex.mint, fontWeight = FontWeight.SemiBold) }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        VitalType.values().forEach { vt ->
                            val last = state.vitals.filter { it.type == vt }.maxByOrNull { it.ts }
                            Column(
                                Modifier
                                    .width(128.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                                    .clickable(onClick = onOpenHealth)
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(vt.ru, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                Text(last?.display() ?: "—", fontSize = 24.sp, fontWeight = FontWeight.Light)
                                Text(vt.unit, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        if (insights.isNotEmpty()) {
            item {
                Column(Modifier.enter(4), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Label("Наблюдения", Modifier.padding(horizontal = 20.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        insights.forEach { i ->
                            Panel(Modifier.width(250.dp)) {
                                Text(i.title, style = MaterialTheme.typography.labelLarge, color = ex.mint)
                                Text(i.text, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        }

        item {
            Box(side.enter(5)) {
                Panel {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Label("В планах сегодня")
                        TextButton(onClick = onOpenPlans) { Text("Все", color = ex.mint) }
                    }
                    if (occ.isEmpty()) {
                        Text(
                            "На сегодня ничего нет. Таблетки и визиты добавляются во вкладке «Планы».",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                        )
                    } else {
                        occ.forEach { o ->
                            val done = state.isDone(o)
                            OccRow(o, done, late = !done && !o.r.time.isAfter(now.toLocalTime()), showDay = false) { onToggle(o) }
                        }
                    }
                }
            }
        }

        item {
            Box(side.enter(6)) {
                Panel {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Label("Эта неделя на теле")
                        Text(
                            week.pain.size.toString() + " " + plural(week.pain.size, "запись", "записи", "записей"),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            BodyMap(true, emptySet(), 0, week.heat, Modifier.height(220.dp))
                            Label("спереди")
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            BodyMap(false, emptySet(), 0, week.heat, Modifier.height(220.dp))
                            Label("сзади")
                        }
                    }
                    if (week.pain.isEmpty()) {
                        Text("Пока нет записей о боли. Когда они появятся, зоны засветятся цветом силы боли.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
            }
        }

        if (todays.isNotEmpty()) {
            item { Label("Записи за сегодня", Modifier.padding(horizontal = 20.dp)) }
            items(todays, key = { it.id }) { e ->
                Box(side) { EntryCard(e, onDelete = { onDeleteEntry(e) }) }
            }
        }
    }
}
