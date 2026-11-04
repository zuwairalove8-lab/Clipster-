package com.cybertrace.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cybertrace.android.BuildConfig
import com.cybertrace.android.data.Config

@Composable
fun SettingsScreen(
    state: AppState,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { CtTopBar(title = "Settings", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(title = "Account") {
                KeyValueRow("Signed in as", state.userEmail ?: "—")
                KeyValueRow("Project", Config.SUPABASE_URL.removePrefix("https://"))
            }

            Card(title = "Connection") {
                KeyValueRow(
                    "Backend",
                    when (state.backendOk) {
                        true -> "Connected"
                        false -> "Unreachable"
                        null -> "Checking…"
                    },
                )
                KeyValueRow("Realtime", if (state.live) "Subscribed" else "Not connected")
                KeyValueRow("Links", state.links.size.toString())
                KeyValueRow("Visits", state.visits.size.toString())
                KeyValueRow("Captures", state.captures.size.toString())
            }

            Card(title = "App") {
                KeyValueRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                KeyValueRow("Permissions", "Internet only")
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Authorized use",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "CyberTrace is for investigations you are legally allowed to run. The person " +
                            "opening a link always sees a consent screen before anything beyond a " +
                            "normal web visit is recorded, every item is requested separately, and a " +
                            "refusal is never re-prompted. Do not use it to monitor people without " +
                            "authority, and delete evidence when your retention period ends.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Sign out")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
