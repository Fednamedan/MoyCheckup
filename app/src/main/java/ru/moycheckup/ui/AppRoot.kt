package ru.moycheckup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import ru.moycheckup.data.Entry
import ru.moycheckup.data.Occurrence
import ru.moycheckup.data.Reminder
import ru.moycheckup.data.Repo
import ru.moycheckup.data.Vital
import ru.moycheckup.data.isDone
import ru.moycheckup.data.occurrences
import ru.moycheckup.notify.Notifier
import ru.moycheckup.ui.theme.LocalExtra
import java.time.LocalDateTime

@Composable
fun AppRoot(requestNotifications: () -> Unit) {
    val ctx = LocalContext.current
    val state by Repo.state.collectAsStateWithLifecycle()
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            delay(30_000)
            value = LocalDateTime.now()
        }
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var logging by rememberSaveable { mutableStateOf(false) }
    var splash by rememberSaveable { mutableStateOf(true) }
    var vitalSheet by rememberSaveable { mutableStateOf(false) }
    var confetti by remember { mutableIntStateOf(0) }
    BackHandler(enabled = logging) { logging = false }

    // Салют, когда за сегодня выполнено всё запланированное.
    val todayOcc = state.occurrences(now.toLocalDate())
    val allDone = todayOcc.isNotEmpty() && todayOcc.all { state.isDone(it) }
    var prevAllDone by remember { mutableStateOf(allDone) }
    LaunchedEffect(allDone) {
        if (allDone && !prevAllDone) confetti++
        prevAllDone = allDone
    }

    fun toggleDone(o: Occurrence) = Repo.update { s ->
        s.copy(done = if (o.key in s.done) s.done - o.key else s.done + o.key)
    }
    fun deleteEntry(e: Entry) = Repo.update { s -> s.copy(entries = s.entries.filter { it.id != e.id }) }
    fun addEntry(e: Entry) = Repo.update { s -> s.copy(entries = s.entries + e) }
    fun addVital(v: Vital) = Repo.update { s -> s.copy(vitals = s.vitals + v) }
    fun deleteVital(v: Vital) = Repo.update { s -> s.copy(vitals = s.vitals.filter { it.id != v.id }) }
    fun setMood(level: Int) = Repo.update { s -> s.copy(moods = s.moods + (now.toLocalDate().toString() to level)) }
    fun addReminder(r: Reminder) {
        Repo.update { s -> s.copy(reminders = s.reminders + r) }
        Notifier.scheduleAll(ctx, r)
        if (!Notifier.canNotify(ctx)) requestNotifications()
    }
    fun deleteReminder(r: Reminder) {
        Repo.update { s -> s.copy(reminders = s.reminders.filter { it.id != r.id }) }
        Notifier.cancelAll(ctx, r)
    }

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottom = navInset + 112.dp

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()

        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (fadeIn(tween(380)) + slideInVertically(tween(380)) { it / 24 }) togetherWith fadeOut(tween(160))
            },
            label = "tabs",
        ) { t ->
            when (t) {
                0 -> TodayScreen(
                    state = state, now = now, bottomPad = bottom,
                    onLog = { logging = true },
                    onGood = {
                        addEntry(Entry(Repo.newId(), System.currentTimeMillis(), emptyList(), 0, emptyList(), ""))
                        confetti++
                    },
                    onToggle = { toggleDone(it) },
                    onDeleteEntry = { deleteEntry(it) },
                    onOpenPlans = { tab = 2 },
                    onMood = { setMood(it) },
                    onAddVital = { vitalSheet = true },
                    onOpenHealth = { tab = 3 },
                )
                1 -> DiaryScreen(state, bottom, onAdd = { logging = true }, onDelete = { deleteEntry(it) })
                2 -> PlansScreen(state, now, bottom, onToggle = { toggleDone(it) }, onAdd = { addReminder(it) }, onDelete = { deleteReminder(it) })
                else -> HealthScreen(state, now, bottom, onAddVital = { vitalSheet = true }, onDeleteVital = { deleteVital(it) }, onDeleteEntry = { deleteEntry(it) })
            }
        }

        if (!logging) {
            Box(Modifier.align(Alignment.BottomCenter)) {
                FloatingNav(tab, onTab = { tab = it }, onAdd = { logging = true })
            }
        }

        AnimatedVisibility(
            visible = logging,
            enter = slideInVertically(tween(420)) { it / 2 } + fadeIn(tween(420)),
            exit = slideOutVertically(tween(300)) { it / 2 } + fadeOut(tween(300)),
        ) {
            LogScreen(onClose = { logging = false }, onSave = { addEntry(it); logging = false; tab = 0 })
        }

        if (vitalSheet) {
            VitalSheet(onDismiss = { vitalSheet = false }, onSave = { addVital(it); vitalSheet = false })
        }

        Confetti(confetti)

        AnimatedVisibility(visible = splash, exit = fadeOut(tween(500))) {
            Splash { splash = false }
        }
    }
}

@Composable
private fun FloatingNav(tab: Int, onTab: (Int) -> Unit, onAdd: () -> Unit) {
    val ex = LocalExtra.current
    val pill = RoundedCornerShape(36.dp)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        val slotW = maxWidth / 5
        val slotIndex = if (tab < 2) tab else tab + 1
        val x by animateDpAsState(slotW * slotIndex, spring(dampingRatio = 0.68f, stiffness = 380f), label = "navx")
        Box(Modifier.fillMaxWidth().height(72.dp)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(pill)
                    .background(Color(0xEB0B1230))
                    .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.16f))), pill),
            )
            Box(
                Modifier
                    .offset(x = x)
                    .width(slotW)
                    .fillMaxHeight()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(ex.mint.copy(alpha = 0.22f), ex.violet.copy(alpha = 0.22f))))
                    .border(1.dp, ex.mint.copy(alpha = 0.35f), RoundedCornerShape(28.dp)),
            )
            Row(Modifier.fillMaxSize()) {
                NavItem(Icons.Rounded.Home, "Сегодня", tab == 0, Modifier.weight(1f)) { onTab(0) }
                NavItem(Icons.Rounded.List, "Дневник", tab == 1, Modifier.weight(1f)) { onTab(1) }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { AddButton(onAdd) }
                NavItem(Icons.Rounded.Notifications, "Планы", tab == 2, Modifier.weight(1f)) { onTab(2) }
                NavItem(Icons.Rounded.Favorite, "Здоровье", tab == 3, Modifier.weight(1f)) { onTab(3) }
            }
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ex = LocalExtra.current
    val tint by animateColorAsState(if (selected) ex.mint else Color(0xFF8B98B8), tween(250), label = "tint")
    val sc by animateFloatAsState(if (selected) 1.18f else 1f, spring(dampingRatio = 0.45f, stiffness = 380f), label = "sc")
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp).graphicsLayer { scaleX = sc; scaleY = sc })
        Text(label, color = tint, fontSize = 10.5.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
    }
}

/** Центральная кнопка «+» с вращающимся светящимся кольцом. */
@Composable
private fun AddButton(onClick: () -> Unit) {
    val ex = LocalExtra.current
    val t = rememberInfiniteTransition(label = "fab")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "spin")
    val glow by t.animateFloat(0.5f, 1f, infiniteRepeatable(tween(1800), androidx.compose.animation.core.RepeatMode.Reverse), label = "glow")
    Box(
        Modifier
            .offset(y = (-10).dp)
            .size(64.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(ex.mint.copy(alpha = 0.18f * glow), radius = size.minDimension / 2f)
            rotate(spin) {
                drawCircle(
                    brush = Brush.sweepGradient(listOf(ex.mint, ex.blue, ex.violet, ex.pink, ex.mint)),
                    radius = size.minDimension / 2f - 2.5f * density,
                    style = Stroke(width = 3f * density, cap = StrokeCap.Round),
                )
            }
        }
        Box(
            Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(ex.mint, ex.blue))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Записать, что болит", tint = Color(0xFF03231C), modifier = Modifier.size(30.dp))
        }
    }
}
