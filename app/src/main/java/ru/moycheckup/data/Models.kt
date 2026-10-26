package ru.moycheckup.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class ZoneRef(val id: String, val label: String)

data class Entry(
    val id: String,
    val ts: Long,
    val zones: List<ZoneRef>,
    val sev: Int,
    val kinds: List<String>,
    val note: String,
    /** Когда проявляется: в покое, при движении, ночью... */
    val triggers: List<String> = emptyList(),
    /** Что ещё беспокоит: тошнота, головокружение... */
    val extras: List<String> = emptyList(),
    /** Как давно болит. */
    val since: String = "",
) {
    val dateTime: LocalDateTime
        get() = LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault())
    val date: LocalDate get() = dateTime.toLocalDate()
}

enum class RemType(val ru: String) {
    PILL("Таблетка"), DOC("Врач"), LAB("Анализ"), OTHER("Другое")
}

enum class Repeat(val ru: String) {
    NONE("Один раз"), DAILY("Каждый день"), WEEKLY("Каждую неделю")
}

data class Reminder(
    val id: String,
    val title: String,
    val type: RemType,
    val date: LocalDate,
    val time: LocalTime,
    val repeat: Repeat,
    val note: String,
    /** Дозировка, например «1 таблетка, 500 мг». */
    val dose: String = "",
    /** Как принимать: «После еды, запивать водой». */
    val how: String = "",
    /** Для визитов и анализов: клиника, кабинет. */
    val place: String = "",
    /** Для курса таблеток: последний день приёма (включительно). */
    val until: LocalDate? = null,
)

enum class VitalType(val ru: String, val unit: String) {
    PRESSURE("Давление", "мм рт. ст."),
    PULSE("Пульс", "уд/мин"),
    TEMP("Температура", "°C"),
    WEIGHT("Вес", "кг"),
}

/** Одно измерение. Для давления a = верхнее, b = нижнее. */
data class Vital(
    val id: String,
    val ts: Long,
    val type: VitalType,
    val a: Double,
    val b: Double = 0.0,
) {
    val dateTime: LocalDateTime
        get() = LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault())
    val date: LocalDate get() = dateTime.toLocalDate()
}

data class AppState(
    val entries: List<Entry> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    /** Ключи вида "idНапоминания|2026-10-22" для выполненных приёмов. */
    val done: Set<String> = emptySet(),
    val vitals: List<Vital> = emptyList(),
    /** Настроение по дням: "2026-10-22" -> от 1 (плохо) до 5 (отлично). */
    val moods: Map<String, Int> = emptyMap(),
)

data class Occurrence(val r: Reminder, val date: LocalDate) {
    val key: String get() = r.id + "|" + date.toString()
}

fun Reminder.occursOn(d: LocalDate): Boolean {
    val end = until
    if (end != null && d.isAfter(end)) return false
    return when (repeat) {
        Repeat.NONE -> d == date
        Repeat.DAILY -> !d.isBefore(date)
        Repeat.WEEKLY -> !d.isBefore(date) && d.dayOfWeek == date.dayOfWeek
    }
}

fun AppState.occurrences(d: LocalDate): List<Occurrence> =
    reminders.filter { it.occursOn(d) }.map { Occurrence(it, d) }.sortedBy { it.r.time }

fun AppState.isDone(o: Occurrence): Boolean = o.key in done

/** Ближайшая дата напоминания, начиная с сегодняшнего дня (выполненный сегодняшний приём пропускается). */
fun AppState.nextDate(r: Reminder, from: LocalDate): LocalDate? {
    for (i in 0..400) {
        val d = from.plusDays(i.toLong())
        if (r.occursOn(d) && !(i == 0 && (r.id + "|" + d.toString()) in done)) return d
    }
    return null
}
