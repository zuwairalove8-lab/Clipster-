package com.cybertrace.android.ui

import android.media.MediaPlayer
import android.widget.MediaController
import android.widget.VideoView
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.cybertrace.android.data.CaptureDto
import com.cybertrace.android.data.CaptureTypes
import com.cybertrace.android.data.Repo
import com.cybertrace.android.data.VisitDto
import com.cybertrace.android.util.fullDate
import com.cybertrace.android.util.openMaps
import kotlinx.serialization.json.JsonObject

@Composable
fun DetailScreen(
    state: AppState,
    visitId: String,
    captureId: String?,
    signedUrl: suspend (String) -> String?,
    onBack: () -> Unit,
) {
    val visit = state.visits.firstOrNull { it.id == visitId }
    val capture = captureId?.let { id -> state.captures.firstOrNull { it.id == id } }
    val link = visit?.let { state.linkById(it.link_id) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CtTopBar(
                title = capture?.let { CaptureTypes.label(it.capture_type) } ?: "Visit",
                onBack = onBack,
            )
        },
    ) { padding ->
        if (visit == null) {
            EmptyState("Not found", "This record is no longer available.")
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (capture != null) {
                DetailCard(title = "Capture") {
                    KeyValueRow("Type", CaptureTypes.label(capture.capture_type))
                    KeyValueRow("Status", if (capture.isDenied) "Denied by recipient" else "Granted")
                    KeyValueRow("Captured", fullDate(capture.captured_at))
                    if (capture.isDenied) {
                        val reason = Repo.payloadValue(capture.payload, "reason")
                        KeyValueRow("Reason", reason ?: "user_denied")
                    }
                }

                if (!capture.isDenied) {
                    MediaBlock(capture = capture, signedUrl = signedUrl)
                }
            }

            DetailCard(title = "Visit") {
                KeyValueRow("Link", link?.label ?: visit.link_id.take(8))
                KeyValueRow("First seen", fullDate(visit.first_seen_at))
                KeyValueRow("IP address", visit.ip_address.ifBlank { "—" })
                KeyValueRow("User agent", visit.user_agent.ifBlank { "—" })
            }

            val geo = visit.ip_geo
            if (geo != null) {
                DetailCard(title = "IP geolocation (approximate)") {
                    KeyValueRow("City", geo.city ?: "—")
                    KeyValueRow("Region", geo.region ?: "—")
                    KeyValueRow("Country", geo.country ?: "—")
                    KeyValueRow("ISP", geo.isp ?: "—")
                    KeyValueRow("Organisation", geo.org ?: "—")
                    KeyValueRow("Timezone", geo.timezone ?: "—")
                    KeyValueRow("Lookup source", geo.source ?: "—")
                    if (geo.latitude != null && geo.longitude != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "%.5f, %.5f".format(geo.latitude, geo.longitude),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Precise coordinates come from a granted GPS capture, not from the IP lookup.
            val coords = capture?.let { Repo.coordinates(it.payload) }
            if (coords != null) {
                DetailCard(title = "Precise coordinates") {
                    KeyValueRow("Latitude", "%.6f".format(coords.first))
                    KeyValueRow("Longitude", "%.6f".format(coords.second))
                    Repo.payloadValue(capture.payload, "accuracy_m")?.let {
                        KeyValueRow("Accuracy", "$it m")
                    }
                    Spacer(Modifier.height(10.dp))
                    val context = LocalContext.current
                    OutlinedButton(onClick = { openMaps(context, coords.first, coords.second) }) {
                        Icon(Icons.Default.LocationOn, contentDescription = null)
                        Spacer(Modifier.height(0.dp))
                        Text("  Open in Maps")
                    }
                }
            }

            if (visit.fingerprint.isNotEmpty()) {
                DetailCard(title = "Device fingerprint") {
                    fingerprintRows(visit.fingerprint)
                }
            }

            if (capture != null && capture.payload.isNotEmpty()) {
                DetailCard(title = "Raw payload") {
                    fingerprintRows(capture.payload)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DetailCard(title: String, content: @Composable () -> Unit) {
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

@Composable
private fun fingerprintRows(obj: JsonObject) {
    obj.entries.forEach { (key, value) ->
        KeyValueRow(key.replace('_', ' '), value.toString().trim('"'))
    }
}

@Composable
private fun MediaBlock(capture: CaptureDto, signedUrl: suspend (String) -> String?) {
    val path = capture.storage_path ?: return
    var url by remember(path) { mutableStateOf<String?>(null) }

    LaunchedEffect(path) {
        url = signedUrl(path)
    }

    val resolved = url ?: return

    when {
        CaptureTypes.isImage(capture.capture_type, path) -> {
            DetailCard(title = "Media") {
                RemoteImage(path = path, signedUrl = signedUrl, modifier = Modifier.height(320.dp))
            }
        }

        CaptureTypes.isVideo(capture.capture_type, path) -> {
            DetailCard(title = "Video") {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    factory = { context ->
                        VideoView(context).apply {
                            setMediaController(MediaController(context).also { it.setAnchorView(this) })
                            setVideoPath(resolved)
                            setOnPreparedListener { it.isLooping = false }
                        }
                    },
                )
            }
        }

        CaptureTypes.isAudio(capture.capture_type, path) -> {
            DetailCard(title = "Audio") {
                AudioPlayer(resolved)
            }
        }
    }
}

@Composable
private fun AudioPlayer(url: String) {
    val context = LocalContext.current
    var player by remember(url) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(url) { mutableStateOf(false) }

    DisposableEffect(url) {
        onDispose {
            runCatching { player?.release() }
            player = null
        }
    }

    Row {
        Button(
            onClick = {
                val current = player
                if (current == null) {
                    val mp = MediaPlayer()
                    runCatching {
                        mp.setDataSource(url)
                        mp.setOnPreparedListener {
                            it.start()
                            playing = true
                        }
                        mp.setOnCompletionListener {
                            playing = false
                            it.release()
                            player = null
                        }
                        mp.prepareAsync()
                    }
                    player = mp
                } else if (current.isPlaying) {
                    current.pause()
                    playing = false
                } else {
                    current.start()
                    playing = true
                }
            },
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Text(if (playing) "  Pause" else "  Play")
        }
    }
}
