package com.example.trimvideopro.processor

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Publish only complete files, to the correct audio/video collection and with the correct MIME type. */
object MediaExport {
    fun mimeType(title: String): String = when (title.substringAfterLast('.').lowercase()) {
        "wav" -> "audio/wav"
        "m4a" -> "audio/mp4"
        else -> "video/mp4"
    }

    suspend fun save(context: Context, source: Uri, title: String, downloads: Boolean): Boolean = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = mimeType(title)
        val audio = mime.startsWith("audio/")
        val folder = if (downloads) Environment.DIRECTORY_DOWNLOADS else if (audio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
        var inserted: Uri? = null
        var legacyFile: File? = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = if (downloads) MediaStore.Downloads.EXTERNAL_CONTENT_URI else if (audio)
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, title)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/TrimVideoPro")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val item = resolver.insert(collection, values) ?: error("Cannot create exported media.")
                inserted = item
                resolver.openInputStream(source)?.use { input ->
                    resolver.openOutputStream(item)?.use { output -> input.copyTo(output) }
                        ?: error("Cannot write export.")
                } ?: error("Cannot read source media.")
                resolver.update(item, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } else {
                // Android 7–9 require the app's existing storage permission to be granted.
                val directory = File(Environment.getExternalStoragePublicDirectory(folder), "TrimVideoPro").apply { mkdirs() }
                val destination = File(directory, "${System.currentTimeMillis()}_$title")
                legacyFile = destination
                resolver.openInputStream(source)?.use { input -> destination.outputStream().use { input.copyTo(it) } }
                    ?: error("Cannot read source media.")
                MediaScannerConnection.scanFile(context, arrayOf(destination.absolutePath), arrayOf(mime), null)
            }
            true
        } catch (e: Exception) {
            inserted?.let { runCatching { resolver.delete(it, null, null) } }
            legacyFile?.delete()
            false
        }
    }
}
