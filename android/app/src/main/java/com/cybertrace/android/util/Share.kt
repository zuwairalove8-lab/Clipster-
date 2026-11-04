package com.cybertrace.android.util

import android.content.Context
import android.content.Intent

fun shareText(context: Context, text: String, subject: String = "CyberTrace link") {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share link"))
}

fun openMaps(context: Context, lat: Double, lng: Double) {
    val uri = android.net.Uri.parse("geo:$lat,$lng?q=$lat,$lng")
    val intent = Intent(Intent.ACTION_VIEW, uri)
    runCatching { context.startActivity(intent) }
}
