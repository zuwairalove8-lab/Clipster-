package com.cybertrace.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cybertrace.android.data.CaptureDto
import com.cybertrace.android.data.LinkDto
import com.cybertrace.android.data.VisitDto
import com.cybertrace.android.util.timeAgo

@Composable
fun LinksScreen(
    state: AppState,
    onOpenFeed: (String?) -> Unit,
    onCreate: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onExport: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CtTopBar(
                title = "Links",
                actions = {
                    IconButton(onClick = onExport) {
                        Text("Export", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New link") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (state.backendOk == false) {
                StatusBanner(
                    "Backend unreachable",
                    state.dataError ?: "Check the connection and pull to refresh.",
                    BannerTone.Warn,
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    LiveActivityRow(
                        visits = state.visits.size,
                        captures = state.captures.size,
                        live = state.live,
                        onClick = { onOpenFeed(null) },
                    )
                }

                if (state.loading && !state.loadedOnce) {
                    item { LoadingRow("Loading your links…") }
                } else if (state.links.isEmpty()) {
                    item {
                        EmptyState(
                            title = "No links yet",
                            message = "Create your first tracking link. The recipient sees a " +
                                "consent screen before anything is requested.",
                        )
                    }
                } else {
                    items(state.links, key = { it.id }) { link ->
                        val visits = state.visitsFor(link.id)
                        val captures = state.capturesFor(link.id)
                        LinkCard(
                            link = link,
                            visits = visits,
                            captures = captures,
                            onClick = { onOpenFeed(link.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveActivityRow(visits: Int, captures: Int, live: Boolean, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "All activity",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "$visits visits · $captures captures",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }
            Pill(
                text = if (live) "● live" else "○ offline",
                accent = if (live) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LinkCard(
    link: LinkDto,
    visits: List<VisitDto>,
    captures: List<CaptureDto>,
    onClick: () -> Unit,
) {
    val granted = captures.count { !it.isDenied }
    val denied = captures.count { it.isDenied }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    link.label.ifBlank { "Untitled link" },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    timeAgo(link.created_at),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "/c/${link.link_token}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(10.dp))

            val enabled = link.config.enabledItems
            if (enabled.isEmpty()) {
                Text(
                    "Visit data only — no camera, microphone or location.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    enabled.take(3).forEach { (_, label) -> Pill(text = label) }
                    if (enabled.size > 3) Pill(text = "+${enabled.size - 3}")
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${visits.size} visits",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    "$granted granted",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (denied > 0) {
                    Spacer(Modifier.size(12.dp))
                    Text(
                        "$denied denied",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
