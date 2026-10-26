package ru.moycheckup.ui

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

val RU: Locale = Locale.forLanguageTag("ru")

fun LocalDate.fmt(pattern: String): String = format(DateTimeFormatter.ofPattern(pattern, RU))
fun LocalDateTime.fmt(pattern: String): String = format(DateTimeFormatter.ofPattern(pattern, RU))
fun String.cap(): String = replaceFirstChar { it.uppercase() }

fun plural(n: Int, one: String, few: String, many: String): String {
    val m = Math.abs(n) % 100
    val k = m % 10
    return when {
        m in 11..19 -> many
        k == 1 -> one
        k in 2..4 -> few
        else -> many
    }
}

fun dayLabel(d: LocalDate, today: LocalDate = LocalDate.now()): String = when (d) {
    today -> "сегодня"
    today.plusDays(1) -> "завтра"
    today.minusDays(1) -> "вчера"
    else -> d.fmt("EEE, d MMM")
}

fun greeting(hour: Int): String = when {
    hour < 5 -> "Доброй ночи"
    hour < 12 -> "Доброе утро"
    hour < 18 -> "Добрый день"
    else -> "Добрый вечер"
}

fun sevWord(n: Int): String = when {
    n <= 3 -> "Слабая"
    n <= 6 -> "Умеренная"
    n <= 8 -> "Сильная"
    else -> "Нестерпимая"
}

/** Какая боль. */
val KINDS = listOf(
    "Тупая", "Острая", "Жгучая", "Пульсирующая", "Давящая",
    "Стреляющая", "Ноющая", "Распирающая", "Покалывание",
)

/** Когда проявляется. */
val TRIGGERS = listOf(
    "Постоянно", "Приступами", "В покое", "При движении", "При нагрузке",
    "Утром", "Вечером", "Ночью", "После еды",
)

/** Что ещё беспокоит. */
val EXTRAS = listOf(
    "Тошнота", "Головокружение", "Слабость", "Температура", "Светобоязнь",
    "Онемение", "Отёк", "Одышка", "Сыпь",
)

/** Как давно. */
val SINCE = listOf("Только что", "Несколько часов", "С утра", "Несколько дней", "Больше недели")

/** Как принимать таблетки. */
val HOW = listOf("Натощак", "До еды", "Во время еды", "После еды", "Перед сном", "Запивать водой")

fun weekLabel(days: List<LocalDate>): String {
    val a = days.first()
    val b = days.last()
    return if (a.month == b.month) "${a.dayOfMonth} – ${b.dayOfMonth} ${b.fmt("MMMM")}"
    else "${a.dayOfMonth} ${a.fmt("MMMM")} – ${b.dayOfMonth} ${b.fmt("MMMM")}"
}

private fun one(v: Double): String = String.format(RU, "%.1f", v)

/** Значение измерения для показа: 120/80, 68, 36,6, 72,5. */
fun ru.moycheckup.data.Vital.display(): String = when (type) {
    ru.moycheckup.data.VitalType.PRESSURE -> "${a.toInt()}/${b.toInt()}"
    ru.moycheckup.data.VitalType.PULSE -> a.toInt().toString()
    else -> one(a)
}

fun moodWord(level: Int?): String = when (level) {
    1 -> "Плохо"
    2 -> "Не очень"
    3 -> "Нормально"
    4 -> "Хорошо"
    5 -> "Отлично"
    else -> "Нажмите на лицо, чтобы отметить"
}
