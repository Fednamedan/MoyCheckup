package ru.moycheckup.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

data class Insight(val title: String, val text: String)
data class Badge(val title: String, val desc: String, val unlocked: Boolean)

private val RU_LOCALE: Locale = Locale.forLanguageTag("ru")

private fun f1(v: Double): String = String.format(RU_LOCALE, "%.1f", v)

private fun times(n: Int): String {
    val m = n % 100
    val k = n % 10
    return if (m in 11..14) "раз" else if (k in 2..4) "раза" else "раз"
}

/** Дни, в которые что-то записано: боль, «жалоб нет», настроение, измерение. */
fun AppState.activeDates(): Set<LocalDate> {
    val s = mutableSetOf<LocalDate>()
    entries.forEach { s.add(it.date) }
    vitals.forEach { s.add(it.date) }
    moods.keys.forEach { k -> runCatching { s.add(LocalDate.parse(k)) } }
    return s
}

/** Сколько дней подряд что-то записывали. Если сегодня ещё пусто, серия считается со вчера. */
fun AppState.streak(today: LocalDate): Int {
    val a = activeDates()
    var d = if (today in a) today else today.minusDays(1)
    var n = 0
    while (d in a) {
        n++
        d = d.minusDays(1)
    }
    return n
}

/** Оценка самочувствия от 5 до 100 по последней неделе. null, если данных нет. */
fun AppState.wellness(now: LocalDateTime): Int? {
    if (entries.isEmpty() && moods.isEmpty()) return null
    val today = now.toLocalDate()
    var penalty = 0.0
    for (e in entries) {
        val age = ChronoUnit.DAYS.between(e.date, today)
        if (age in 0..6 && e.sev > 0) penalty += e.sev * 0.85.pow(age.toDouble())
    }
    var score = 100.0 - minOf(75.0, penalty * 2.5)
    val mood = moods[today.toString()]
    if (mood != null) score += (mood - 3) * 4

    val pills = (0..6).map { today.minusDays(it.toLong()) }
        .flatMap { occurrences(it) }
        .filter { it.r.type == RemType.PILL && (it.date.isBefore(today) || !it.r.time.isAfter(now.toLocalTime())) }
    if (pills.isNotEmpty()) {
        val adherence = pills.count { isDone(it) }.toDouble() / pills.size
        score -= (1.0 - adherence) * 15.0
    }
    return score.coerceIn(5.0, 100.0).roundToInt()
}

fun wellnessWord(score: Int?): String = when {
    score == null -> "Пока нет данных"
    score >= 85 -> "Отлично"
    score >= 65 -> "Хорошо"
    score >= 45 -> "Так себе"
    else -> "Нужно внимание"
}

/** Самая сильная боль за день. null: записей нет. 0: только «жалоб нет». */
fun AppState.maxSevOn(d: LocalDate): Int? {
    val es = entries.filter { it.date == d }
    return if (es.isEmpty()) null else es.maxOf { it.sev }
}

fun AppState.insights(now: LocalDateTime): List<Insight> {
    val today = now.toLocalDate()
    val out = mutableListOf<Insight>()
    val m30 = entries.filter { it.sev > 0 && !it.date.isBefore(today.minusDays(29)) && !it.date.isAfter(today) }

    if (m30.isNotEmpty()) {
        val zc = m30.flatMap { it.zones }.groupingBy { it.label }.eachCount().maxByOrNull { it.value }
        if (zc != null) out.add(Insight("Чаще всего болит", "${zc.key}: ${zc.value} ${times(zc.value)} за 30 дней"))

        fun part(h: Int) = when (h) {
            in 5..11 -> "утром"
            in 12..17 -> "днём"
            in 18..22 -> "вечером"
            else -> "ночью"
        }
        val pc = m30.groupingBy { part(it.dateTime.hour) }.eachCount().maxByOrNull { it.value }
        if (pc != null && m30.size >= 3) out.add(Insight("Когда болит", "Чаще всего ${pc.key}: ${pc.value} из ${m30.size} записей"))

        val tc = m30.flatMap { it.triggers }.groupingBy { it }.eachCount().maxByOrNull { it.value }
        if (tc != null && tc.value >= 2) out.add(Insight("Что замечено", "Чаще всего отмечаете: «${tc.key}»"))
    }

    val thisWeek = entries.filter { it.sev > 0 && !it.date.isBefore(today.minusDays(6)) && !it.date.isAfter(today) }
    val prevWeek = entries.filter { it.sev > 0 && !it.date.isBefore(today.minusDays(13)) && it.date.isBefore(today.minusDays(6)) }
    if (thisWeek.isNotEmpty() && prevWeek.isNotEmpty()) {
        val a = thisWeek.map { it.sev }.average()
        val b = prevWeek.map { it.sev }.average()
        val d = a - b
        val text = when {
            abs(d) < 0.5 -> "Средняя сила боли почти не изменилась: ${f1(a)} из 10"
            d < 0 -> "Стало легче: средняя сила боли снизилась с ${f1(b)} до ${f1(a)}"
            else -> "Стало тяжелее: средняя сила боли выросла с ${f1(b)} до ${f1(a)}"
        }
        out.add(Insight("Динамика за неделю", text))
    }

    val pr = vitals.filter { it.type == VitalType.PRESSURE && !it.date.isBefore(today.minusDays(6)) }
    if (pr.size >= 2) {
        out.add(Insight("Давление", "Среднее за 7 дней: ${pr.map { it.a }.average().roundToInt()}/${pr.map { it.b }.average().roundToInt()}"))
    }

    val pills = (0..6).map { today.minusDays(it.toLong()) }
        .flatMap { occurrences(it) }
        .filter { it.r.type == RemType.PILL && (it.date.isBefore(today) || !it.r.time.isAfter(now.toLocalTime())) }
    if (pills.isNotEmpty()) {
        val done = pills.count { isDone(it) }
        out.add(Insight("Таблетки", "За неделю принято $done из ${pills.size}"))
    }
    return out.take(6)
}

fun AppState.badges(now: LocalDateTime): List<Badge> {
    val today = now.toLocalDate()
    val st = streak(today)
    val pills = (0..6).map { today.minusDays(it.toLong()) }
        .flatMap { occurrences(it) }
        .filter { it.r.type == RemType.PILL && (it.date.isBefore(today) || !it.r.time.isAfter(now.toLocalTime())) }
    val perfect = pills.size >= 3 && pills.all { isDone(it) }
    return listOf(
        Badge("Первый шаг", "Сделана первая запись", entries.isNotEmpty()),
        Badge("3 дня подряд", "Записи три дня без пропусков", st >= 3),
        Badge("Неделя подряд", "Записи семь дней без пропусков", st >= 7),
        Badge("Дисциплина", "Все таблетки за неделю приняты", perfect),
        Badge("Наблюдатель", "Пять измерений давления, пульса, температуры или веса", vitals.size >= 5),
    )
}
