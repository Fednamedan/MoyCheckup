package ru.moycheckup.data

/** Геометрия схем тела и головы. Тело в условных координатах 200 x 430, голова 100 x 120. */
sealed interface BodyShape {
    data class Rect(val x: Float, val y: Float, val w: Float, val h: Float, val r: Float) : BodyShape
    data class Oval(val cx: Float, val cy: Float, val rx: Float, val ry: Float) : BodyShape
}

data class Zone(val id: String, val label: String, val shape: BodyShape)

object Body {
    const val W = 200f
    const val H = 430f
    const val HW = 100f
    const val HH = 120f

    val front: List<Zone> = buildBody(true)
    val back: List<Zone> = buildBody(false)
    val headFront: List<Zone> = buildHead(true)
    val headBack: List<Zone> = buildHead(false)
    val byId: Map<String, Zone> = (front + back + headFront + headBack).associateBy { it.id }

    /** Контур головы и шеи, на который ложатся зоны. */
    val headBase: List<BodyShape> = listOf(
        BodyShape.Oval(50f, 56f, 34f, 46f),
        BodyShape.Rect(41f, 94f, 18f, 24f, 8f),
    )

    /** Подписи всех зон головы: общая зона «Голова» на теле закрашивается по самой сильной из них. */
    val headLabels: Set<String> = (headFront + headBack).map { it.label }.toSet() + setOf("Голова")

    fun zones(isFront: Boolean): List<Zone> = if (isFront) front else back
    fun headZones(isFront: Boolean): List<Zone> = if (isFront) headFront else headBack

    fun isHeadZone(z: Zone): Boolean = z.id == "f_head" || z.id == "b_head"

    /** Самая сильная боль, которой нужно закрасить зону. */
    fun heatFor(z: Zone, heat: Map<String, Int>): Int =
        if (isHeadZone(z)) headLabels.maxOf { heat[it] ?: 0 } else (heat[z.label] ?: 0)

    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float): BodyShape = BodyShape.Rect(x, y, w, h, r)
    private fun oval(cx: Float, cy: Float, rx: Float, ry: Float): BodyShape = BodyShape.Oval(cx, cy, rx, ry)

    private fun mirror(s: BodyShape, width: Float): BodyShape = when (s) {
        is BodyShape.Rect -> s.copy(x = width - s.x - s.w)
        is BodyShape.Oval -> s.copy(cx = width - s.cx)
    }

    /** Род: 0 среднего (правое), 1 женский (правая), 2 мужской (правый). */
    private class Builder(val prefix: String, val isFront: Boolean, val width: Float) {
        val zones = mutableListOf<Zone>()

        fun one(key: String, label: String, shape: BodyShape) {
            zones.add(Zone(prefix + "_" + key, label, shape))
        }

        // На виде спереди слева находится ПРАВАЯ сторона человека, на виде сзади слева ЛЕВАЯ.
        fun pair(key: String, noun: String, gender: Int, shape: BodyShape) {
            val right = when (gender) { 1 -> "Правая"; 2 -> "Правый"; else -> "Правое" }
            val left = when (gender) { 1 -> "Левая"; 2 -> "Левый"; else -> "Левое" }
            val viewerLeft = if (isFront) right else left
            val viewerRight = if (isFront) left else right
            one(key + "a", "$viewerLeft $noun", shape)
            one(key + "b", "$viewerRight $noun", mirror(shape, width))
        }
    }

    private fun buildBody(isFront: Boolean): List<Zone> {
        val b = Builder(if (isFront) "f" else "b", isFront, W)
        if (isFront) {
            b.one("head", "Голова", oval(100f, 32f, 22f, 27f))
            b.one("neck", "Шея", rect(90f, 60f, 20f, 16f, 6f))
            b.one("chest", "Грудь", rect(64f, 78f, 72f, 50f, 14f))
            b.one("belly", "Живот", rect(68f, 130f, 64f, 44f, 10f))
            b.one("pelvis", "Низ живота и таз", rect(66f, 176f, 68f, 30f, 12f))
            b.pair("sh", "плечо", 0, rect(36f, 80f, 22f, 70f, 11f))
            b.pair("fa", "предплечье", 0, rect(32f, 154f, 18f, 54f, 9f))
            b.pair("hd", "кисть", 1, oval(38f, 224f, 9f, 13f))
            b.pair("th", "бедро", 0, rect(68f, 208f, 30f, 88f, 14f))
            b.pair("kn", "колено", 0, oval(83f, 306f, 14f, 12f))
            b.pair("sn", "голень", 1, rect(70f, 320f, 26f, 70f, 12f))
            b.pair("ft", "стопа", 1, oval(80f, 408f, 16f, 9f))
        } else {
            b.one("head", "Затылок", oval(100f, 32f, 22f, 27f))
            b.one("neck", "Шея сзади", rect(90f, 60f, 20f, 16f, 6f))
            b.one("up", "Верх спины", rect(64f, 78f, 72f, 46f, 14f))
            b.one("low", "Поясница", rect(68f, 126f, 64f, 38f, 10f))
            b.one("glut", "Ягодицы", rect(66f, 166f, 68f, 40f, 16f))
            b.pair("sh", "плечо", 0, rect(36f, 80f, 22f, 70f, 11f))
            b.pair("fa", "предплечье", 0, rect(32f, 154f, 18f, 54f, 9f))
            b.pair("hd", "кисть", 1, oval(38f, 224f, 9f, 13f))
            b.pair("th", "бедро (сзади)", 0, rect(68f, 208f, 30f, 88f, 14f))
            b.pair("kn", "подколенная ямка", 1, oval(83f, 306f, 14f, 12f))
            b.pair("sn", "икра", 1, rect(70f, 320f, 26f, 70f, 12f))
            b.pair("ft", "пятка", 1, oval(80f, 408f, 16f, 9f))
        }
        return b.zones
    }

    private fun buildHead(isFront: Boolean): List<Zone> {
        val b = Builder(if (isFront) "hf" else "hb", isFront, HW)
        if (isFront) {
            b.one("crown", "Темя", oval(50f, 14f, 20f, 8f))
            b.one("forehead", "Лоб", rect(26f, 23f, 48f, 19f, 9f))
            b.pair("temple", "висок", 2, oval(22f, 45f, 6f, 10f))
            b.pair("eye", "область глаза", 1, oval(37f, 50f, 10f, 6f))
            b.one("nose", "Нос", rect(46f, 55f, 8f, 18f, 4f))
            b.pair("cheek", "щека", 1, oval(32f, 70f, 9f, 8f))
            b.one("mouth", "Рот и губы", rect(40f, 78f, 20f, 8f, 4f))
            b.one("jaw", "Челюсть и подбородок", rect(34f, 88f, 32f, 12f, 6f))
            b.pair("ear", "ухо", 0, oval(12f, 56f, 5f, 9f))
        } else {
            b.one("crown", "Темя", oval(50f, 14f, 22f, 9f))
            b.one("occ", "Затылок", rect(28f, 26f, 44f, 28f, 12f))
            b.pair("side", "сторона головы", 1, oval(23f, 46f, 8f, 16f))
            b.one("base", "Основание черепа", rect(36f, 58f, 28f, 16f, 7f))
            b.pair("ear", "ухо", 0, oval(12f, 56f, 5f, 9f))
            b.one("neck", "Шея сзади", rect(42f, 80f, 16f, 22f, 6f))
        }
        return b.zones
    }

    /** Какая зона под пальцем. Координаты в системе схемы. Небольшой запас по краям облегчает попадание. */
    fun hit(zones: List<Zone>, x: Float, y: Float, pad: Float = 3f): Zone? {
        for (z in zones.asReversed()) {
            when (val s = z.shape) {
                is BodyShape.Rect ->
                    if (x >= s.x - pad && x <= s.x + s.w + pad && y >= s.y - pad && y <= s.y + s.h + pad) return z
                is BodyShape.Oval -> {
                    val dx = (x - s.cx) / (s.rx + pad)
                    val dy = (y - s.cy) / (s.ry + pad)
                    if (dx * dx + dy * dy <= 1f) return z
                }
            }
        }
        return null
    }
}
