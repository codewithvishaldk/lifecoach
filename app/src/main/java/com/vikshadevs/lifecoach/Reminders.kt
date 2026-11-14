package com.vikshadevs.lifecoach

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class ReminderDef(
    val id: Int,
    val group: String,
    val label: String,
    val hour: Int,
    val minute: Int,
    val kind: String,
    val title: String,
    val body: String,
    val officeOnly: Boolean = false,
    val dow: DayOfWeek? = null,
)

object Reminders {
    val all: List<ReminderDef> = buildList {
        add(ReminderDef(1, "walk_am", "Subah ki walk", 6, 30, "plain", "Subah ki walk 🚶", "Uth jao! 3,000 steps abhi nipta lo, phir din halka lagega."))
        add(ReminderDef(2, "gm", "Good Morning message", 7, 30, "gm", "Good Morning bhejo ❤️", "Usko good morning bol do. Tap karke WhatsApp kholo."))
        add(ReminderDef(3, "breakfast", "Breakfast + log", 8, 30, "plain", "Breakfast ka time 🥣", "Chilla/poha + doodh. Khane ka log bhi kar do."))
        add(ReminderDef(4, "ga", "Good Afternoon message", 13, 15, "ga", "Good Afternoon bhejo ❤️", "Lunch ho gaya? Usse bhi pooch lo."))
        add(ReminderDef(5, "lunch_walk", "Lunch walk", 13, 30, "plain", "Lunch walk 🚶", "10 minute chal lo. Baithe baithe call se break."))
        listOf(10, 11, 12, 14, 15, 16, 17, 18, 19).forEach { h ->
            add(
                ReminderDef(
                    100 + h, "stand", "Office me khade ho (har ghante)", h, 0, "plain",
                    "Khade ho jao 🧍", "2-3 minute chalo aur paani piyo 💧", officeOnly = true,
                )
            )
        }
        add(ReminderDef(6, "dinner", "Halka dinner", 20, 15, "plain", "Halka dinner 🥗", "2 roti + sabzi/dal + salad. Raat ko bhari khana nahi."))
        add(ReminderDef(7, "evening_walk", "Raat ki walk", 20, 30, "steps", "Raat ki walk 🚶", ""))
        add(ReminderDef(8, "warn", "Steps warning", 21, 0, "warn", "⚠️ Steps kam hain", ""))
        add(ReminderDef(9, "lock_soon", "Lock se pehle alert", 21, 45, "lock_soon", "🔒 Phone jaldi lock hoga", ""))
        add(ReminderDef(12, "sleep", "Sone ka time", 22, 30, "plain", "Sone ka time 😴", "Kal subah walk hai. Time pe so jao."))
        add(ReminderDef(13, "weigh", "Weekly weight (Sunday)", 7, 0, "plain", "Weight check ⚖️", "Subah khali pet weight naap ke app me daalo.", dow = DayOfWeek.SUNDAY))
        add(ReminderDef(14, "report", "Weekly report (Sunday)", 20, 0, "plain", "Weekly report 📊", "Report tab me report banao aur agle hafte ka plan dekho.", dow = DayOfWeek.SUNDAY))
    }
}

object Notifs {
    const val CH_STEPS = "steps"
    const val CH_REM = "reminders"
    const val ID_STEPS = 1

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_STEPS, "Steps counter", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_REM, "Reminders", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun stepsNotification(ctx: Context, steps: Int): Notification {
        val target = Prefs(ctx).stepTarget()
        return NotificationCompat.Builder(ctx, CH_STEPS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(String.format("Aaj: %,d / %,d steps", steps, target))
            .setContentText(if (steps >= target) "Target pura ✅" else "${target - steps} steps baaki")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(ctx))
            .build()
    }

    fun reminder(ctx: Context, def: ReminderDef) {
        ensureChannels(ctx)
        val p = Prefs(ctx)
        val steps = StepStore.stepsToday(ctx)
        val target = p.stepTarget()
        val left = (target - steps).coerceAtLeast(0)
        var title = def.title
        var body = def.body

        when (def.kind) {
            "steps" -> {
                if (left == 0) {
                    title = "Target pura ✅"
                    body = "Aaj $steps steps ho gaye. Shabaash!"
                } else {
                    body = "Abhi tak $steps steps. $left baaki hain, chalo!"
                }
            }
            "warn" -> {
                if (steps >= target * 6 / 10) return
                body = "Sirf $steps/$target steps hue. ${DateUtil.fmt(p.lockMinutes)} tak $left steps baaki hain, warna phone lock."
            }
            "lock_soon" -> {
                if (left == 0) return
                body = "$left steps baaki. Abhi chalna shuru karo, warna phone lock ho jayega."
            }
        }

        val waMsg = when (def.kind) {
            "gm" -> p.gmMsg
            "ga" -> p.gaMsg
            else -> null
        }

        val builder = NotificationCompat.Builder(ctx, CH_REM)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        if (waMsg != null && p.gfNumber.isNotBlank()) {
            val pi = PendingIntent.getActivity(
                ctx, def.id,
                Intent(ctx, WaActivity::class.java).putExtra("msg", waMsg),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.setContentIntent(pi)
            builder.addAction(0, "WhatsApp ❤️", pi)
        } else {
            builder.setContentIntent(openApp(ctx))
        }

        try {
            ctx.getSystemService(NotificationManager::class.java).notify(1000 + def.id, builder.build())
        } catch (_: SecurityException) {
        }
    }
}

object Scheduler {
    fun scheduleAll(ctx: Context) {
        Reminders.all.forEach { schedule(ctx, it) }
    }

    private fun pending(ctx: Context, def: ReminderDef): PendingIntent = PendingIntent.getBroadcast(
        ctx, def.id,
        Intent(ctx, ReminderReceiver::class.java).putExtra("id", def.id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun schedule(ctx: Context, def: ReminderDef) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pending(ctx, def)
        val p = Prefs(ctx)
        if (!p.reminderOn(def.group)) {
            am.cancel(pi)
            return
        }
        val at = nextTrigger(p, def)
        try {
            val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun nextTrigger(p: Prefs, def: ReminderDef): Long {
        val m = p.reminderMinutes(def)
        val now = LocalDateTime.now()
        for (i in 0..8) {
            val date: LocalDate = LocalDate.now().plusDays(i.toLong())
            if (def.dow != null && date.dayOfWeek != def.dow) continue
            if (def.officeOnly && !p.isOfficeDay(date)) continue
            val dt = date.atTime(m / 60, m % 60)
            if (dt.isAfter(now)) {
                return dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        }
        return System.currentTimeMillis() + 24L * 60 * 60 * 1000
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", -1)
        val def = Reminders.all.firstOrNull { it.id == id } ?: return
        if (Prefs(context).reminderOn(def.group)) {
            Notifs.reminder(context, def)
        }
        Scheduler.schedule(context, def)
        StepService.start(context)
    }
}

object Wa {
    /** WhatsApp me girlfriend ki chat khol ke message pehle se likh deta hai (auto-send ON ho to bhej bhi deta hai). */
    fun open(ctx: Context, msg: String) {
        val p = Prefs(ctx)
        var num = p.gfNumber.filter { it.isDigit() }
        if (num.length == 10) num = "91$num"
        if (num.isEmpty()) {
            Toast.makeText(ctx, "Pehle Settings me WhatsApp number daalo", Toast.LENGTH_LONG).show()
            return
        }
        if (p.waAutoSend) p.waAutoSendUntil = System.currentTimeMillis() + 25_000
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$num?text=" + Uri.encode(msg)))
            .setPackage("com.whatsapp")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(i)
        } catch (_: Exception) {
            Toast.makeText(ctx, "WhatsApp nahi mila", Toast.LENGTH_LONG).show()
        }
    }
}
