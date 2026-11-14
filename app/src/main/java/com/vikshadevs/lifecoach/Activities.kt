package com.vikshadevs.lifecoach

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** Notification ke "WhatsApp" button se khulta hai, WhatsApp chat kholta hai aur band ho jata hai. */
class WaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Wa.open(this, intent.getStringExtra("msg") ?: "")
        finish()
    }
}

/** Lock ke time pe "Emergency unlock" dabane par khulta hai. Sahi passcode = aaj ke liye lock band. */
class EmergencyUnlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize()) {
                    UnlockScreen(onDone = { finish() })
                }
            }
        }
    }
}

@Composable
fun UnlockScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val p = Prefs(ctx)
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Emergency unlock", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Sahi passcode daaloge to aaj raat ke liye lock band ho jayega.",
            modifier = Modifier.padding(vertical = 12.dp),
        )
        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
                wrong = false
            },
            label = { Text("Passcode") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            isError = wrong,
            supportingText = { if (wrong) Text("Galat passcode") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                if (p.emergencyPass.isNotEmpty() && input == p.emergencyPass) {
                    p.emergencyDay = DateUtil.todayKey()
                    Toast.makeText(ctx, "Aaj ke liye unlock ho gaya", Toast.LENGTH_SHORT).show()
                    onDone()
                } else {
                    wrong = true
                }
            },
            modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
        ) { Text("Unlock") }
        OutlinedButton(onClick = onDone, modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) { Text("Wapas") }
    }
}
