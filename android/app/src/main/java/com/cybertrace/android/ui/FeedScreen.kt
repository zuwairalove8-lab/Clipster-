package com.cybertrace.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cybertrace.android.data.CaptureDto
import com.cybertrace.android.data.CaptureTypes
import com.cybertrace.android.data.Repo
import com.cybertrace.android.data.VisitDto
import com.cybertrace.android.util.parseInstant
import com.cybertrace.android.util.timeAgo
import java.time.Duration
import java.time.Instant

private sealed interface FeedItem {
    val key: String
    val timestamp: String

    data class VisitItem(val visit: VisitDto, val captures: List<CaptureDto>) : FeedItem {
        override val key: String get() = "v-${visit.id}"
        override val timestamp: String get() = visit.first_seen_at
    }

    data class CaptureItem(val capture: CaptureDto) : FeedItem {
        override val key: String get() = "c-${capture.id}"
        override val timestamp: String get() = capture.captured_at
    }
}

private val RANGES = listOf("24h" to 1, "7d" to 7, "30d" to 30, "All" to 0)

@Composable
fun FeedScreen(
    state: AppState,
    linkId: String?,
    signedUrl: suspend (String) -> String?,
    onBack: () -> Unit,
    onOpen: (String, String?) -> Unit,
) {
    var filterLink by remember(linkId) { mutableStateOf(linkId) }
    var filterType by remember { mutableStateOf<String?>(null) }
    var filterStatus by remember { mutableStateOf<String?>(null) }
    var filterRangeDays by remember { mutableStateOf(7) }

    val now = Instant.now()
    val items = remember(state.visits, state.captures, filterLink, filterType, filterStatus, filterRangeDays) {
        buildItems(state, filterLink, filterType, filterStatus, filterRangeDays, now)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CtTopBar(
                title = filterLink?.let { id -> state.linkById(id)?.label ?: "Activity" } ?: "All activity",
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (!state.live) {
                StatusBanner(
                    "Live updates disconnected",
                    "Showing the last loaded data. Tap refresh on the links list to reconnect.",
                    BannerTone.Warn,
                )
            }

            FilterBar(
                state = state,
                filterLink = filterLink,
                onLinkChange = { filterLink = it },
                filterType = filterType,
                onTypeChange = { filterType = it },
                filterStatus = filterStatus,
                onStatusChange = { filterStatus = it },
                rangeDays = filterRangeDays,
                onRangeChange = { filterRangeDays = it },
            )

            if (items.isEmpty()) {
                EmptyState(
                    "Nothing here yet",
                    if (filterStatus == "denied") "No refusals in this window."
                    else "Open a link on a phone and this feed fills in live.",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(items, key = { it.key }) { item ->
                        when (item) {
                            is FeedItem.VisitItem -> VisitCard(
                                visit = item.visit,
                                captures = item.captures,
                                onClick = { onOpen(item.visit.id, null) },
                            )

                            is FeedItem.CaptureItem -> CaptureCard(
                                capture = item.capture,
                                signedUrl = signedUrl,
                                onClick = { onOpen(item.capture.visit_id, item.capture.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterBar(
    state: AppState,
    filterLink: String?,
    onLinkChange: (String?) -> Unit,
    filterType: String?,
    onTypeChange: (String?) -> Unit,
    filterStatus: String?,
    onStatusChange: (String?) -> Unit,
    rangeDays: Int,
    onRangeChange: (Int) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Pill("All links", selected = filterLink == null, onClick = { onLinkChange(null) })
            state.links.forEach { link ->
                Pill(
                    link.label.ifBlank { link.link_token.take(6) },
                    selected = filterLink == link.id,
                    onClick = { onLinkChange(link.id) },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Pill("Every type", selected = filterType == null, onClick = { onTypeChange(null) })
            CaptureTypes.all.forEach { type ->
                Pill(
                    CaptureTypes.label(type),
                    selected = filterType == type,
                    onClick = { onTypeChange(if (filterType == type) null else type) },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Pill("Any status", selected = filterStatus == null, onClick = { onStatusChange(null) })
            Pill("Granted", selected = filterStatus == "granted", onClick = { onStatusChange("granted") })
            Pill("Denied", selected = filterStatus == "denied", onClick = { onStatusChange("denied") })

            Spacer(Modifier.size(10.dp))
            RANGES.forEach { (label, days) ->
                Pill(label, selected = rangeDays == days, onClick = { onRangeChange(days) })
            }
        }
    }
}

@Composable
private fun VisitCard(visit: VisitDto, captures: List<CaptureDto>, onClick: () -> Unit) {
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
                Pill("Visit", accent = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                Text(
                    timeAgo(visit.first_seen_at),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                visit.ip_address.ifBlank { "IP unavailable" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                visit.ip_geo?.place ?: "Location unavailable",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (captures.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    buildString {
                        append("$granted captured")
                        if (denied > 0) append(" · $denied refused")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun CaptureCard(
    capture: CaptureDto,
    signedUrl: suspend (String) -> String?,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(
                    if (capture.isDenied) "Denied" else "Granted",
                    accent = if (capture.isDenied) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    CaptureTypes.label(capture.capture_type),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    timeAgo(capture.captured_at),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!capture.isDenied) {
                val path = capture.storage_path
                if (path != null && CaptureTypes.isImage(capture.capture_type, path)) {
                    RemoteImage(path, signedUrl)
                } else if (path != null && CaptureTypes.isVideo(capture.capture_type, path)) {
                    Text(
                        "Video clip — open for playback",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else if (path != null && CaptureTypes.isAudio(capture.capture_type, path)) {
                    Text(
                        "Audio clip — open for playback",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    val coords = Repo.coordinates(capture.payload)
                    if (coords != null) {
                        Text(
                            "%.5f, %.5f".format(coords.first, coords.second),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            } else {
                Text(
                    "The recipient refused this item. They were not asked again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
fun RemoteImage(path: String, signedUrl: suspend (String) -> String?, modifier: Modifier = Modifier) {
    var url by remember(path) { mutableStateOf<String?>(null) }

    LaunchedEffect(path) {
        url = signedUrl(path)
    }

    if (url == null) {
        Box(
            modifier = modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Loading preview…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        AsyncImage(
            model = url,
            contentDescription = "Capture preview",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
    }
}

private fun buildItems(
    state: AppState,
    linkId: String?,
    type: String?,
    status: String?,
    rangeDays: Int,
    now: Instant,
): List<FeedItem> {
    val threshold = if (rangeDays > 0) now.minus(Duration.ofDays(rangeDays.toLong())) else null

    fun within(iso: String): Boolean {
        if (threshold == null) return true
        val instant = parseInstant(iso) ?: return true
        return instant.isAfter(threshold)
    }

    val captures = state.capturesFor(linkId)
        .filter { type == null || it.capture_type == type }
        .filter {
            when (status) {
                "granted" -> !it.isDenied
                "denied" -> it.isDenied
                else -> true
            }
        }
        .filter { within(it.captured_at) }

    val capturesByVisit = captures.groupBy { it.visit_id }

    val visits = state.visitsFor(linkId)
        .filter { within(it.first_seen_at) }
        .map { FeedItem.VisitItem(it, capturesByVisit[it.id].orEmpty()) }

    val captureItems = captures.map { FeedItem.CaptureItem(it) }

    return (visits + captureItems).sortedWith(
        compareByDescending<FeedItem> { parseInstant(it.timestamp)?.toEpochMilli() ?: 0L }
    )
}
