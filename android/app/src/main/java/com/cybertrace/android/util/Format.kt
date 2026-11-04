package com.cybertrace.android.util

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val fullFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.UK).withZone(ZoneId.systemDefault())

fun parseInstant(iso: String?): Instant? {
    if (iso.isNullOrBlank()) return null
    return runCatching { Instant.parse(iso) }.getOrNull()
}

fun fullDate(iso: String?): String {
    val instant = parseInstant(iso) ?: return "—"
    return fullFormatter.format(instant)
}

fun timeAgo(iso: String?, now: Instant = Instant.now()): String {
    val instant = parseInstant(iso) ?: return "—"
    val seconds = Duration.between(instant, now).seconds.coerceAtLeast(0)
    return when {
        seconds < 10 -> "just now"
        seconds < 60 -> "${seconds}s ago"
        seconds < 3600 -> "${seconds / 60}m ago"
        seconds < 86_400 -> "${seconds / 3600}h ago"
        seconds < 604_800 -> "${seconds / 86_400}d ago"
        else -> fullFormatter.format(instant)
    }
}

fun shortToken(token: String): String =
    if (token.length <= 10) token else token.take(6) + "…" + token.takeLast(3)
