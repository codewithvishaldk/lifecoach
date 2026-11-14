package com.vikshadevs.lifecoach

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

object DateUtil {
    fun todayKey(): String = LocalDate.now().toString()

    fun yesterdayKey(): String = LocalDate.now().minusDays(1).toString()

    fun nowMinutes(): Int {
        val t = LocalTime.now()
        return t.hour * 60 + t.minute
    }

    fun fmt(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        val suffix = if (h < 12) "AM" else "PM"
        val h12 = if (h % 12 == 0) 12 else h % 12
        return String.format("%d:%02d %s", h12, m, suffix)
    }
}

data class FoodEntry(val name: String, val kcal: Int, val protein: Int)

data class WeightEntry(val day: String, val kg: Float)

data class FoodItem(val name: String, val kcal: Int, val protein: Int)

// Approx values, Indian home food. Phase 2 me edit karne ka option aayega.
val FOOD_MENU = listOf(
    FoodItem("Moong dal chilla (1)", 110, 6),
    FoodItem("Besan chilla (1)", 120, 6),
    FoodItem("Poha (1 plate)", 250, 5),
    FoodItem("Roti (1)", 100, 3),
    FoodItem("Dal (1 katori)", 150, 8),
    FoodItem("Sabzi (1 katori)", 120, 3),
    FoodItem("Paneer 100 g", 265, 18),
    FoodItem("Soya chunks 50 g (dry)", 170, 26),
    FoodItem("Dahi (1 katori)", 90, 5),
    FoodItem("Doodh (1 glass)", 130, 7),
    FoodItem("Fruit (1)", 90, 1),
    FoodItem("Badam (10)", 70, 3),
    FoodItem("Bhuna chana 30 g", 110, 6),
    FoodItem("Makhana (1 katori)", 105, 3),
    FoodItem("Khichdi (1 plate)", 300, 10),
    FoodItem("Chawal (1 katori)", 190, 4),
    FoodItem("Salad (1 plate)", 40, 2),
    FoodItem("Sattu drink (1 glass)", 150, 9),
    FoodItem("Chai (1 cup, chini ke saath)", 60, 1),
    FoodItem("Samosa / bahar ka tala hua", 250, 4),
)

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("lifecoach", Context.MODE_PRIVATE)

    // ---------- Lock ----------
    var lockEnabled: Boolean
        get() = sp.getBoolean("lock_on", true)
        set(v) {
            sp.edit().putBoolean("lock_on", v).apply()
        }

    var lockMinutes: Int
        get() = sp.getInt("lock_min", 22 * 60)
        set(v) {
            sp.edit().putInt("lock_min", v).apply()
        }

    var emergencyPass: String
        get() = sp.getString("emg_pass", "") ?: ""
        set(v) {
            sp.edit().putString("emg_pass", v).apply()
        }

    var emergencyDay: String
        get() = sp.getString("emg_day", "") ?: ""
        set(v) {
            sp.edit().putString("emg_day", v).apply()
        }

    var testLockUntil: Long
        get() = sp.getLong("test_lock_until", 0L)
        set(v) {
            sp.edit().putLong("test_lock_until", v).apply()
        }

    // ---------- Office ----------
    var officeStart: Int
        get() = sp.getInt("office_start", 9 * 60)
        set(v) {
            sp.edit().putInt("office_start", v).apply()
        }

    var officeEnd: Int
        get() = sp.getInt("office_end", 20 * 60)
        set(v) {
            sp.edit().putInt("office_end", v).apply()
        }

    var sundayOff: Boolean
        get() = sp.getBoolean("sunday_off", true)
        set(v) {
            sp.edit().putBoolean("sunday_off", v).apply()
        }

    fun isOfficeDay(date: LocalDate): Boolean = !(sundayOff && date.dayOfWeek == DayOfWeek.SUNDAY)

    fun isOfficeTime(minutes: Int): Boolean =
        isOfficeDay(LocalDate.now()) && minutes >= officeStart && minutes < officeEnd

    // ---------- Steps ----------
    var baseTarget: Int
        get() = sp.getInt("base_target", 6000)
        set(v) {
            sp.edit().putInt("base_target", v).apply()
        }

    var ramp: Boolean
        get() = sp.getBoolean("ramp", true)
        set(v) {
            sp.edit().putBoolean("ramp", v).apply()
        }

    private fun startDay(): LocalDate {
        val s = sp.getString("start_day", null)
        if (s == null) {
            val today = LocalDate.now()
            sp.edit().putString("start_day", today.toString()).apply()
            return today
        }
        return LocalDate.parse(s)
    }

    /** Hafta 1 = baseTarget, phir har hafte +1000, max 10,000 (ya baseTarget agar usse zyada ho). */
    fun stepTarget(): Int {
        val base = baseTarget.coerceAtLeast(1000)
        if (!ramp) return base
        val weeks = (ChronoUnit.DAYS.between(startDay(), LocalDate.now()) / 7).toInt().coerceAtLeast(0)
        return maxOf(base, minOf(10000, base + 1000 * weeks))
    }

    // ---------- Diet targets ----------
    var calTarget: Int
        get() = sp.getInt("cal_target", 1900)
        set(v) {
            sp.edit().putInt("cal_target", v).apply()
        }

    var proteinTarget: Int
        get() = sp.getInt("protein_target", 105)
        set(v) {
            sp.edit().putInt("protein_target", v).apply()
        }

    var waterTarget: Int
        get() = sp.getInt("water_target", 12)
        set(v) {
            sp.edit().putInt("water_target", v).apply()
        }

    // ---------- Girlfriend / WhatsApp ----------
    var gfName: String
        get() = sp.getString("gf_name", "") ?: ""
        set(v) {
            sp.edit().putString("gf_name", v).apply()
        }

    var gfNumber: String
        get() = sp.getString("gf_number", "") ?: ""
        set(v) {
            sp.edit().putString("gf_number", v).apply()
        }

    var gmMsg: String
        get() = sp.getString("gm_msg", "Good morning ❤️ Aaj ka din mast jaye!") ?: ""
        set(v) {
            sp.edit().putString("gm_msg", v).apply()
        }

    var gaMsg: String
        get() = sp.getString("ga_msg", "Good afternoon ❤️ Lunch kiya?") ?: ""
        set(v) {
            sp.edit().putString("ga_msg", v).apply()
        }

    var waAutoSend: Boolean
        get() = sp.getBoolean("wa_auto", true)
        set(v) {
            sp.edit().putBoolean("wa_auto", v).apply()
        }

    var waAutoSendUntil: Long
        get() = sp.getLong("wa_auto_until", 0L)
        set(v) {
            sp.edit().putLong("wa_auto_until", v).apply()
        }

    // ---------- Reminders ----------
    fun reminderOn(group: String): Boolean = sp.getBoolean("rem_on_$group", true)

    fun setReminderOn(group: String, on: Boolean) {
        sp.edit().putBoolean("rem_on_$group", on).apply()
    }

    fun reminderMinutes(def: ReminderDef): Int = sp.getInt("rem_t_${def.id}", def.hour * 60 + def.minute)

    fun setReminderMinutes(id: Int, minutes: Int) {
        sp.edit().putInt("rem_t_$id", minutes).apply()
    }

    // ---------- Food ----------
    fun food(day: String = DateUtil.todayKey()): List<FoodEntry> {
        val arr = JSONArray(sp.getString("food_$day", "[]") ?: "[]")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            FoodEntry(o.getString("n"), o.getInt("k"), o.getInt("p"))
        }
    }

    private fun saveFood(day: String, list: List<FoodEntry>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("n", it.name).put("k", it.kcal).put("p", it.protein)) }
        sp.edit().putString("food_$day", arr.toString()).apply()
    }

    fun addFood(e: FoodEntry) {
        val d = DateUtil.todayKey()
        saveFood(d, food(d) + e)
    }

    fun removeFood(index: Int) {
        val d = DateUtil.todayKey()
        val l = food(d).toMutableList()
        if (index in l.indices) {
            l.removeAt(index)
            saveFood(d, l)
        }
    }

    // ---------- Water ----------
    fun water(day: String = DateUtil.todayKey()): Int = sp.getInt("water_$day", 0)

    fun addWater(delta: Int) {
        val d = DateUtil.todayKey()
        sp.edit().putInt("water_$d", (water(d) + delta).coerceIn(0, 40)).apply()
    }

    // ---------- Weight ----------
    fun weights(): List<WeightEntry> {
        val arr = JSONArray(sp.getString("weights", "[]") ?: "[]")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            WeightEntry(o.getString("d"), o.getDouble("k").toFloat())
        }
    }

    fun addWeight(kg: Float) {
        val today = DateUtil.todayKey()
        val list = weights().filter { it.day != today } + WeightEntry(today, kg)
        val arr = JSONArray()
        list.sortedBy { it.day }.forEach { arr.put(JSONObject().put("d", it.day).put("k", it.kg.toDouble())) }
        sp.edit().putString("weights", arr.toString()).apply()
    }
}

/** Phone ka step counter sensor cumulative hota hai; yahan se "aaj ke steps" nikalte hain. */
object StepStore {
    private fun sp(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences("lifecoach_steps", Context.MODE_PRIVATE)

    fun stepsToday(ctx: Context): Int {
        val p = sp(ctx)
        return if (p.getString("day", "") == DateUtil.todayKey()) p.getInt("saved", 0) else 0
    }

    fun stepsOn(ctx: Context, day: String): Int = sp(ctx).getInt("hist_$day", 0)

    fun onRaw(ctx: Context, raw: Long): Int {
        val p = sp(ctx)
        val today = DateUtil.todayKey()
        val day = p.getString("day", "") ?: ""
        val last = p.getLong("last", raw)
        var base = p.getLong("base", raw)
        var saved = p.getInt("saved", 0)

        if (day != today) {
            base = if (day == DateUtil.yesterdayKey() && raw >= last) last else raw
            saved = 0
        } else if (raw < last) {
            // phone restart hua, sensor 0 se shuru
            base = raw - saved
        }
        saved = (raw - base).toInt().coerceAtLeast(0)

        p.edit()
            .putString("day", today)
            .putLong("base", base)
            .putLong("last", raw)
            .putInt("saved", saved)
            .putInt("hist_$today", saved)
            .apply()
        return saved
    }
}

object LockPolicy {
    fun isLocked(ctx: Context): Boolean {
        val p = Prefs(ctx)
        if (p.testLockUntil > System.currentTimeMillis()) return true
        // Safety: emergency passcode set nahi hai to lock kabhi nahi lagega.
        if (!p.lockEnabled || p.emergencyPass.length < 8) return false
        if (p.emergencyDay == DateUtil.todayKey()) return false
        val m = DateUtil.nowMinutes()
        if (m < p.lockMinutes) return false
        if (p.isOfficeTime(m)) return false
        return StepStore.stepsToday(ctx) < p.stepTarget()
    }
}
