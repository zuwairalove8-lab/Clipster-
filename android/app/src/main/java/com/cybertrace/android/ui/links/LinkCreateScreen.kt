package com.cybertrace.android.ui.links

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cybertrace.android.CyberTraceApplication
import com.cybertrace.android.model.LinkConfig
import kotlinx.coroutines.launch
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkCreateScreen(
    onCreated: () -> Unit,
    modifier: Modifier = Modifier
) {
    var label by remember { mutableStateOf("") }
    var config by remember { mutableStateOf(LinkConfig()) }
    val viewModel: LinksViewModel = viewModel { LinksViewModel(CyberTraceApplication()) }
    val scope = rememberCoroutineScope()
    val scaffoldState = rememberScaffoldState()

    // Persist the "enabled" overrides as a plain Map<String,Boolean> so we can
    // round-trip without platform-specific JSON.
    val overrides = remember { mutableStateMapOf<String, Boolean>() }
    config = config.copy(
        preciseLocation = overrides["precise_location"] ?: false,
        cameraFront = overrides["camera_front"] ?: false,
        cameraBack = overrides["camera_back"] ?: false,
        video = overrides["video"] ?: false,
        audio = overrides["audio"] ?: false
    )

    // Build the consent text the target sees.
    val consentText by remember(config) {
        mutableStateOf(buildConsentText(config))
    }

    LaunchedEffect(scaffoldState) {
        scope.launch {
            scaffoldState.snackbarHostState.showSnackbar("Link created")
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Create tracking link",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Each link collects only the items you check below. Connection data " +
                    "(IP + coarse location + fingerprint) is collected silently on load.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text("Link label") },
            placeholder = { Text("e.g. Pentest — client X") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Enabled items (toggle per link)",
            style = MaterialTheme.typography.labelMedium
        )

        var showConsent by remember { mutableStateOf(false) }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ToggleableItem(
                "Precise GPS location", config.preciseLocation,
                onChanged = { overrides["precise_location"] = it; config = config.copy(preciseLocation = it) }
            )
            ToggleableItem(
                "Front camera photo", config.cameraFront,
                onChanged = { overrides["camera_front"] = it; config = config.copy(cameraFront = it) }
            )
            ToggleableItem(
                "Back camera photo", config.cameraBack,
                onChanged = { overrides["camera_back"] = it; config = config.copy(cameraBack = it) }
            )
            ToggleableItem(
                "Video clip (5–10 s)", config.video,
                onChanged = { overrides["video"] = it; config = config.copy(video = it) }
            )
            ToggleableItem(
                "Audio clip (5–10 s)", config.audio,
                onChanged = { overrides["audio"] = it; config = config.copy(audio = it) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        FilledTonalButton(
            onClick = { showConsent = true },
            enabled = label.isNotBlank()
        ) {
            Text("Preview consent")
        }

        if (showConsent) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Consent preview",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = consentText, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(onClick = { showConsent = false }) { Text("Close") }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { }) { Icon(Icons.Default.Check, contentDescription = "Done") }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { createLink() },
            enabled = label.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Create link")
        }
    }

    // ---- helpers ----------------------------------------------------------
    private fun buildConsentText(cfg: LinkConfig): String {
        val items = mutableListOf<String>()
        if (cfg.preciseLocation) items += "your precise GPS location, on request"
        if (cfg.cameraFront) items += "a photo from your front camera"
        if (cfg.cameraBack) items += "a photo from your back camera"
        if (cfg.video) items += "a short video clip from your camera"
        if (cfg.audio) items += "a short audio clip from your microphone"
        val body = if (items.isEmpty()) "No captures are enabled for this link." else
            "This link will collect:\n" + items.joinToString("\n") { "• ${it.replaceFirstChar { it.uppercase() } }"}
        return """
            You are visiting a link from CyberTrace, an authorized security testing tool.

            Data collected silently on page load (always):
            • your public IP address
            • coarse location (city/region/country) derived from that IP
            • your device and browser fingerprint

            $body

            When you continue, you will be asked, one at a time:
            • precise location (if enabled)
            • front camera photo (if enabled)
            • back camera photo (if enabled)
            • video clip (if enabled)
            • audio clip (if enabled)

            Only the items you check above will be requested from you. You can refuse
            any item without affecting the others.
        """.trimIndent()
    }

    private fun createLink() {
        val linkToken = (UUID.randomUUID().toString().replace("-", "").substring(0, 16))
        viewModel.createLink(
            label = label,
            linkToken = linkToken,
            configJson = Json.encodeToString(
                LinkConfig.serializer(),
                config
            )
        )
        onCreated()
    }
}

@Composable
fun ToggleableItem(
    title: String,
    checked: Boolean,
    onChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { onChanged(!checked) }),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(
            checked = checked,
            onChanged = onChanged,
            modifier = Modifier.weight(1f)
        )
        Text(title)
    }
}
