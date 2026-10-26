package ru.moycheckup.data

import java.time.LocalDate
import java.time.LocalDateTime

data class DayStat(val date: LocalDate, val max: Int, val n: Int, val good: Boolean)
data class ZoneStat(val label: String, val n: Int, val max: Int)

data class WeekStats(
    val days: List<LocalDate>,
    val entries: List<Entry>,
    val pain: List<Entry>,
    val perDay: List<DayStat>,
    val top: List<ZoneStat>,
    val heat: Map<String, Int>,
    val avg: Double,
    val painDays: Int,
    val max: Int,
    val pillsPast: List<Occurrence>,
    val visits: List<Occurrence>,
)

/** Неделя с понедельника по воскресенье. offset = 0 текущая, -1 прошлая и т. д. */
fun weekDays(offset: Int): List<LocalDate> {
    val today = LocalDate.now()
    val start = today.minusDays((today.dayOfWeek.value - 1).toLong()).plusWeeks(offset.toLong())
    return (0..6).map { start.plusDays(it.toLong()) }
}

fun AppState.weekStats(offset: Int, now: LocalDateTime): WeekStats {
    val days = weekDays(offset)
    val set = days.toSet()
    val es = entries.filter { it.date in set }.sortedBy { it.ts }
    val pain = es.filter { it.sev > 0 }

    val perDay = days.map { d ->
        val de = es.filter { it.date == d }
        DayStat(d, de.maxOfOrNull { it.sev } ?: 0, de.count { it.sev > 0 }, de.any { it.sev == 0 })
    }

    val zm = mutableMapOf<String, ZoneStat>()
    for (e in pain) for (z in e.zones) {
        val cur = zm[z.label]
        zm[z.label] = ZoneStat(z.label, (cur?.n ?: 0) + 1, maxOf(cur?.max ?: 0, e.sev))
    }
    val top = zm.values.sortedWith(compareByDescending<ZoneStat> { it.n }.thenByDescending { it.max })
    val heat = top.associate { it.label to it.max }

    val today = now.toLocalDate()
    val nowTime = now.toLocalTime()
    val occ = days.flatMap { occurrences(it) }
    val past = occ.filter { it.date.isBefore(today) || (it.date == today && !it.r.time.isAfter(nowTime)) || isDone(it) }

    return WeekStats(
        days = days,
        entries = es,
        pain = pain,
        perDay = perDay,
        top = top,
        heat = heat,
        avg = if (pain.isEmpty()) 0.0 else pain.map { it.sev }.average(),
        painDays = perDay.count { it.n > 0 },
        max = pain.maxOfOrNull { it.sev } ?: 0,
        pillsPast = past.filter { it.r.type == RemType.PILL },
        visits = occ.filter { it.r.type == RemType.DOC || it.r.type == RemType.LAB },
    )
}

/** Невыполненные визиты и анализы на ближайшие n дней. */
fun AppState.upcoming(from: LocalDate, n: Int): List<Occurrence> {
    val out = mutableListOf<Occurrence>()
    for (i in 0 until n) {
        val d = from.plusDays(i.toLong())
        for (o in occurrences(d)) if (o.r.type != RemType.PILL && !isDone(o)) out.add(o)
    }
    return out
}
