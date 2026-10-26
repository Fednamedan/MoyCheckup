package ru.moycheckup.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/** Хранилище: всё лежит в SharedPreferences одной JSON-строкой, на самом телефоне. */
object Repo {
    private var prefs: SharedPreferences? = null
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state

    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("checkup", Context.MODE_PRIVATE)
        prefs = p
        val raw = p.getString("data", null)
        if (raw != null) {
            try {
                _state.value = decode(JSONObject(raw))
            } catch (e: Exception) {
                // повреждённые данные не должны ронять приложение
            }
        }
    }

    fun update(f: (AppState) -> AppState) {
        val next = f(_state.value)
        _state.value = next
        prefs?.edit()?.putString("data", encode(next).toString())?.apply()
    }

    fun newId(): String = UUID.randomUUID().toString().take(12)

    // ---------- JSON ----------

    private fun arr(list: List<String>): JSONArray {
        val a = JSONArray()
        for (x in list) a.put(x)
        return a
    }

    private fun strings(j: JSONObject, key: String): List<String> {
        val a = j.optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).map { a.getString(it) }
    }

    private fun encode(s: AppState): JSONObject {
        val entries = JSONArray()
        for (e in s.entries) {
            val zones = JSONArray()
            for (z in e.zones) zones.put(JSONObject().put("id", z.id).put("label", z.label))
            entries.put(
                JSONObject()
                    .put("id", e.id).put("ts", e.ts).put("sev", e.sev).put("note", e.note)
                    .put("zones", zones).put("kinds", arr(e.kinds))
                    .put("triggers", arr(e.triggers)).put("extras", arr(e.extras)).put("since", e.since)
            )
        }
        val rems = JSONArray()
        for (r in s.reminders) {
            rems.put(
                JSONObject()
                    .put("id", r.id).put("title", r.title).put("type", r.type.name)
                    .put("date", r.date.toString()).put("time", r.time.toString())
                    .put("repeat", r.repeat.name).put("note", r.note)
                    .put("dose", r.dose).put("how", r.how).put("place", r.place)
                    .put("until", r.until?.toString() ?: "")
            )
        }
        val done = JSONArray()
        for (d in s.done) done.put(d)
        val vitals = JSONArray()
        for (v in s.vitals) {
            vitals.put(
                JSONObject().put("id", v.id).put("ts", v.ts).put("type", v.type.name).put("a", v.a).put("b", v.b)
            )
        }
        val moods = JSONObject()
        for ((k, v) in s.moods) moods.put(k, v)
        return JSONObject().put("entries", entries).put("reminders", rems).put("done", done)
            .put("vitals", vitals).put("moods", moods)
    }

    private fun decode(o: JSONObject): AppState {
        val entries = mutableListOf<Entry>()
        val ea = o.optJSONArray("entries") ?: JSONArray()
        for (i in 0 until ea.length()) {
            val j = ea.getJSONObject(i)
            val za = j.getJSONArray("zones")
            val zones = (0 until za.length()).map { k ->
                val zj = za.getJSONObject(k)
                ZoneRef(zj.getString("id"), zj.getString("label"))
            }
            entries.add(
                Entry(
                    j.getString("id"), j.getLong("ts"), zones, j.getInt("sev"),
                    strings(j, "kinds"), j.optString("note", ""),
                    strings(j, "triggers"), strings(j, "extras"), j.optString("since", ""),
                )
            )
        }
        val rems = mutableListOf<Reminder>()
        val ra = o.optJSONArray("reminders") ?: JSONArray()
        for (i in 0 until ra.length()) {
            val j = ra.getJSONObject(i)
            rems.add(
                Reminder(
                    id = j.getString("id"),
                    title = j.getString("title"),
                    type = RemType.valueOf(j.getString("type")),
                    date = LocalDate.parse(j.getString("date")),
                    time = LocalTime.parse(j.getString("time")),
                    repeat = Repeat.valueOf(j.getString("repeat")),
                    note = j.optString("note", ""),
                    dose = j.optString("dose", ""),
                    how = j.optString("how", ""),
                    place = j.optString("place", ""),
                    until = j.optString("until", "").takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) },
                )
            )
        }
        val done = mutableSetOf<String>()
        val da = o.optJSONArray("done") ?: JSONArray()
        for (i in 0 until da.length()) done.add(da.getString(i))
        val vitals = mutableListOf<Vital>()
        val va = o.optJSONArray("vitals") ?: JSONArray()
        for (i in 0 until va.length()) {
            val j = va.getJSONObject(i)
            vitals.add(Vital(j.getString("id"), j.getLong("ts"), VitalType.valueOf(j.getString("type")), j.getDouble("a"), j.optDouble("b", 0.0)))
        }
        val moods = mutableMapOf<String, Int>()
        val mo = o.optJSONObject("moods")
        if (mo != null) {
            val it = mo.keys()
            while (it.hasNext()) {
                val k = it.next()
                moods[k] = mo.getInt(k)
            }
        }
        return AppState(entries, rems, done, vitals, moods)
    }
}
