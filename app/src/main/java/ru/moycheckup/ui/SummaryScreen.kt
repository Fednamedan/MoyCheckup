package ru.moycheckup.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.moycheckup.data.AppState
import ru.moycheckup.data.RemType
import ru.moycheckup.data.weekDays
import ru.moycheckup.data.weekStats
import ru.moycheckup.report.PdfReport
import ru.moycheckup.ui.theme.LocalExtra
import java.io.File
import java.time.LocalDateTime

@Composable
fun SummaryScreen(state: AppState, now: LocalDateTime, bottomPad: Dp) {
    val cs = MaterialTheme.colorScheme
    val ex = LocalExtra.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var offset by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val w = state.weekStats(offset, now)
    val side = Modifier.padding(horizontal = 16.dp)

    fun makePdf(then: (File) -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            val f = try {
                withContext(Dispatchers.Default) { PdfReport.build(ctx, state, offset, now) }
            } catch (e: Exception) {
                null
            }
            busy = false
            if (f != null) then(f) else Toast.makeText(ctx, "Не получилось собрать PDF", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(top = top + 16.dp, bottom = bottomPad + 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { ScreenTitle("Сводка недели") }

        item {
            Row(side.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { offset -= 1 }) { Icon(Icons.Rounded.KeyboardArrowLeft, contentDescription = "Предыдущая неделя") }
                Text(weekLabel(w.days), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { if (offset < 0) offset += 1 }, enabled = offset < 0) {
                    Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = "Следующая неделя")
                }
            }
        }

        item {
            Box(side) {
                Panel {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stat(Modifier.weight(1f), w.pain.size.toString(), plural(w.pain.size, "запись", "записи", "записей") + " о боли")
                        Stat(Modifier.weight(1f), "${w.painDays} из 7", "дней с болью")
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stat(Modifier.weight(1f), if (w.pain.isEmpty()) "—" else "%.1f".format(RU, w.avg), "средняя сила, из 10")
                        Stat(Modifier.weight(1f), if (w.pain.isEmpty()) "—" else w.max.toString(), "самая сильная, из 10")
                    }
                }
            }
        }

        item {
            Box(side) {
                Panel {
                    Label("Где болело")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            BodyMap(true, emptySet(), 0, w.heat, Modifier.height(210.dp))
                            Label("спереди")
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            BodyMap(false, emptySet(), 0, w.heat, Modifier.height(210.dp))
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
                                Box(
                                    Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(cs.surfaceVariant)
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth(maxOf(0.08f, z.n.toFloat() / mx))
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(ex.sev(z.max))
                                    )
                                }
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

        item {
            Box(side) {
                Panel {
                    Label("По дням, самая сильная боль")
                    Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        w.perDay.forEach { p ->
                            Column(
                                Modifier.weight(1f).fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    if (p.max > 0) p.max.toString() else if (p.good) "ок" else "",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Box(
                                    Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(cs.surfaceVariant),
                                    contentAlignment = Alignment.BottomCenter,
                                ) {
                                    if (p.max > 0) {
                                        Box(Modifier.fillMaxWidth().fillMaxHeight(p.max / 10f).background(ex.sev(p.max)))
                                    }
                                }
                                Text(p.date.fmt("EE").cap(), fontSize = 12.sp, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        item {
            Box(side) {
                Panel {
                    Label("Лекарства и визиты")
                    val done = w.pillsPast.count { state.done.contains(it.key) }
                    Text(
                        if (w.pillsPast.isEmpty()) "Приёмов таблеток за прошедшие дни нет."
                        else "Таблетки приняты $done из ${w.pillsPast.size}.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
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

        item {
            Box(side) {
                Panel {
                    Label("Файл для врача")
                    Text(
                        "PDF на 1–3 страницы: итоги, схема тела, все записи по порядку, таблетки и визиты, ближайшие планы.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                    Button(
                        onClick = { makePdf { PdfReport.share(ctx, it) } },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text(if (busy) "Собираю PDF…" else "Отправить или сохранить PDF", fontWeight = FontWeight.SemiBold) }
                    OutlinedButton(
                        onClick = { makePdf { PdfReport.open(ctx, it) } },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("Открыть PDF") }
                }
            }
        }
    }
}

@Composable
private fun Stat(modifier: Modifier, value: String, caption: String) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(cs.background).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp)
        Text(caption, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
}
