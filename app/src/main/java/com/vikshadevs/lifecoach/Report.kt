package com.vikshadevs.lifecoach

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/** Gemini ka free API key aur model naam (Settings me badal sakte ho). */
class AiPrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("lifecoach", Context.MODE_PRIVATE)

    var geminiKey: String
        get() = sp.getString("gemini_key", "") ?: ""
        set(v) {
            sp.edit().putString("gemini_key", v.trim()).apply()
        }

    var geminiModel: String
        get() = sp.getString("gemini_model", "gemini-2.5-flash") ?: "gemini-2.5-flash"
        set(v) {
            sp.edit().putString("gemini_model", v.trim()).apply()
        }
}

fun hasUsageAccess(ctx: Context): Boolean {
    return try {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        mode == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) {
        false
    }
}

object ReportBuilder {
    private fun appLabel(pm: PackageManager, pkg: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }

    /** (app naam, total milliseconds) pichhle `days` dino ka, sabse zyada use hone wale pehle. */
    private fun topApps(ctx: Context, days: Int): List<Pair<String, Long>> {
        if (!hasUsageAccess(ctx)) return emptyList()
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val end = System.currentTimeMillis()
        val start = end - days * 24L * 3600L * 1000L
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end) ?: return emptyList()
        val pm = ctx.packageManager
        val totals = HashMap<String, Long>()
        for (s in stats) totals[s.packageName] = (totals[s.packageName] ?: 0L) + s.totalTimeInForeground
        return totals.entries
            .filter { it.value > 60_000L && it.key != ctx.packageName && pm.getLaunchIntentForPackage(it.key) != null }
            .sortedByDescending { it.value }
            .take(6)
            .map { appLabel(pm, it.key) to it.value }
    }

    private fun hm(ms: Long): String {
        val m = ms / 60000
        return "${m / 60}h ${m % 60}m"
    }

    fun build(ctx: Context): String {
        val p = Prefs(ctx)
        val target = p.stepTarget()
        val sb = StringBuilder()
        sb.appendLine("PICHHLE 7 DIN KA DATA")

        var stepsSum = 0
        var metDays = 0
        var kcalSum = 0
        var protSum = 0
        var waterSum = 0
        var foodDays = 0
        for (i in 6 downTo 0) {
            val d = LocalDate.now().minusDays(i.toLong()).toString()
            val st = if (i == 0) StepStore.stepsToday(ctx) else StepStore.stepsOn(ctx, d)
            val foods = p.food(d)
            val k = foods.sumOf { it.kcal }
            val pr = foods.sumOf { it.protein }
            val w = p.water(d)
            sb.appendLine("$d: steps $st, kcal $k, protein ${pr}g, paani $w glass")
            stepsSum += st
            if (st >= target) metDays++
            if (foods.isNotEmpty()) {
                foodDays++
                kcalSum += k
                protSum += pr
            }
            waterSum += w
        }
        val avgSteps = stepsSum / 7
        val avgKcal = if (foodDays > 0) kcalSum / foodDays else 0
        val avgProt = if (foodDays > 0) protSum / foodDays else 0
        val avgWater = waterSum / 7

        sb.appendLine()
        sb.appendLine("Average steps: $avgSteps (target $target), target pura hua $metDays/7 din")
        sb.appendLine("Khana log hua $foodDays/7 din. Average: $avgKcal kcal (target ${p.calTarget}), $avgProt g protein (target ${p.proteinTarget})")
        sb.appendLine("Average paani: $avgWater glass (target ${p.waterTarget})")

        val w = p.weights()
        if (w.size >= 2) {
            val first = w.first()
            val last = w.last()
            sb.appendLine(String.format("Weight: %.1f kg (%s) se %.1f kg (%s), change %+.1f kg", first.kg, first.day, last.kg, last.day, last.kg - first.kg))
        } else if (w.size == 1) {
            sb.appendLine("Weight: ${w[0].kg} kg (sirf ek entry)")
        } else {
            sb.appendLine("Weight: koi entry nahi")
        }

        val apps = topApps(ctx, 7)
        if (apps.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("PHONE USE (7 din total)")
            apps.forEach { sb.appendLine("${it.first}: ${hm(it.second)}") }
        } else if (!hasUsageAccess(ctx)) {
            sb.appendLine()
            sb.appendLine("Phone use: Usage access allow nahi hai, isliye screen time nahi mila.")
        }

        sb.appendLine()
        sb.appendLine("SEEDHI BAAT")
        var any = false
        fun say(s: String) {
            sb.appendLine("- $s")
            any = true
        }
        if (avgSteps < target * 6 / 10) say("Steps target se bahut kam hain. Subah ki 3,000 steps wali walk skip mat karo, wahi sabse zyada fark dalti hai.")
        else if (metDays < 4) say("Target sirf $metDays din pura hua. Lock time se pehle ki walk (8:30 PM) pakki karo.")
        if (foodDays < 4) say("Khana sirf $foodDays din log hua. Bina log ke weight loss ka andaza nahi lagta.")
        if (foodDays >= 3 && avgKcal > p.calTarget + 200) say("Average calories target se $avgKcal vs ${p.calTarget}. Chai/chini aur bahar ka tala hua kam karo.")
        if (foodDays >= 3 && avgProt < p.proteinTarget * 8 / 10) say("Protein kam hai ($avgProt g). Soya chunks, paneer, dal aur dahi badhao.")
        if (avgWater < p.waterTarget * 6 / 10) say("Paani kam pi rahe ho. Desk pe bottle rakho.")
        if (apps.isNotEmpty() && apps.first().second / 7 > 2 * 3600 * 1000L) say("${apps.first().first} par roz 2 ghante se zyada ja raha hai.")
        if (!any) say("Is hafte sab theek chal raha hai, aise hi chalte raho.")

        return sb.toString().trimEnd()
    }
}

object Gemini {
    /** Network ka kaam hai, background thread se hi bulao. Text ya error message lautata hai. */
    fun analyze(ctx: Context, summary: String): String {
        val a = AiPrefs(ctx)
        if (a.geminiKey.isBlank()) return "Pehle Settings me Gemini API key daalo."

        val prompt = "Tum ek seedhe-saade lekin pyaare personal coach ho. User 21 saal ka hai, wazan lagbhag 100 kg, " +
            "height 5.9 ft, pure vegetarian, akela rehta hai, tele-calling job (9 se 8 baitha rehta hai). " +
            "Neeche uska pichhle 7 din ka data hai. Hinglish me, 200 shabdon se kam me batao: " +
            "1) 3 sabse badi problems, 2) agle hafte ke liye 3 chhote aur karne layak kadam, 3) ek line ka hausla. " +
            "Doctor jaisi salah mat do.\n\n" + summary

        val body = JSONObject().put(
            "contents",
            JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))),
        )
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/${a.geminiModel}:generateContent")
        val c = url.openConnection() as HttpURLConnection
        return try {
            c.requestMethod = "POST"
            c.connectTimeout = 15_000
            c.readTimeout = 60_000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("x-goog-api-key", a.geminiKey)
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                "Gemini error $code. Key ya model naam check karo.\n" + text.take(300)
            } else {
                JSONObject(text).getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        } finally {
            c.disconnect()
        }
    }
}
