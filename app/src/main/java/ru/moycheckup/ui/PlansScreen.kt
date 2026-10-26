package ru.moycheckup.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.moycheckup.data.AppState
import ru.moycheckup.data.Occurrence
import ru.moycheckup.data.RemType
import ru.moycheckup.data.Reminder
import ru.moycheckup.data.Repeat
import ru.moycheckup.data.Repo
import ru.moycheckup.data.isDone
import ru.moycheckup.data.nextDate
import ru.moycheckup.data.occurrences
import ru.moycheckup.notify.Notifier
import ru.moycheckup.ui.theme.LocalExtra
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Composable
fun PlansScreen(
    state: AppState,
    now: LocalDateTime,
    bottomPad: Dp,
    onToggle: (Occurrence) -> Unit,
    onAdd: (Reminder) -> Unit,
    onDelete: (Reminder) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ex = LocalExtra.current
    val ctx = LocalContext.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val today = now.toLocalDate()
    val canNotify = Notifier.canNotify(ctx)
    val canExact = Notifier.canExact(ctx)
    val occ = state.occurrences(today)
    var sheet by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Reminder?>(null) }
    val all = state.reminders
        .map { it to state.nextDate(it, today) }
        .sortedBy { it.second ?: LocalDate.MAX }
    val side = Modifier.padding(horizontal = 16.dp)

    LazyColumn(
        contentPadding = PaddingValues(top = top + 16.dp, bottom = bottomPad + 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenTitle("Планы") {
                Button(onClick = { sheet = true }, shape = RoundedCornerShape(14.dp)) { Text("Добавить", fontWeight = FontWeight.SemiBold) }
            }
        }
        if (!canNotify || !canExact) {
            item {
                Box(side) {
                    Panel {
                        Label("Нужно разрешение")
                        Text(
                            if (!canNotify) "Без разрешения на уведомления напоминания не придут."
                            else "Чтобы уведомления приходили минута в минуту, разрешите приложению будильники и напоминания.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = {
                                val i = if (!canNotify) {
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                } else if (Build.VERSION.SDK_INT >= 31) {
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName))
                                } else {
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                                }
                                ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            },
                            shape = RoundedCornerShape(14.dp),
                        ) { Text("Открыть настройки") }
                    }
                }
            }
        }
        item {
            Box(side) {
                Panel {
                    Label("Сегодня")
                    if (occ.isEmpty()) {
                        Text("На сегодня ничего нет.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
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
            Box(side) {
                Panel {
                    Label("Все напоминания")
                    if (all.isEmpty()) {
                        Text(
                            "Здесь будут таблетки, визиты к врачу и анализы. Нажмите «Добавить», и телефон напомнит вовремя.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                        )
                    }
                    all.forEach { (r, next) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(ex.type(r.type)))
                            Column(Modifier.weight(1f)) {
                                Text(r.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    r.type.ru + " · " + r.repeat.ru + " · " + r.time + " · " +
                                        (if (next != null) "ближайшее: " + dayLabel(next, today) else "прошло"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant,
                                )
                                val extra = listOf(r.dose, r.how, r.place).filter { it.isNotBlank() }.joinToString(" · ")
                                if (extra.isNotEmpty()) Text(extra, style = MaterialTheme.typography.bodySmall)
                                if (r.until != null) Text("Курс до " + r.until.fmt("d MMMM"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                if (r.note.isNotBlank()) Text(r.note, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { toDelete = r }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Удалить", tint = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        item {
            Text(
                "Таблетки: уведомление за ${Notifier.PRE_MINUTES} минут и ровно в назначенное время. Врачи и анализы: накануне в ${Notifier.EVE_HOUR}:00 и в день визита за ${Notifier.DAY_HOURS_BEFORE} ч. Уведомления приходят, даже когда приложение закрыто.",
                modifier = side,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
    }

    if (sheet) {
        AddReminderSheet(onDismiss = { sheet = false }, onSave = { onAdd(it); sheet = false })
    }
    val del = toDelete
    if (del != null) {
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить напоминание?") },
            text = { Text(del.title) },
            confirmButton = { TextButton(onClick = { onDelete(del); toDelete = null }) { Text("Удалить", color = cs.error) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddReminderSheet(onDismiss: () -> Unit, onSave: (Reminder) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var type by remember { mutableStateOf(RemType.PILL) }
    var title by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("") }
    val how = remember { mutableStateListOf<String>() }
    var place by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf(Repeat.DAILY) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var until by remember { mutableStateOf<LocalDate?>(null) }
    var time by remember { mutableStateOf(LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showUntil by remember { mutableStateOf(false) }

    val field = Modifier.fillMaxWidth()
    val round = RoundedCornerShape(16.dp)

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
            Text("Новое напоминание", style = MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RemType.values().forEach { t ->
                    FilterChip(
                        selected = type == t,
                        onClick = {
                            type = t
                            repeat = if (t == RemType.PILL) Repeat.DAILY else Repeat.NONE
                        },
                        label = { Text(t.ru) },
                    )
                }
            }
            Text(Notifier.describe(type), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = {
                    Text(
                        when (type) {
                            RemType.PILL -> "Название лекарства"
                            RemType.DOC -> "К какому врачу"
                            RemType.LAB -> "Какой анализ"
                            RemType.OTHER -> "Что напомнить"
                        }
                    )
                },
                singleLine = true,
                shape = round,
                modifier = field,
            )

            if (type == RemType.PILL) {
                OutlinedTextField(
                    value = dose,
                    onValueChange = { dose = it },
                    label = { Text("Дозировка") },
                    placeholder = { Text("1 таблетка, 500 мг") },
                    singleLine = true,
                    shape = round,
                    modifier = field,
                )
                Label("Как принимать")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HOW.forEach { h ->
                        FilterChip(
                            selected = h in how,
                            onClick = { if (h in how) how.remove(h) else how.add(h) },
                            label = { Text(h) },
                        )
                    }
                }
            }
            if (type == RemType.DOC || type == RemType.LAB) {
                OutlinedTextField(
                    value = place,
                    onValueChange = { place = it },
                    label = { Text("Где: клиника, кабинет") },
                    singleLine = true,
                    shape = round,
                    modifier = field,
                )
            }

            Label(if (type == RemType.PILL) "Начало приёма" else "Дата и время")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { showDate = true }, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(14.dp)) {
                    Text(date.fmt("d MMM yyyy"))
                }
                OutlinedButton(onClick = { showTime = true }, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(14.dp)) {
                    Text(time.toString())
                }
            }

            Label("Повторять")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Repeat.values().forEach { r ->
                    FilterChip(selected = repeat == r, onClick = { repeat = r }, label = { Text(r.ru) })
                }
            }
            if (type == RemType.PILL && repeat != Repeat.NONE) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { showUntil = true }, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(14.dp)) {
                        Text(if (until == null) "Курс: без конца" else "Курс до " + until!!.fmt("d MMM yyyy"))
                    }
                    if (until != null) {
                        TextButton(onClick = { until = null }) { Text("Сбросить") }
                    }
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = {
                    Text(
                        when (type) {
                            RemType.PILL -> "Заметка: как пить, на что обратить внимание"
                            RemType.DOC -> "Заметка: о чём спросить, что взять"
                            RemType.LAB -> "Заметка: подготовка, что взять"
                            RemType.OTHER -> "Заметка"
                        }
                    )
                },
                minLines = 3,
                shape = round,
                modifier = field,
            )
            Button(
                onClick = {
                    onSave(
                        Reminder(
                            id = Repo.newId(), title = title.trim(), type = type, date = date, time = time,
                            repeat = repeat, note = note.trim(),
                            dose = dose.trim(), how = how.joinToString(", "), place = place.trim(), until = until,
                        )
                    )
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Сохранить", fontWeight = FontWeight.SemiBold) }
        }
    }
    if (showDate) AppDatePicker(date, onDismiss = { showDate = false }, onPick = { date = it })
    if (showTime) AppTimePicker(time, onDismiss = { showTime = false }, onPick = { time = it })
    if (showUntil) AppDatePicker(until ?: date, onDismiss = { showUntil = false }, onPick = { until = it })
}
