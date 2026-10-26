package ru.moycheckup.report

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import ru.moycheckup.data.AppState
import ru.moycheckup.data.Body
import ru.moycheckup.data.BodyShape
import ru.moycheckup.data.RemType
import ru.moycheckup.data.VitalType
import ru.moycheckup.data.WeekStats
import ru.moycheckup.data.isDone
import ru.moycheckup.data.upcoming
import ru.moycheckup.data.weekStats
import ru.moycheckup.ui.RU
import ru.moycheckup.ui.cap
import ru.moycheckup.ui.display
import ru.moycheckup.ui.moodWord
import ru.moycheckup.ui.fmt
import ru.moycheckup.ui.plural
import ru.moycheckup.ui.weekLabel
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime

/** PDF-сводка недели. Рисуется через android.graphics.pdf.PdfDocument, текст в PDF остаётся текстом. */
object PdfReport {
    private const val PW = 595
    private const val PH = 842
    private const val M = 40f

    private const val INK = 0xFF12262A.toInt()
    private const val MUT = 0xFF5B6F73.toInt()
    private const val LINE = 0xFFD5E0E0.toInt()
    private const val ACC = 0xFF0B6E5F.toInt()
    private const val TILE = 0xFFEDF2F1.toInt()
    private const val BODY = 0xFFDCE6E6.toInt()
    private const val BLINE = 0xFFB7C8C8.toInt()

    private fun sevInt(n: Int): Int = when {
        n <= 0 -> 0xFF7FB8A9.toInt()
        n <= 3 -> 0xFFF0C45A.toInt()
        n <= 6 -> 0xFFE8883A.toInt()
        else -> 0xFFD2423B.toInt()
    }

    private fun paint(size: Float, color: Int, bold: Boolean = false): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    private fun fill(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color
    }

    private fun wrap(text: String, p: Paint, maxW: Float): List<String> {
        val out = mutableListOf<String>()
        var cur = ""
        for (word in text.split(" ")) {
            val t = if (cur.isEmpty()) word else "$cur $word"
            if (p.measureText(t) > maxW && cur.isNotEmpty()) {
                out.add(cur)
                cur = word
            } else {
                cur = t
            }
        }
        if (cur.isNotEmpty()) out.add(cur)
        return out
    }

    private class Pg(val doc: PdfDocument) {
        var n = 0
        lateinit var page: PdfDocument.Page
        lateinit var c: Canvas
        var y = PdfReport.M

        fun next() {
            if (n > 0) {
                footer()
                doc.finishPage(page)
            }
            n++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PdfReport.PW, PdfReport.PH, n).create())
            c = page.canvas
            y = PdfReport.M
        }

        fun ensure(h: Float) {
            if (y + h > PdfReport.PH - PdfReport.M - 20f) next()
        }

        fun footer() {
            val p = PdfReport.paint(8f, PdfReport.MUT)
            c.drawText("Создано в приложении «Мой чекап». Не заменяет консультацию врача.", PdfReport.M, PdfReport.PH - 22f, p)
            val label = "Стр. $n"
            c.drawText(label, PdfReport.PW - PdfReport.M - p.measureText(label), PdfReport.PH - 22f, p)
        }

        fun finish() {
            footer()
            doc.finishPage(page)
        }
    }

    fun build(ctx: Context, state: AppState, offset: Int, now: LocalDateTime): File {
        val w = state.weekStats(offset, now)
        val doc = PdfDocument()
        val g = Pg(doc)
        g.next()

        header(g, w)
        stats(g, w)
        bodies(g, w)
        days(g, w)
        entries(g, w)
        vitals(g, state, w)
        meds(g, state, w, now)

        g.finish()
        val dir = File(ctx.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "moy-chekap-${w.days.first()}_${w.days.last()}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    private fun heading(g: Pg, text: String) {
        g.ensure(40f)
        g.y += 14f
        g.c.drawText(text.uppercase(RU), M, g.y + 10f, paint(9f, MUT, true).apply { letterSpacing = 0.08f })
        g.y += 22f
    }

    private fun header(g: Pg, w: WeekStats) {
        g.c.drawText("Мой чекап", M, g.y + 20f, paint(22f, ACC, true))
        val r = paint(11f, MUT)
        val t = "Недельная сводка"
        g.c.drawText(t, PW - M - r.measureText(t), g.y + 18f, r)
        g.y += 36f
        g.c.drawText(weekLabel(w.days) + " " + w.days.last().year, M, g.y + 16f, paint(17f, INK, true))
        g.y += 28f
        g.c.drawRect(M, g.y, PW - M, g.y + 1f, fill(LINE))
        g.y += 12f
    }

    private fun stats(g: Pg, w: WeekStats) {
        g.ensure(70f)
        val gap = 9f
        val bw = (PW - 2 * M - 3 * gap) / 4f
        val items = listOf(
            w.pain.size.toString() to plural(w.pain.size, "запись", "записи", "записей") + " о боли",
            "${w.painDays} из 7" to "дней с болью",
            (if (w.pain.isEmpty()) "—" else "%.1f".format(RU, w.avg)) to "средняя сила, из 10",
            (if (w.pain.isEmpty()) "—" else w.max.toString()) to "самая сильная, из 10",
        )
        items.forEachIndexed { i, (v, cap) ->
            val x = M + i * (bw + gap)
            g.c.drawRoundRect(RectF(x, g.y, x + bw, g.y + 60f), 9f, 9f, fill(TILE))
            g.c.drawText(v, x + 10f, g.y + 29f, paint(20f, INK, true))
            g.c.drawText(cap, x + 10f, g.y + 48f, paint(8.5f, MUT))
        }
        g.y += 66f
    }

    private fun drawBody(c: Canvas, front: Boolean, ox: Float, oy: Float, k: Float, heat: Map<String, Int>) {
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
            color = BLINE
        }
        for (z in Body.zones(front)) {
            val sev = Body.heatFor(z, heat)
            val f = fill(if (sev > 0) sevInt(sev) else BODY)
            when (val s = z.shape) {
                is BodyShape.Rect -> {
                    val r = RectF(ox + s.x * k, oy + s.y * k, ox + (s.x + s.w) * k, oy + (s.y + s.h) * k)
                    c.drawRoundRect(r, s.r * k, s.r * k, f)
                    c.drawRoundRect(r, s.r * k, s.r * k, line)
                }
                is BodyShape.Oval -> {
                    val r = RectF(ox + (s.cx - s.rx) * k, oy + (s.cy - s.ry) * k, ox + (s.cx + s.rx) * k, oy + (s.cy + s.ry) * k)
                    c.drawOval(r, f)
                    c.drawOval(r, line)
                }
            }
        }
    }

    private fun bodies(g: Pg, w: WeekStats) {
        heading(g, "Где болело")
        val k = 0.52f
        val bh = Body.H * k
        g.ensure(bh + 30f)
        drawBody(g.c, true, M, g.y, k, w.heat)
        drawBody(g.c, false, M + Body.W * k + 18f, g.y, k, w.heat)
        val lab = paint(7.5f, MUT)
        g.c.drawText("СПЕРЕДИ", M + 28f, g.y + bh + 14f, lab)
        g.c.drawText("СЗАДИ", M + Body.W * k + 18f + 32f, g.y + bh + 14f, lab)

        val tx = M + 2 * Body.W * k + 50f
        val tw = PW - M - tx
        var ty = g.y + 6f
        if (w.top.isEmpty()) {
            g.c.drawText("Записей о боли на этой неделе нет.", tx, ty + 12f, paint(11f, MUT))
        } else {
            val mx = w.top.first().n
            for (z in w.top.take(7)) {
                g.c.drawText(z.label, tx, ty + 12f, paint(11f, INK))
                val num = z.n.toString()
                val np = paint(11f, INK, true)
                g.c.drawText(num, tx + tw - np.measureText(num), ty + 12f, np)
                g.c.drawRoundRect(RectF(tx, ty + 18f, tx + tw - 22f, ty + 24f), 3f, 3f, fill(TILE))
                g.c.drawRoundRect(RectF(tx, ty + 18f, tx + maxOf(8f, (tw - 22f) * z.n / mx), ty + 24f), 3f, 3f, fill(sevInt(z.max)))
                ty += 34f
            }
            val note = paint(8f, MUT)
            for (ln in wrap("Цвет: самая сильная боль в зоне. Число: сколько раз зону отметили.", note, tw)) {
                g.c.drawText(ln, tx, ty + 10f, note)
                ty += 11f
            }
        }
        g.y += bh + 24f
    }

    private fun days(g: Pg, w: WeekStats) {
        heading(g, "По дням: самая сильная боль")
        g.ensure(110f)
        val cw = (PW - 2 * M) / 7f
        val th = 60f
        w.perDay.forEachIndexed { i, p ->
            val x = M + i * cw + 4f
            val bw = cw - 8f
            val label = if (p.max > 0) p.max.toString() else if (p.good) "ок" else ""
            val lp = paint(10f, INK, true)
            g.c.drawText(label, x + (bw - lp.measureText(label)) / 2f, g.y + 10f, lp)
            g.c.drawRoundRect(RectF(x, g.y + 16f, x + bw, g.y + 16f + th), 6f, 6f, fill(TILE))
            if (p.max > 0) {
                val h = th * p.max / 10f
                g.c.drawRoundRect(RectF(x, g.y + 16f + th - h, x + bw, g.y + 16f + th), 6f, 6f, fill(sevInt(p.max)))
            }
            val dl = p.date.fmt("EE d").cap()
            val dp = paint(8.5f, MUT)
            g.c.drawText(dl, x + (bw - dp.measureText(dl)) / 2f, g.y + 16f + th + 13f, dp)
        }
        g.y += 16f + th + 22f
    }

    private fun entries(g: Pg, w: WeekStats) {
        heading(g, "Записи")
        if (w.entries.isEmpty()) {
            g.ensure(20f)
            g.c.drawText("Записей за неделю нет.", M, g.y + 12f, paint(11f, MUT))
            g.y += 20f
            return
        }
        val textX = M + 112f
        val textW = PW - M - 60f - textX
        for (e in w.entries) {
            val zonesText = if (e.zones.isEmpty()) "Без жалоб" else e.zones.joinToString(", ") { it.label }
            val zp = paint(11f, INK, true)
            val mp = paint(9.5f, MUT)
            val zl = wrap(zonesText, zp, textW)
            val meta = listOf(
                e.kinds.joinToString(", "),
                e.triggers.joinToString(", "),
                e.extras.joinToString(", "),
                if (e.since.isBlank()) "" else "болит: " + e.since.lowercase(),
                e.note,
            ).filter { it.isNotBlank() }.joinToString(" · ")
            val ml = if (meta.isEmpty()) emptyList() else wrap(meta, mp, textW)
            val h = maxOf(24f, zl.size * 14f + ml.size * 12.5f + 8f)
            g.ensure(h + 8f)
            g.c.drawText(e.dateTime.fmt("EE, d MMM, HH:mm").cap(), M, g.y + 12f, paint(9.5f, MUT))
            var ly = g.y
            for (ln in zl) {
                g.c.drawText(ln, textX, ly + 12f, zp)
                ly += 14f
            }
            for (ln in ml) {
                g.c.drawText(ln, textX, ly + 11f, mp)
                ly += 12.5f
            }
            val bx = PW - M - 44f
            g.c.drawRoundRect(RectF(bx, g.y, bx + 44f, g.y + 18f), 6f, 6f, fill(sevInt(e.sev)))
            val bt = if (e.sev > 0) "${e.sev}/10" else "ок"
            val bp = paint(10f, if (e.sev >= 7) 0xFFFFFFFF.toInt() else 0xFF1B1206.toInt(), true)
            g.c.drawText(bt, bx + (44f - bp.measureText(bt)) / 2f, g.y + 13f, bp)
            g.y += h
            g.c.drawRect(M, g.y, PW - M, g.y + 0.6f, fill(LINE))
            g.y += 7f
        }
    }

    private fun lines(g: Pg, text: String, p: Paint, lh: Float, x: Float = M, width: Float = PW - 2 * M) {
        for (ln in wrap(text, p, width)) {
            g.ensure(lh + 2f)
            g.c.drawText(ln, x, g.y + lh - 3f, p)
            g.y += lh
        }
    }

    private fun vitals(g: Pg, state: AppState, w: WeekStats) {
        val days = w.days.toSet()
        val vs = state.vitals.filter { it.date in days }.sortedBy { it.ts }
        val moods = w.days.mapNotNull { d -> state.moods[d.toString()]?.let { d to it } }
        if (vs.isEmpty() && moods.isEmpty()) return
        heading(g, "Показатели и самочувствие")
        for (t in VitalType.values()) {
            val list = vs.filter { it.type == t }
            if (list.isEmpty()) continue
            val last = list.last()
            val txt = "${t.ru}: ${list.size} " + plural(list.size, "измерение", "измерения", "измерений") +
                ", последнее ${last.display()} ${t.unit} (${last.date.fmt("d MMM")})"
            lines(g, txt, paint(11f, INK), 16f)
        }
        if (moods.isNotEmpty()) {
            val avg = moods.map { it.second }.average()
            val txt = "Настроение: " + moods.joinToString(", ") { it.first.fmt("EEE") + " — " + moodWord(it.second).lowercase(RU) } +
                String.format(RU, ". Среднее %.1f из 5.", avg)
            lines(g, txt, paint(11f, INK), 16f)
        }
        g.y += 4f
    }

    private fun meds(g: Pg, state: AppState, w: WeekStats, now: LocalDateTime) {
        heading(g, "Лекарства и визиты")
        val done = w.pillsPast.count { state.isDone(it) }
        val line = if (w.pillsPast.isEmpty()) "Приёмов таблеток за прошедшие дни нет." else "Таблетки приняты $done из ${w.pillsPast.size}."
        g.ensure(18f)
        g.c.drawText(line, M, g.y + 12f, paint(11.5f, INK, true))
        g.y += 18f

        val missed = w.pillsPast.filter { !state.isDone(it) }
        if (missed.isNotEmpty()) {
            val txt = "Пропущено: " + missed.joinToString("; ") { it.r.title + " (" + it.date.fmt("EE, d MMM") + ")" }
            lines(g, txt, paint(9.5f, MUT), 13f)
        }

        val pills = state.reminders.filter { it.type == RemType.PILL }
        if (pills.isNotEmpty()) {
            heading(g, "Схема приёма лекарств")
            for (r in pills) {
                val head = r.title + (if (r.dose.isNotBlank()) ", " + r.dose else "")
                lines(g, head, paint(11f, INK, true), 14f)
                val sched = listOf(
                    r.repeat.ru.lowercase().cap() + " в " + r.time,
                    r.how,
                    if (r.until != null) "курс до " + r.until.fmt("d MMMM") else "",
                ).filter { it.isNotBlank() }.joinToString(" · ")
                lines(g, sched, paint(9.5f, MUT), 12.5f)
                if (r.note.isNotBlank()) lines(g, r.note, paint(9.5f, INK), 12.5f)
                g.y += 5f
            }
        }

        if (w.visits.isNotEmpty()) {
            heading(g, "Визиты и анализы на этой неделе")
            for (o in w.visits) visitLine(g, state, o.r.title, o.date, o.r.time.toString(), o.r.type.ru, o.r.place, o.r.note, state.isDone(o))
        }

        val weekSet = w.days.toSet()
        val up = state.upcoming(now.toLocalDate(), 14).filter { it.date !in weekSet }
        if (up.isNotEmpty()) {
            heading(g, "Впереди, 14 дней")
            for (o in up) visitLine(g, state, o.r.title, o.date, o.r.time.toString(), o.r.type.ru, o.r.place, o.r.note, false)
        }
    }

    private fun visitLine(
        g: Pg, state: AppState, title: String, date: java.time.LocalDate, time: String,
        type: String, place: String, note: String, done: Boolean,
    ) {
        val t = date.fmt("EE, d MMM").cap() + ", " + time + " · " + type + ": " + title + (if (done) " · выполнено" else "")
        lines(g, t, paint(11f, INK, true), 14f)
        val extra = listOf(place, note).filter { it.isNotBlank() }.joinToString(" · ")
        if (extra.isNotEmpty()) lines(g, extra, paint(9.5f, MUT), 12.5f)
        g.y += 4f
    }

    private fun uri(ctx: Context, f: File) = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)

    fun share(ctx: Context, f: File) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri(ctx, f))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, "Сводка за неделю").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun open(ctx: Context, f: File) {
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri(ctx, f), "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            ctx.startActivity(i)
        } catch (e: Exception) {
            share(ctx, f)
        }
    }
}
