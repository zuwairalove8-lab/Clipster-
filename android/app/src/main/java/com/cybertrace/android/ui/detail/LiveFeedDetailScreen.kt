package com.cybertrace.android.ui.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cybertrace.android.model.Visit

@Composable
fun LiveFeedDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visit = remember { Visit(
        id = java.util.UUID.randomUUID(),
        linkId = java.util.UUID.randomUUID(),
        ipAddress = "203.0.113.10",
        ipGeo = mapOf("city" to "Example City", "region" to "Example Region", "country" to "Example Country", "ISP" to "Example ISP"),
        fingerprint = mapOf("user_agent" to "Mozilla/5.0", "screen" to "1920x1080", "device_memory" to "8"),
        userAgent = "Mozilla/5.0",
        consentShownAt = "2026-11-01T10:00:00Z",
        firstSeenAt = "2026-11-01T10:00:00Z",
        lastSeenAt = "2026-11-01T10:05:00Z"
    ) }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(
                text = "Visit detail",
                style = MaterialTheme.typography.headlineSmall
            )
            DetailRow("IP address", visit.ipAddress)
            DetailRow("Coarse location", buildString {
                visit.ipGeo.forEach { (k, v) -> appendLine("  $k: $v") }
            })
            DetailRow("User agent", visit.userAgent)
            DetailRow("Fingerprint", buildString {
                visit.fingerprint.forEach { (k, v) -> appendLine("  $k: $v") }
            })
            DetailRow("Consent shown at", visit.consentShownAt)
            DetailRow("First seen at", visit.firstSeenAt)
            DetailRow("Last seen at", visit.lastSeenAt)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Close")
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
