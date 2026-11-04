package com.cybertrace.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cybertrace.android.data.LinkConfig
import com.cybertrace.android.util.qrBitmap
import com.cybertrace.android.util.shareText

private data class Toggle(val key: String, val title: String, val description: String)

private val toggles = listOf(
    Toggle("precise_location", "Precise GPS location", "Asks the browser for a GPS fix, after a tap."),
    Toggle("camera_front", "Front camera photo", "One still frame from the front camera."),
    Toggle("camera_back", "Back camera photo", "One still frame from the rear camera."),
    Toggle("video", "Short video clip", "Six seconds from the front camera."),
    Toggle("audio", "Short audio clip", "Six seconds from the microphone."),
)

@Composable
fun CreateLinkScreen(
    state: AppState,
    onCreate: (String, LinkConfig) -> Unit,
    onBack: () -> Unit,
    newLinkUrl: String?,
    onDismissNewLink: () -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var config by remember { mutableStateOf(LinkConfig()) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { CtTopBar(title = "New link", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StatusBanner(
                "Authorized use only",
                "Only send this link to people you are permitted to investigate, and keep the " +
                    "consent screen intact. IP data and a device fingerprint are recorded when " +
                    "the link opens; everything else needs an explicit tap.",
                BannerTone.Neutral,
            )

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Link label") },
                placeholder = { Text("e.g. Case 2411 — invoice fraud") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel("Items this link may request")

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    toggles.forEach { toggle ->
                        val checked = when (toggle.key) {
                            "precise_location" -> config.precise_location
                            "camera_front" -> config.camera_front
                            "camera_back" -> config.camera_back
                            "video" -> config.video
                            else -> config.audio
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(toggle.title, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    toggle.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.size(12.dp))
                            Switch(
                                checked = checked,
                                onCheckedChange = { value ->
                                    config = when (toggle.key) {
                                        "precise_location" -> config.copy(precise_location = value)
                                        "camera_front" -> config.copy(camera_front = value)
                                        "camera_back" -> config.copy(camera_back = value)
                                        "video" -> config.copy(video = value)
                                        else -> config.copy(audio = value)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            SectionLabel("Consent the recipient sees")
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    consentPreview(label.ifBlank { "this link" }, config),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.createError != null) {
                Text(
                    state.createError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = { onCreate(label, config) },
                enabled = label.isNotBlank() && !state.creating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.creating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.size(10.dp))
                }
                Text("Create link")
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (newLinkUrl != null) {
        NewLinkDialog(url = newLinkUrl, onDismiss = onDismissNewLink)
    }
}

@Composable
private fun NewLinkDialog(url: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val qr = remember(url) { qrBitmap(url) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link ready") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (qr != null) {
                    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
                        Image(
                            bitmap = qr,
                            contentDescription = "QR code for the link",
                            modifier = Modifier
                                .size(200.dp)
                                .padding(8.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Text(
                    url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Anyone opening it sees the consent screen first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(url))
                onDismiss()
            }) { Text("Copy") }
        },
        dismissButton = {
            TextButton(onClick = {
                shareText(context, url)
            }) { Text("Share") }
        },
    )
}

private fun consentPreview(label: String, config: LinkConfig): String {
    val items = buildList {
        if (config.precise_location) add("your precise GPS coordinates")
        if (config.camera_front) add("one photo from your front camera")
        if (config.camera_back) add("one photo from your back camera")
        if (config.video) add("a short video clip")
        if (config.audio) add("a short audio clip")
    }
    val ask = if (items.isEmpty()) {
        "No camera, microphone or location access is enabled for this link, so you will not be asked for any."
    } else {
        "You will then be asked, one at a time and only after you tap a button:\n" +
            items.joinToString("\n") { "• $it" } +
            "\n\nYou can decline any of these. Declining one does not affect the others."
    }
    return "This page is part of \"$label\", an investigation carried out with CyberTrace.\n\n" +
        "When this page opened, the following was recorded automatically, the same way any " +
        "website records a visit:\n" +
        "• the public IP address you are connecting from\n" +
        "• approximate location derived from that IP address (city and country level — not GPS)\n" +
        "• technical details about your browser and device\n\n" +
        ask
}
