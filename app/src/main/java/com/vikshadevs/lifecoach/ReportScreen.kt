package com.vikshadevs.lifecoach

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ReportScreen(tick: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var summary by remember { mutableStateOf("") }
    var ai by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val hasKey = AiPrefs(ctx).geminiKey.isNotBlank()
    val usageOk = hasUsageAccess(ctx)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Weekly report", style = MaterialTheme.typography.headlineSmall)

        if (!usageOk) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Phone ka screen time dekhne ke liye Usage access allow karo (Life Coach ko ON karo).")
                    OutlinedButton(onClick = {
                        ctx.startActivity(Intent(AndroidSettings.ACTION_USAGE_ACCESS_SETTINGS))
                    }) { Text("Usage access kholo") }
                }
            }
        }

        Button(onClick = {
            summary = ReportBuilder.build(ctx)
            ai = ""
        }) { Text("Report banao") }

        if (summary.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Text(summary, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            }

            if (hasKey) {
                Button(
                    enabled = !loading,
                    onClick = {
                        loading = true
                        ai = "Gemini soch raha hai..."
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { Gemini.analyze(ctx, summary) }
                            ai = r
                            loading = false
                        }
                    },
                ) { Text("AI analysis (Gemini, free)") }
            } else {
                Text(
                    "AI analysis ke liye Settings me free Gemini API key daalo (aistudio.google.com se milti hai).",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (ai.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Text(ai, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }

            OutlinedButton(onClick = {
                val text = summary + if (ai.isNotEmpty() && !loading) "\n\nAI ANALYSIS\n$ai" else ""
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                ctx.startActivity(Intent.createChooser(send, "Report share karo"))
            }) { Text("Share / Claude ko bhejo") }
        }
    }
}
