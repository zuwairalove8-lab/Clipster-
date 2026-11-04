package com.cybertrace.android.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cybertrace.android.util.Exporter
import com.cybertrace.android.util.parseInstant
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

private val WINDOWS = listOf("7 days" to 7, "30 days" to 30, "90 days" to 90, "Everything" to 0)

@Composable
fun ExportScreen(
    state: AppState,
    signedUrl: suspend (String) -> String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var linkId by remember { mutableStateOf<String?>(null) }
    var windowDays by remember { mutableStateOf(30) }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var message by remember { mutableStateOf("") }
    var resultPath by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { CtTopBar(title = "Export", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StatusBanner(
                "What the ZIP contains",
                "summary.json, visits.json, captures.json, and every granted media file under media/.",
                BannerTone.Neutral,
            )

            SectionLabel("Link")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Pill("All links", selected = linkId == null, onClick = { linkId = null })
                state.links.forEach { link ->
                    Pill(
                        link.label.ifBlank { link.link_token.take(6) },
                        selected = linkId == link.id,
                        onClick = { linkId = link.id },
                    )
                }
            }

            SectionLabel("Window")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                WINDOWS.forEach { (label, days) ->
                    Pill(label, selected = windowDays == days, onClick = { windowDays = days })
                }
            }

            val scopeVisits = state.visitsFor(linkId).filter { within(it.first_seen_at, windowDays) }
            val scopeCaptures = state.capturesFor(linkId).filter { within(it.captured_at, windowDays) }

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "${scopeVisits.size} visits · ${scopeCaptures.size} captures",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "${scopeCaptures.count { !it.isDenied && it.storage_path != null }} media files to download",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(
                onClick = {
                    if (running) return@Button
                    running = true
                    error = null
                    resultPath = null
                    progress = 0f
                    message = "Starting…"
                    scope.launch {
                        val link = state.links.firstOrNull { it.id == linkId }
                        val result = Exporter.export(
                            context = context,
                            link = link,
                            visits = scopeVisits,
                            captures = scopeCaptures,
                            signedUrl = signedUrl,
                            onProgress = { fraction, note ->
                                progress = fraction
                                message = note
                            },
                        )
                        running = false
                        result.onSuccess {
                            resultPath = it.displayPath
                            message = "Exported ${it.mediaCount} media files"
                        }.onFailure {
                            error = it.message ?: "Export failed"
                            message = ""
                        }
                    }
                },
                enabled = !running && (scopeVisits.isNotEmpty() || scopeCaptures.isNotEmpty()),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (running) "Exporting…" else "Export ZIP")
            }

            if (running || progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            resultPath?.let { path ->
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "Saved",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            path,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun within(iso: String, days: Int): Boolean {
    if (days <= 0) return true
    val instant = parseInstant(iso) ?: return true
    return instant.isAfter(Instant.now().minus(Duration.ofDays(days.toLong())))
}
