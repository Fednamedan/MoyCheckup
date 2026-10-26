package ru.moycheckup.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ru.moycheckup.MainActivity
import ru.moycheckup.R
import ru.moycheckup.data.RemType
import ru.moycheckup.data.Reminder
import ru.moycheckup.data.Repo
import ru.moycheckup.data.occursOn
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Виды уведомлений. Таблетка: PRE и MAIN. Врач и анализ: EVE и DAY. Остальное: MAIN. */
enum class Alert(val code: String) {
    /** За 30 минут до приёма таблетки. */
    PRE("pre"),

    /** Ровно в назначенное время. */
    MAIN("main"),

    /** За день до визита, вечером. */
    EVE("eve"),

    /** В день визита, за пару часов. */
    DAY("day");

    companion object {
        fun from(code: String?): Alert? = values().firstOrNull { it.code == code }
    }
}

object Notifier {
    const val CHANNEL = "reminders"

    // Эти значения можно менять: когда приходят уведомления о визитах и таблетках.
    const val PRE_MINUTES = 30L          // таблетка: за сколько минут предупредить
    const val EVE_HOUR = 19              // визит: во сколько накануне напомнить
    const val DAY_HOURS_BEFORE = 2L      // визит: за сколько часов до начала напомнить в тот же день
    const val DAY_EARLIEST_HOUR = 7      // но не раньше этого часа утром

    fun alertsFor(t: RemType): List<Alert> = when (t) {
        RemType.PILL -> listOf(Alert.PRE, Alert.MAIN)
        RemType.DOC, RemType.LAB -> listOf(Alert.EVE, Alert.DAY)
        RemType.OTHER -> listOf(Alert.MAIN)
    }

    /** Короткое описание для экрана добавления напоминания. */
    fun describe(t: RemType): String = when (t) {
        RemType.PILL -> "Придут два уведомления: за $PRE_MINUTES минут и ровно в назначенное время."
        RemType.DOC, RemType.LAB ->
            "Придут два уведомления: накануне в $EVE_HOUR:00 и в день визита за $DAY_HOURS_BEFORE ч до начала."
        RemType.OTHER -> "Уведомление придёт в назначенное время."
    }

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Напоминания", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "Таблетки, визиты к врачу и анализы"
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    /** Во сколько сработает уведомление для приёма или визита, назначенного на occ. */
    fun triggerFor(a: Alert, occ: LocalDateTime): LocalDateTime = when (a) {
        Alert.PRE -> occ.minusMinutes(PRE_MINUTES)
        Alert.MAIN -> occ
        Alert.EVE -> LocalDateTime.of(occ.toLocalDate().minusDays(1), LocalTime.of(EVE_HOUR, 0))
        Alert.DAY -> {
            val earliest = LocalDateTime.of(occ.toLocalDate(), LocalTime.of(DAY_EARLIEST_HOUR, 0))
            val t = occ.minusHours(DAY_HOURS_BEFORE)
            when {
                !t.isBefore(earliest) -> t
                earliest.isBefore(occ) -> earliest
                else -> occ.minusMinutes(30)
            }
        }
    }

    /** Ближайшее будущее срабатывание и дата приёма или визита, к которому оно относится. */
    fun next(r: Reminder, a: Alert, now: LocalDateTime = LocalDateTime.now()): Pair<LocalDateTime, LocalDate>? {
        val start = now.toLocalDate()
        for (i in 0..400) {
            val d = start.plusDays(i.toLong())
            if (!r.occursOn(d)) continue
            val t = triggerFor(a, LocalDateTime.of(d, r.time))
            if (t.isAfter(now)) return Pair(t, d)
        }
        return null
    }

    private fun key(id: String, a: Alert): String = id + "#" + a.code

    private fun pending(ctx: Context, id: String, a: Alert, date: String): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java)
            .putExtra("id", id)
            .putExtra("kind", a.code)
            .putExtra("date", date)
        return PendingIntent.getBroadcast(
            ctx, key(id, a).hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun canExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun schedule(ctx: Context, r: Reminder, a: Alert) {
        val n = next(r, a) ?: return
        val millis = n.first.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pending(ctx, r.id, a, n.second.toString())
        try {
            if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    fun scheduleAll(ctx: Context, r: Reminder) {
        for (a in alertsFor(r.type)) schedule(ctx, r, a)
    }

    fun cancelAll(ctx: Context, r: Reminder) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        for (a in Alert.values()) {
            am.cancel(pending(ctx, r.id, a, ""))
            NotificationManagerCompat.from(ctx).cancel(key(r.id, a).hashCode())
        }
    }

    fun rescheduleAll(ctx: Context) {
        for (r in Repo.state.value.reminders) scheduleAll(ctx, r)
    }

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun parts(vararg items: String): String = items.filter { it.isNotBlank() }.joinToString(" · ")

    fun show(ctx: Context, r: Reminder, a: Alert) {
        if (!canNotify(ctx)) return
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val time = r.time.toString()
        val title: String
        val text: String
        when (a) {
            Alert.PRE -> {
                title = "Через $PRE_MINUTES минут: ${r.title}"
                text = parts("В $time", r.dose, r.how, r.note)
            }
            Alert.MAIN -> {
                title = if (r.type == RemType.PILL) "Пора пить: ${r.title}" else r.title
                text = if (r.type == RemType.PILL) parts(r.dose, r.how, r.note) else parts("В $time", r.note)
            }
            Alert.EVE -> {
                title = "Завтра в $time: ${r.title}"
                text = parts(r.place, r.note)
            }
            Alert.DAY -> {
                title = "Сегодня в $time: ${r.title}"
                text = parts(r.place, r.note)
            }
        }
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        if (r.type == RemType.PILL && a == Alert.MAIN) {
            val act = PendingIntent.getBroadcast(
                ctx, key(r.id, a).hashCode() + 1,
                Intent(ctx, ActionReceiver::class.java)
                    .putExtra("id", r.id)
                    .putExtra("date", LocalDate.now().toString()),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            b.addAction(0, "Принял", act)
        }
        try {
            NotificationManagerCompat.from(ctx).notify(key(r.id, a).hashCode(), b.build())
        } catch (e: SecurityException) {
            // разрешение отозвано между проверкой и показом
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Repo.init(context)
        val id = intent.getStringExtra("id") ?: return
        val a = Alert.from(intent.getStringExtra("kind")) ?: return
        val date = intent.getStringExtra("date") ?: ""
        val r = Repo.state.value.reminders.firstOrNull { it.id == id } ?: return

        // Если таблетку уже отметили принятой, напоминание не показываем.
        val alreadyDone = r.type == RemType.PILL && (id + "|" + date) in Repo.state.value.done
        if (!alreadyDone) Notifier.show(context, r, a)

        // Следующее срабатывание того же вида (для повторяющихся напоминаний).
        Notifier.schedule(context, r, a)
    }
}

/** Кнопка «Принял» в уведомлении. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Repo.init(context)
        val id = intent.getStringExtra("id") ?: return
        val date = intent.getStringExtra("date") ?: return
        Repo.update { it.copy(done = it.done + (id + "|" + date)) }
        val nm = NotificationManagerCompat.from(context)
        nm.cancel((id + "#" + Alert.MAIN.code).hashCode())
        nm.cancel((id + "#" + Alert.PRE.code).hashCode())
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Repo.init(context)
        Notifier.ensureChannel(context)
        Notifier.rescheduleAll(context)
    }
}
