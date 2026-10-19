package com.example.trimvideopro.processor

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.trimvideopro.model.Transcript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

internal enum class TranscriptExportFormat(val extension: String, val mimeType: String) {
    SRT("srt", "application/x-subrip"), VTT("vtt", "text/vtt"), TEXT("txt", "text/plain")
}

/** A success means the file is actually in public Downloads, never just in private app storage. */
internal object TranscriptExportProcessor {
    const val DIRECTORY = "TrimVideoPro/Transcripts"

    fun content(transcript: Transcript, format: TranscriptExportFormat): String = when (format) {
        TranscriptExportFormat.SRT -> transcript.toSrtFormat()
        TranscriptExportFormat.VTT -> "WEBVTT\n\n" + transcript.chunks.mapIndexed { index, chunk ->
            "${index + 1}\n${chunk.formatTimestamp().replace(',', '.')}\n${chunk.text}\n\n"
        }.joinToString("")
        TranscriptExportFormat.TEXT -> transcript.chunks.joinToString("\n\n") { "[${it.formatTimestamp()}]\n${it.text}" } + "\n"
    }

    suspend fun save(context: Context, transcript: Transcript, format: TranscriptExportFormat): String = withContext(Dispatchers.IO) {
        require(transcript.chunks.isNotEmpty()) { "No transcript to export." }
        val fileName = "transcript_${UUID.randomUUID()}.${format.extension}"
        val text = content(transcript, format)
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= 29) {
            var inserted: Uri? = null
            try {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$DIRECTORY")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Cannot create a file in Downloads.")
                inserted = uri
                resolver.openOutputStream(uri, "w")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) }
                    ?: error("Cannot write to Downloads.")
                check(resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) > 0) {
                    "Cannot finalize the downloaded transcript."
                }
            } catch (e: Exception) {
                inserted?.let { runCatching { resolver.delete(it, null, null) } }
                throw e
            }
        } else {
            val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DIRECTORY)
            check(folder.exists() || folder.mkdirs()) { "Cannot create Downloads folder. Check storage permission." }
            val file = File(folder, fileName)
            try {
                file.writeText(text, Charsets.UTF_8)
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(format.mimeType), null)
            } catch (e: Exception) { file.delete(); throw e }
        }
        "Downloads/$DIRECTORY/$fileName"
    }
}
