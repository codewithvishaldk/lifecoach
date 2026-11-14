package com.vikshadevs.lifecoach

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

class StepService : Service(), SensorEventListener {
    private var sensorManager: SensorManager? = null
    private var lastNotify = 0L

    override fun onCreate() {
        super.onCreate()
        Notifs.ensureChannels(this)
        ServiceCompat.startForeground(
            this,
            Notifs.ID_STEPS,
            Notifs.stepsNotification(this, StepStore.stepsToday(this)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH,
        )
        val sm = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorManager = sm
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sensor != null) {
            sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val steps = StepStore.onRaw(this, event.values[0].toLong())
        val now = System.currentTimeMillis()
        if (now - lastNotify > 15_000) {
            lastNotify = now
            try {
                getSystemService(NotificationManager::class.java)
                    .notify(Notifs.ID_STEPS, Notifs.stepsNotification(this, steps))
            } catch (_: SecurityException) {
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        sensorManager?.unregisterListener(this)
        super.onDestroy()
    }

    companion object {
        fun start(ctx: Context) {
            if (Build.VERSION.SDK_INT >= 29 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED
            ) return
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, StepService::class.java))
            } catch (_: Exception) {
                // Background se start allowed nahi tha; app khulne par dobara try hoga.
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Scheduler.scheduleAll(context)
        StepService.start(context)
    }
}

fun isAccessibilityOn(ctx: Context): Boolean {
    val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?: return false
    val cn = ComponentName(ctx, LockAccessibilityService::class.java)
    return enabled.split(':').any {
        it.equals(cn.flattenToString(), true) || it.equals(cn.flattenToShortString(), true)
    }
}

/**
 * 1) Lock: steps poore nahi hue aur lock time aa gaya to poori screen pe overlay (sirf call + emergency unlock khule).
 * 2) WhatsApp auto-send: notification se chat khulne par Send button dabata hai.
 */
class LockAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: View? = null
    private var remainView: TextView? = null
    private var lastPkg = ""
    private var lastCls = ""
    private var imePackages: Set<String> = emptySet()

    private val tick = object : Runnable {
        override fun run() {
            try {
                val rootPkg = rootInActiveWindow?.packageName?.toString()
                if (rootPkg != null && rootPkg != lastPkg && !isNeutral(rootPkg, "")) {
                    lastPkg = rootPkg
                    lastCls = ""
                }
                evaluate()
            } catch (_: Exception) {
            }
            handler.postDelayed(this, 3000)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        imePackages = try {
            getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
        StepService.start(this)
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        hideOverlay()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val cls = event.className?.toString() ?: ""
            if (!isNeutral(pkg, cls)) {
                lastPkg = pkg
                lastCls = cls
                evaluate()
            }
        }
        if (pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b") {
            tryAutoSend()
        }
    }

    /** Ye windows (keyboard, status bar, hamare khud ke overlay/trampoline) lock ka faisla nahi badalte. */
    private fun isNeutral(pkg: String, cls: String): Boolean {
        if (pkg == "com.android.systemui" || pkg == "android") return true
        if (pkg in imePackages) return true
        if (pkg == packageName) {
            return !(cls.endsWith("EmergencyUnlockActivity") || cls.endsWith("MainActivity"))
        }
        return false
    }

    private fun allowedPackages(): Set<String> {
        val s = mutableSetOf(
            "com.android.dialer", "com.google.android.dialer", "com.android.incallui",
            "com.android.phone", "com.android.server.telecom", "com.android.contacts",
            "com.google.android.contacts", "com.android.emergency",
        )
        try {
            getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { s.add(it) }
        } catch (_: Exception) {
        }
        return s
    }

    private fun evaluate() {
        val locked = LockPolicy.isLocked(this)
        val allowed = lastPkg in allowedPackages() ||
            (lastPkg == packageName && lastCls.endsWith("EmergencyUnlockActivity"))
        if (locked && !allowed) showOverlay() else hideOverlay()
    }

    private fun remainingText(): String {
        val p = Prefs(this)
        val left = (p.stepTarget() - StepStore.stepsToday(this)).coerceAtLeast(0)
        return if (p.testLockUntil > System.currentTimeMillis()) "Lock test chal raha hai" else "$left steps baaki"
    }

    private fun showOverlay() {
        if (overlay != null) {
            remainView?.text = remainingText()
            return
        }
        val v = buildOverlay()
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).addView(v, lp)
            overlay = v
        } catch (_: Exception) {
        }
    }

    private fun hideOverlay() {
        val v = overlay ?: return
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (_: Exception) {
        }
        overlay = null
        remainView = null
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(text: String, sp: Float, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = sp
        setTextColor(0xFFFFFFFF.toInt())
        gravity = Gravity.CENTER
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(8), 0, dp(8))
    }

    private fun buildOverlay(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xF2101820.toInt())
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }
        root.addView(label("🔒 Phone lock ON", 26f, true))
        val remain = label(remainingText(), 40f, true)
        remainView = remain
        root.addView(remain)
        root.addView(label("Chalo! Steps poore karo, phone apne aap khul jayega.", 16f))

        val call = Button(this).apply {
            text = "📞 Phone / Call"
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        val emergency = Button(this).apply {
            text = "Emergency unlock"
            setOnClickListener {
                startActivity(
                    Intent(this@LockAccessibilityService, EmergencyUnlockActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
        root.addView(call)
        root.addView(emergency)
        return root
    }

    // ---------- WhatsApp auto-send ----------
    private fun tryAutoSend() {
        val p = Prefs(this)
        if (!p.waAutoSend || p.waAutoSendUntil < System.currentTimeMillis()) return
        val root = rootInActiveWindow ?: return
        var node: AccessibilityNodeInfo? =
            root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")?.firstOrNull()
        if (node == null) node = findByDescription(root, 0)
        var n = node
        while (n != null && !n.isClickable) n = n.parent
        if (n != null && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            p.waAutoSendUntil = 0L
            Toast.makeText(this, "Message bhej diya ✅", Toast.LENGTH_SHORT).show()
        }
    }

    private fun findByDescription(n: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (n == null || depth > 12) return null
        val d = n.contentDescription?.toString()?.lowercase()
        if (d == "send" || d == "भेजें") return n
        for (i in 0 until n.childCount) {
            val r = findByDescription(n.getChild(i), depth + 1)
            if (r != null) return r
        }
        return null
    }
}
