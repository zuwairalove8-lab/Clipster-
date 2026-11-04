package com.cybertrace.android.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.cybertrace.android.data.CaptureDto
import com.cybertrace.android.data.LinkDto
import com.cybertrace.android.data.VisitDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a ZIP with JSON summaries plus every media file for the selected link
 * and window.
 *
 * Android 10+ goes through MediaStore into Download/CyberTrace/ so the file is
 * visible in the Files app without any storage permission. Android 8–9 writes
 * to the app-specific external directory instead, because MediaStore.Downloads
 * does not exist before API 29 and we deliberately do not request
 * WRITE_EXTERNAL_STORAGE.
 */
object Exporter {

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    data class Result(val displayPath: String, val mediaCount: Int, val bytes: Long)

    suspend fun export(
        context: Context,
        link: LinkDto?,
        visits: List<VisitDto>,
        captures: List<CaptureDto>,
        signedUrl: suspend (String) -> String?,
        onProgress: (Float, String) -> Unit,
    ): kotlin.Result<Result> = withContext(Dispatchers.IO) {
        runCatching {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.UK).format(Date())
            val slug = (link?.label ?: "all-links")
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .ifBlank { "export" }
                .take(40)
            val fileName = "cybertrace-$slug-$stamp.zip"

            onProgress(0.02f, "Preparing…")

            val media = captures.filter { !it.isDenied && !it.storage_path.isNullOrBlank() }
            val totalSteps = media.size + 2
            var done = 0

            val sink = openSink(context, fileName)
            var wrote = 0L

            ZipOutputStream(BufferedOutputStream(sink.stream)).use { zip ->
                fun putText(name: String, text: String) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(text.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                    wrote += text.toByteArray(Charsets.UTF_8).size.toLong()
                }

                putText("summary.json", json.encodeToString(ExportSummary.serializer(), ExportSummary(
                    linkId = link?.id,
                    label = link?.label,
                    linkToken = link?.link_token,
                    exportedAt = java.time.Instant.now().toString(),
                    visitCount = visits.size,
                    captureCount = captures.size,
                    grantedCount = captures.count { !it.isDenied },
                    deniedCount = captures.count { it.isDenied },
                )))
                putText("visits.json", json.encodeToString(ListSerializer(VisitDto.serializer()), visits))
                putText("captures.json", json.encodeToString(ListSerializer(CaptureDto.serializer()), captures))

                done = 2
                onProgress(done.toFloat() / totalSteps, "Writing index…")

                for (capture in media) {
                    val path = capture.storage_path ?: continue
                    val url = signedUrl(path)
                    if (url == null) {
                        done += 1
                        continue
                    }
                    val bytes = download(url)
                    if (bytes != null) {
                        val entryName = "media/" + path.replace('/', '_')
                        zip.putNextEntry(ZipEntry(entryName))
                        zip.write(bytes)
                        zip.closeEntry()
                        wrote += bytes.size.toLong()
                    }
                    done += 1
                    onProgress(done.toFloat() / totalSteps, "Downloading media (${done - 2}/${media.size})…")
                }
            }

            sink.finish(wrote)

            onProgress(1f, "Done")
            Result(
                displayPath = sink.displayPath,
                mediaCount = media.size,
                bytes = wrote,
            )
        }
    }

    private class Sink(
        val stream: OutputStream,
        val displayPath: String,
        val finish: (Long) -> Unit,
    )

    private fun openSink(context: Context, fileName: String): Sink {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/CyberTrace")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values)
                ?: error("Could not create the export file")
            val out = resolver.openOutputStream(uri) ?: error("Could not open the export file")

            return Sink(
                stream = out,
                displayPath = "Download/CyberTrace/$fileName",
                finish = { size ->
                    val done = ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                        if (size > 0) put(MediaStore.Downloads.SIZE, size)
                    }
                    runCatching { resolver.update(uri, done, null, null) }
                },
            )
        }

        val dir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        return Sink(
            stream = file.outputStream(),
            displayPath = file.absolutePath,
            finish = { _ -> },
        )
    }

    private fun download(url: String): ByteArray? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

@kotlinx.serialization.Serializable
data class ExportSummary(
    val linkId: String?,
    val label: String?,
    val linkToken: String?,
    val exportedAt: String,
    val visitCount: Int,
    val captureCount: Int,
    val grantedCount: Int,
    val deniedCount: Int,
)
