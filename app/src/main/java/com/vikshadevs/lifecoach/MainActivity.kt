package com.vikshadevs.lifecoach

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppTheme { AppRoot() } }
    }

    override fun onResume() {
        super.onResume()
        Notifs.ensureChannels(this)
        Scheduler.scheduleAll(this)
        StepService.start(this)
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun AppRoot() {
    var tab by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    val refresh: () -> Unit = { tick++ }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            tick++
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Home") },
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                    label = { Text("Khana") },
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                    label = { Text("Reminders") },
                )
                NavigationBarItem(
                    selected = tab == 3, onClick = { tab = 3 },
                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    label = { Text("Report") },
                )
                NavigationBarItem(
                    selected = tab == 4, onClick = { tab = 4 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                )
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> HomeScreen(tick, refresh)
                1 -> FoodScreen(tick, refresh)
                2 -> RemindersScreen(tick, refresh)
                3 -> ReportScreen(tick)
                else -> SettingsScreen(tick, refresh)
            }
        }
    }
}

fun lockStatus(ctx: Context, p: Prefs, steps: Int, target: Int): String = when {
    p.emergencyPass.length < 8 -> "⚠️ Lock abhi OFF hai: Settings me Emergency passcode set karo"
    !p.lockEnabled -> "Lock band hai (Settings me ON kar sakte ho)"
    p.emergencyDay == DateUtil.todayKey() -> "Aaj emergency unlock use hua, lock nahi lagega"
    steps >= target -> "✅ Target pura! Aaj lock nahi lagega"
    LockPolicy.isLocked(ctx) -> "🔒 Lock abhi ON hai"
    else -> "🔒 ${DateUtil.fmt(p.lockMinutes)} ko phone lock hoga agar target pura nahi hua"
}

// ------------------------------------------------------------------ HOME

@Composable
fun HomeScreen(tick: Int, refresh: () -> Unit) {
    val ctx = LocalContext.current
    val p = Prefs(ctx)
    val steps = StepStore.stepsToday(ctx)
    val target = p.stepTarget()
    val left = (target - steps).coerceAtLeast(0)
    val foods = p.food()
    val kcal = foods.sumOf { it.kcal }
    val protein = foods.sumOf { it.protein }
    val weights = p.weights()
    var showWeight by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Aaj • ${LocalDate.now()}", style = MaterialTheme.typography.titleMedium)

        SetupCard(tick)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Steps", style = MaterialTheme.typography.titleMedium)
                Text(
                    String.format("%,d", steps),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                )
                LinearProgressIndicator(
                    progress = { (steps.toFloat() / target).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Target $target • baaki $left")
                Text(lockStatus(ctx, p, steps, target))
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Khana aur paani", style = MaterialTheme.typography.titleMedium)
                Text("Calories: $kcal / ${p.calTarget} kcal")
                LinearProgressIndicator(
                    progress = { (kcal.toFloat() / p.calTarget).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Protein: $protein / ${p.proteinTarget} g")
                LinearProgressIndicator(
                    progress = { (protein.toFloat() / p.proteinTarget).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Paani: ${p.water()} / ${p.waterTarget} glass", Modifier.weight(1f))
                    OutlinedButton(onClick = { p.addWater(-1); refresh() }) { Text("−1") }
                    Button(
                        onClick = { p.addWater(1); refresh() },
                        modifier = Modifier.padding(start = 8.dp),
                    ) { Text("+1 glass") }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Weight", style = MaterialTheme.typography.titleMedium)
                val last = weights.lastOrNull()
                val first = weights.firstOrNull()
                if (last == null) {
                    Text("Abhi tak weight nahi daala")
                } else {
                    val diff = if (first != null && weights.size > 1) {
                        String.format("  (%+.1f kg start se)", last.kg - first.kg)
                    } else ""
                    Text("${last.kg} kg$diff", style = MaterialTheme.typography.titleLarge)
                }
                OutlinedButton(onClick = { showWeight = true }) { Text("Aaj ka weight daalo") }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val name = p.gfName.ifBlank { "Girlfriend" }
                Text("$name ❤️", style = MaterialTheme.typography.titleMedium)
                if (p.gfNumber.isBlank()) {
                    Text("Settings me uska WhatsApp number daalo, phir yahan se ek tap me message jayega.")
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { Wa.open(ctx, p.gmMsg) }) { Text("Good Morning") }
                        Button(onClick = { Wa.open(ctx, p.gaMsg) }) { Text("Good Afternoon") }
                    }
                }
            }
        }
    }

    if (showWeight) {
        WeightDialog(
            onDismiss = { showWeight = false },
            onSave = {
                p.addWeight(it)
                showWeight = false
                refresh()
            },
        )
    }
}

@Composable
fun SetupCard(tick: Int) {
    val ctx = LocalContext.current
    val p = Prefs(ctx)

    val perms = buildList<String> {
        if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        StepService.start(ctx)
    }

    val permsOk = perms.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
    val accOk = isAccessibilityOn(ctx)
    val battOk = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    val overlayOk = AndroidSettings.canDrawOverlays(ctx)
    val passOk = p.emergencyPass.length >= 8

    if (permsOk && accOk && battOk && passOk && overlayOk) return

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Setup baaki hai", style = MaterialTheme.typography.titleMedium)

            if (!permsOk) {
                SetupRow("Steps + notification permission") {
                    Button(onClick = { launcher.launch(perms.toTypedArray()) }) { Text("Allow") }
                }
            }
            if (!accOk) {
                SetupRow("Accessibility ON karo (lock + WhatsApp ke liye zaroori)") {
                    Button(onClick = { ctx.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Kholo") }
                }
                Text(
                    "Agar option grey ho: App info kholo → upar ⋮ → \"Allow restricted settings\", phir dobara try karo.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = {
                    ctx.startActivity(
                        Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                    )
                }) { Text("App info kholo") }
            }
            if (!battOk) {
                SetupRow("Battery optimization band karo (steps background me chalte rahein)") {
                    Button(onClick = {
                        ctx.startActivity(
                            Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                        )
                    }) { Text("Allow") }
                }
            }
            if (!passOk) {
                Text("Settings tab me Emergency passcode set karo (kam se kam 8 characters). Iske bina lock ON nahi hoga.")
            }
            if (!overlayOk) {
                SetupRow("Display over other apps (optional, Emergency unlock ke liye backup)") {
                    OutlinedButton(onClick = {
                        ctx.startActivity(
                            Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                        )
                    }) { Text("Allow") }
                }
            }
        }
    }
}

@Composable
fun SetupRow(text: String, action: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, Modifier.weight(1f))
        action()
    }
}

@Composable
fun WeightDialog(onDismiss: () -> Unit, onSave: (Float) -> Unit) {
    var t by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aaj ka weight (kg)") },
        text = {
            OutlinedTextField(
                value = t,
                onValueChange = { v -> t = v.filter { c -> c.isDigit() || c == '.' }.take(5) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val kg = t.toFloatOrNull()
                if (kg != null && kg in 30f..300f) onSave(kg)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ------------------------------------------------------------------ FOOD

@Composable
fun FoodScreen(tick: Int, refresh: () -> Unit) {
    val ctx = LocalContext.current
    val p = Prefs(ctx)
    val foods = p.food()
    val kcal = foods.sumOf { it.kcal }
    val protein = foods.sumOf { it.protein }
    var showCustom by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Khana", style = MaterialTheme.typography.headlineSmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Aaj: $kcal / ${p.calTarget} kcal • $protein / ${p.proteinTarget} g protein", fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(
                    progress = { (kcal.toFloat() / p.calTarget).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Text("Aaj ka log", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        if (foods.isEmpty()) Text("Abhi kuch add nahi kiya")
        foods.forEachIndexed { i, f ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${f.name} • ${f.kcal} kcal • ${f.protein} g", Modifier.weight(1f))
                IconButton(onClick = { p.removeFood(i); refresh() }) {
                    Icon(Icons.Default.Delete, contentDescription = "Hatao")
                }
            }
        }

        Text("Tap karke add karo", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        FOOD_MENU.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.name)
                    Text("${item.kcal} kcal • ${item.protein} g protein", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = {
                    p.addFood(FoodEntry(item.name, item.kcal, item.protein))
                    refresh()
                }) { Text("+ Add") }
            }
        }
        Button(onClick = { showCustom = true }, modifier = Modifier.padding(top = 8.dp)) { Text("Custom item add karo") }
    }

    if (showCustom) {
        CustomFoodDialog(
            onDismiss = { showCustom = false },
            onSave = {
                p.addFood(it)
                showCustom = false
                refresh()
            },
        )
    }
}

@Composable
fun CustomFoodDialog(onDismiss: () -> Unit, onSave: (FoodEntry) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Naam") }, singleLine = true)
                OutlinedTextField(
                    value = kcal, onValueChange = { kcal = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("Calories (kcal)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = protein, onValueChange = { protein = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("Protein (g)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val k = kcal.toIntOrNull()
                if (name.isNotBlank() && k != null) onSave(FoodEntry(name.trim(), k, protein.toIntOrNull() ?: 0))
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ------------------------------------------------------------------ REMINDERS

@Composable
fun RemindersScreen(tick: Int, refresh: () -> Unit) {
    val ctx = LocalContext.current
    val p = Prefs(ctx)
    val groups = Reminders.all.groupBy { it.group }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Reminders", style = MaterialTheme.typography.headlineSmall)
        for ((g, defs) in groups) {
            val d = defs.first()
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(d.label, fontWeight = FontWeight.SemiBold)
                        if (defs.size == 1) {
                            val m = p.reminderMinutes(d)
                            TextButton(onClick = {
                                TimePickerDialog(ctx, { _, h, mi ->
                                    p.setReminderMinutes(d.id, h * 60 + mi)
                                    Scheduler.schedule(ctx, d)
                                    refresh()
                                }, m / 60, m % 60, false).show()
                            }) { Text(DateUtil.fmt(m) + "  (time badlo)") }
                        } else {
                            Text("10 AM se 7 PM, har ghante (lunch ke alawa)", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Switch(checked = p.reminderOn(g), onCheckedChange = {
                        p.setReminderOn(g, it)
                        Scheduler.scheduleAll(ctx)
                        refresh()
                    })
                }
            }
        }
    }
}

// ------------------------------------------------------------------ SETTINGS

@Composable
fun SettingsScreen(tick: Int, refresh: () -> Unit) {
    val ctx = LocalContext.current
    val p = Prefs(ctx)
    // Shaam 6 baje ke baad lock se jude settings badal nahi sakte (taaki "target kam kar deta hu" wala bahana na bane).
    val frozen = DateUtil.nowMinutes() >= 18 * 60
    var pass by remember { mutableStateOf(p.emergencyPass) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        if (frozen) {
            Text("🔐 Shaam 6 baje ke baad steps aur lock ki settings badal nahi sakte. Kal subah badlo.", style = MaterialTheme.typography.bodySmall)
        }

        SectionTitle("Steps")
        NumField("Roz ka starting step target", p.baseTarget, enabled = !frozen) { p.baseTarget = it; refresh() }
        SwitchRow("Har hafte +1000 steps (max 10,000)", p.ramp, enabled = !frozen) { p.ramp = it; refresh() }
        Text("Aaj ka target: ${p.stepTarget()} steps")

        SectionTitle("Phone lock")
        SwitchRow("Lock ON", p.lockEnabled, enabled = !frozen) { p.lockEnabled = it; refresh() }
        TimeRow("Lock time", p.lockMinutes, enabled = !frozen) { p.lockMinutes = it; refresh() }
        OutlinedTextField(
            value = pass,
            onValueChange = {
                pass = it
                if (it.length >= 8) {
                    p.emergencyPass = it
                    refresh()
                }
            },
            label = { Text("Emergency passcode (kam se kam 8 characters)") },
            supportingText = {
                Text(
                    when {
                        pass.isEmpty() -> "Jab tak passcode set nahi, lock kaam nahi karega"
                        pass.length < 8 -> "8 characters se kam: save nahi hua"
                        else -> "Saved ✅"
                    }
                )
            },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            enabled = !frozen,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Tip: passcode kisi dost/girlfriend se set karwao aur tum bhool jao, tabhi lock asli me kaam karega.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = {
            p.testLockUntil = System.currentTimeMillis() + 60_000
            Toast.makeText(ctx, "60 second ka lock test shuru (Accessibility ON honi chahiye)", Toast.LENGTH_LONG).show()
        }) { Text("Lock test (60 sec)") }

        SectionTitle("Office time")
        TimeRow("Office shuru", p.officeStart, enabled = !frozen) { p.officeStart = it; refresh() }
        TimeRow("Office khatam", p.officeEnd, enabled = !frozen) { p.officeEnd = it; refresh() }
        SwitchRow("Sunday off", p.sundayOff, enabled = !frozen) { p.sundayOff = it; refresh() }
        Text("Office time me phone kabhi lock nahi hoga.", style = MaterialTheme.typography.bodySmall)

        SectionTitle("Khana-paani targets")
        NumField("Calories (kcal)", p.calTarget) { p.calTarget = it; refresh() }
        NumField("Protein (g)", p.proteinTarget) { p.proteinTarget = it; refresh() }
        NumField("Paani (glass, 250 ml)", p.waterTarget) { p.waterTarget = it; refresh() }

        SectionTitle("Girlfriend reminders")
        TextSetting("Naam", p.gfName) { p.gfName = it }
        TextSetting("WhatsApp number (10 digit ya 91 ke saath)", p.gfNumber, KeyboardType.Phone) { p.gfNumber = it }
        TextSetting("Good morning message", p.gmMsg) { p.gmMsg = it }
        TextSetting("Good afternoon message", p.gaMsg) { p.gaMsg = it }
        SwitchRow("WhatsApp message apne aap send ho (Accessibility se)", p.waAutoSend) { p.waAutoSend = it; refresh() }

        SectionTitle("AI report (Gemini, free)")
        val ai = AiPrefs(ctx)
        TextSetting("Gemini API key (aistudio.google.com)", ai.geminiKey) { ai.geminiKey = it }
        TextSetting("Gemini model naam", ai.geminiModel) { ai.geminiModel = it }
        Text(
            "Key sirf tab use hoti hai jab Report tab me AI analysis dabate ho. Tab sirf report ka text Google ko jata hai.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp),
    )
}

@Composable
fun NumField(label: String, value: Int, enabled: Boolean = true, onSave: (Int) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            val d = t.filter { it.isDigit() }.take(6)
            text = d
            d.toIntOrNull()?.let(onSave)
        },
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun TextSetting(label: String, value: String, keyboard: KeyboardType = KeyboardType.Text, onSave: (String) -> Unit) {
    var t by remember { mutableStateOf(value) }
    OutlinedTextField(
        value = t,
        onValueChange = {
            t = it
            onSave(it)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun TimeRow(label: String, minutes: Int, enabled: Boolean = true, onPick: (Int) -> Unit) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        OutlinedButton(
            enabled = enabled,
            onClick = {
                TimePickerDialog(ctx, { _, h, m -> onPick(h * 60 + m) }, minutes / 60, minutes % 60, false).show()
            },
        ) { Text(DateUtil.fmt(minutes)) }
    }
}
