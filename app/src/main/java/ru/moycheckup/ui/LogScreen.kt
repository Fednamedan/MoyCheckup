package ru.moycheckup.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.moycheckup.data.Body
import ru.moycheckup.data.Entry
import ru.moycheckup.data.Repo
import ru.moycheckup.data.Zone
import ru.moycheckup.data.ZoneRef
import ru.moycheckup.ui.theme.LocalExtra
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

private const val VIEW_FRONT = 0
private const val VIEW_BACK = 1
private const val VIEW_HEAD = 2

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogScreen(onClose: () -> Unit, onSave: (Entry) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ex = LocalExtra.current
    val haptic = LocalHapticFeedback.current

    var view by remember { mutableIntStateOf(VIEW_FRONT) }
    val sel = remember { mutableStateMapOf<String, String>() }
    var sev by remember { mutableIntStateOf(4) }
    val kinds = remember { mutableStateListOf<String>() }
    val triggers = remember { mutableStateListOf<String>() }
    val extras = remember { mutableStateListOf<String>() }
    var since by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var time by remember { mutableStateOf(LocalTime.now().withSecond(0).withNano(0)) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    val sevColor by animateColorAsState(ex.sev(sev), tween(200), label = "sev")

    fun toggleZone(z: Zone) {
        if (sel.containsKey(z.id)) {
            sel.remove(z.id)
        } else {
            sel[z.id] = z.label
        }
    }

    fun onBodyTap(z: Zone) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        // Нажатие на голову открывает подробную схему головы.
        if (Body.isHeadZone(z)) view = VIEW_HEAD else toggleZone(z)
    }

    val redFlag by remember {
        derivedStateOf {
            sev >= 9 || (extras.any { it == "Одышка" || it == "Онемение" } &&
                sel.values.any { it == "Грудь" || it in Body.headLabels })
        }
    }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Что болит?", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Закрыть") }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Panel {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        listOf(VIEW_FRONT to "Спереди", VIEW_BACK to "Сзади", VIEW_HEAD to "Голова").forEach { (v, name) ->
                            val on = view == v
                            val bg by animateColorAsState(if (on) Color.White.copy(alpha = 0.16f) else Color.Transparent, tween(160), label = "seg")
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(bg)
                                    .clickable { view = v }
                                    .padding(vertical = 11.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    name,
                                    color = if (on) cs.primary else cs.onSurfaceVariant,
                                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                                )
                            }
                        }
                    }

                    if (view == VIEW_HEAD) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                HeadMap(true, sel.keys.toSet(), sev, emptyMap(), Modifier.fillMaxWidth(), onTap = { z ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    toggleZone(z)
                                }, scan = true)
                                Label("лицо")
                            }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                HeadMap(false, sel.keys.toSet(), sev, emptyMap(), Modifier.fillMaxWidth(), onTap = { z ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    toggleZone(z)
                                }, scan = true)
                                Label("затылок")
                            }
                        }
                        Text(
                            "Выберите, какая именно часть головы болит: лоб, висок, глаза, затылок и так далее.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                        )
                    } else {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            BodyMap(
                                front = view == VIEW_FRONT,
                                selected = sel.keys.toSet(),
                                sev = sev,
                                heat = emptyMap(),
                                modifier = Modifier.height(380.dp),
                                onTap = { z -> onBodyTap(z) },
                                scan = true,
                            )
                        }
                        Text(
                            if (sel.isEmpty()) "Нажмите на тело там, где болит. Можно выбрать несколько мест. Нажмите на голову, чтобы выбрать её часть."
                            else "Нажмите на голову, чтобы выбрать её часть.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                        )
                    }

                    if (sel.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            sel.keys.toList().forEach { id ->
                                GlowChip((sel[id] ?: "") + "  ×", true) { sel.remove(id) }
                            }
                        }
                    }
                }

                Panel {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        SeverityOrb(sev, sevColor)
                        Column(Modifier.weight(1f)) {
                            Label("Сила боли")
                            Text(sevWord(sev), style = MaterialTheme.typography.titleLarge, color = sevColor)
                        }
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(sev.toString(), color = sevColor, fontSize = 44.sp, fontWeight = FontWeight.Bold, lineHeight = 46.sp)
                            Text("/10", color = cs.onSurfaceVariant, fontSize = 16.sp, modifier = Modifier.padding(bottom = 6.dp))
                        }
                    }
                    Slider(
                        value = sev.toFloat(),
                        onValueChange = {
                            val n = it.toInt().coerceIn(1, 10)
                            if (n != sev) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            sev = n
                        },
                        valueRange = 1f..10f,
                        steps = 8,
                        colors = SliderDefaults.colors(thumbColor = sevColor, activeTrackColor = sevColor, inactiveTrackColor = Color.White.copy(alpha = 0.14f)),
                    )
                }

                AnimatedVisibility(
                    visible = redFlag,
                    enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                    exit = fadeOut(tween(200)) + shrinkVertically(tween(200)),
                ) {
                    RedFlag()
                }

                Panel {
                    Label("Какая боль")
                    ChipGroup(KINDS, { it in kinds }) { if (it in kinds) kinds.remove(it) else kinds.add(it) }
                    Label("Когда проявляется")
                    ChipGroup(TRIGGERS, { it in triggers }) { if (it in triggers) triggers.remove(it) else triggers.add(it) }
                    Label("Что ещё беспокоит")
                    ChipGroup(EXTRAS, { it in extras }) { if (it in extras) extras.remove(it) else extras.add(it) }
                    Label("Как давно")
                    ChipGroup(SINCE, { it == since }) { since = if (since == it) "" else it }
                }

                Panel {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Описание своими словами") },
                        placeholder = { Text("Что было до этого, что помогло, что принимали, что сказать врачу") },
                        minLines = 3,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Label("Когда")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { showDate = true }, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(14.dp)) {
                            Text(dayLabel(date).cap())
                        }
                        OutlinedButton(onClick = { showTime = true }, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(14.dp)) {
                            Text(time.toString())
                        }
                    }
                }
            }

            Box(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp)) {
                GradientButton(
                    text = "Сохранить запись",
                    enabled = sel.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val ts = LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val zones = sel.entries.map { ZoneRef(it.key, it.value) }
                        onSave(
                            Entry(
                                id = Repo.newId(), ts = ts, zones = zones, sev = sev,
                                kinds = kinds.toList(), note = note.trim(),
                                triggers = triggers.toList(), extras = extras.toList(), since = since,
                            )
                        )
                    },
                )
            }
        }
    }
    if (showDate) AppDatePicker(date, onDismiss = { showDate = false }, onPick = { date = it })
    if (showTime) AppTimePicker(time, onDismiss = { showTime = false }, onPick = { time = it })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(options: List<String>, isOn: (String) -> Boolean, onToggle: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            GlowChip(o, isOn(o)) { onToggle(o) }
        }
    }
}

@Composable
private fun GlowChip(text: String, on: Boolean, onClick: () -> Unit) {
    val ex = LocalExtra.current
    val bg by animateColorAsState(if (on) ex.mint.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f), tween(180), label = "chipbg")
    val line by animateColorAsState(if (on) ex.mint else Color.White.copy(alpha = 0.18f), tween(180), label = "chipline")
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(text, color = if (on) Color.White else Color(0xFFC3CDE4), fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
    }
}

/** Шар: чем сильнее боль, тем он больше и тем быстрее пульсирует. */
@Composable
private fun SeverityOrb(sev: Int, color: Color) {
    val ms = (1500 - sev * 105).coerceAtLeast(380)
    val t = rememberInfiniteTransition(label = "orb")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(ms, easing = LinearEasing), RepeatMode.Reverse), label = "orbp")
    Canvas(Modifier.size(72.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        val core = r * (0.30f + sev * 0.035f) * (0.92f + 0.10f * p)
        drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = 0.55f), Color.Transparent), center = c, radius = r),
            radius = r, center = c,
        )
        drawCircle(color.copy(alpha = 0.35f * (1f - p)), radius = core + r * 0.35f * p, center = c)
        drawCircle(
            Brush.radialGradient(listOf(Color.White, color), center = Offset(c.x - core * 0.3f, c.y - core * 0.3f), radius = core * 1.4f),
            radius = core, center = c,
        )
    }
}

@Composable
private fun RedFlag() {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFFFF3B5C).copy(alpha = 0.16f))
            .border(1.dp, Color(0xFFFF3B5C).copy(alpha = 0.7f), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Не откладывайте обращение к врачу", color = Color(0xFFFF8CA0), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(
            "Нестерпимая боль, одышка или онемение могут быть признаком серьёзного состояния. Если состояние резко ухудшается, позвоните 103 или 112. Приложение не заменяет врача.",
            color = Color(0xFFE6EAF5),
            fontSize = 14.sp,
        )
    }
}
