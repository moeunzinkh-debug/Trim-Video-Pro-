package com.example.trimvideopro.processor

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import com.example.trimvideopro.model.ClipSegment
import com.example.trimvideopro.model.ProcessedVideo
import com.example.trimvideopro.model.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.nio.ByteBuffer

object VideoProcessor {
    private const val TAG = "VideoProcessor"
    private const val DEFAULT_BUFFER_SIZE = 1024 * 1024 // 1MB

    suspend fun extractMetadata(context: Context, uri: Uri): VideoMetadata? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            val mimeType = (
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                    ?: context.contentResolver.getType(uri)
                    ?: "video/mp4"
                ).lowercase()
            val isAudio = mimeType.startsWith("audio/")

            val durationMs = durationStr?.toLongOrNull() ?: 0L
            var width = if (isAudio) 0 else widthStr?.toIntOrNull() ?: 1280
            var height = if (isAudio) 0 else heightStr?.toIntOrNull() ?: 720
            val rotation = rotationStr?.toIntOrNull() ?: 0

            // Swap dimensions if rotated 90 or 270 degrees
            if (rotation == 90 || rotation == 270) {
                val temp = width
                width = height
                height = temp
            }

            var fileSize = 0L
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                fileSize = pfd.statSize
            }

            val fileName = getFileNameFromUri(context, uri)
                ?: if (isAudio) "Selected_Audio.m4a" else "Selected_Video.mp4"

            VideoMetadata(
                uri = uri,
                fileName = fileName,
                durationMs = durationMs,
                width = width,
                height = height,
                fileSize = fileSize,
                mimeType = mimeType
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting metadata", e)
            null
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }
    }

    suspend fun extractThumbnail(context: Context, uri: Uri, positionMs: Long): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            if (Build.VERSION.SDK_INT >= 27) {
                retriever.getScaledFrameAtTime(
                    positionMs * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    320,
                    240
                )
            } else {
                retriever.getFrameAtTime(positionMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting thumbnail", e)
            null
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }
    }

    suspend fun trimVideo(
        context: Context,
        sourceUri: Uri,
        startMs: Long,
        endMs: Long,
        outputFileName: String,
        onProgress: (Float) -> Unit
    ): ProcessedVideo? = withContext(Dispatchers.IO) {
        val outputDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "TrimmedVideos")
        if (!outputDir.exists()) outputDir.mkdirs()

        val outputFile = File(outputDir, outputFileName)
        val startUs = startMs * 1000
        val endUs = endMs * 1000

        var pfd: ParcelFileDescriptor? = null
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null

        try {
            pfd = context.contentResolver.openFileDescriptor(sourceUri, "r")
            if (pfd == null) return@withContext null
            val fd: FileDescriptor = pfd.fileDescriptor

            extractor.setDataSource(fd)
            val trackCount = extractor.trackCount

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val trackIndexMap = HashMap<Int, Int>()
            var maxBufferSize = DEFAULT_BUFFER_SIZE

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue

                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    val muxerTrackIndex = muxer.addTrack(format)
                    trackIndexMap[i] = muxerTrackIndex

                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        val newSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                        if (newSize > maxBufferSize) {
                            maxBufferSize = newSize
                        }
                    }
                }
            }

            muxer.start()

            val buffer = ByteBuffer.allocateDirect(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            for ((extractorTrackIndex, muxerTrackIndex) in trackIndexMap) {
                extractor.unselectTrack(extractorTrackIndex)
            }

            // Copy each track individually within the time window
            val totalDurationUs = (endUs - startUs).coerceAtLeast(1L)

            for ((extractorTrackIndex, muxerTrackIndex) in trackIndexMap) {
                extractor.selectTrack(extractorTrackIndex)
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                while (true) {
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break

                    val sampleTime = extractor.sampleTime
                    if (sampleTime > endUs) break

                    if (sampleTime >= startUs) {
                        bufferInfo.offset = 0
                        bufferInfo.size = sampleSize
                        bufferInfo.presentationTimeUs = (sampleTime - startUs).coerceAtLeast(0L)
                        bufferInfo.flags = extractor.sampleFlags

                        muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)

                        val currentProgress = ((sampleTime - startUs).toFloat() / totalDurationUs).coerceIn(0f, 1f)
                        onProgress(currentProgress)
                    }

                    extractor.advance()
                }

                extractor.unselectTrack(extractorTrackIndex)
            }

            muxer.stop()
            onProgress(1.0f)

            val resultUri = Uri.fromFile(outputFile)
            val duration = (endMs - startMs).coerceAtLeast(0L)

            ProcessedVideo(
                id = System.currentTimeMillis().toString() + "_" + (1000..9999).random(),
                uri = resultUri,
                title = outputFile.name,
                durationMs = duration,
                fileSize = outputFile.length(),
                timestampMs = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error during video trim operation", e)
            if (outputFile.exists()) outputFile.delete()
            null
        } finally {
            try {
                muxer?.release()
            } catch (ignored: Exception) {}
            try {
                extractor.release()
            } catch (ignored: Exception) {}
            try {
                pfd?.close()
            } catch (ignored: Exception) {}
        }
    }

    suspend fun splitVideo(
        context: Context,
        sourceUri: Uri,
        segments: List<ClipSegment>,
        onProgress: (clipIndex: Int, progress: Float) -> Unit
    ): List<ProcessedVideo> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ProcessedVideo>()
        val total = segments.size

        segments.forEachIndexed { idx, segment ->
            val clipName = "Split_Clip_${idx + 1}_${System.currentTimeMillis() % 10000}.mp4"
            val processed = trimVideo(
                context = context,
                sourceUri = sourceUri,
                startMs = segment.startMs,
                endMs = segment.endMs,
                outputFileName = clipName
            ) { progress ->
                onProgress(idx + 1, progress)
            }

            if (processed != null) {
                results.add(processed)
            }
        }

        results
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var name: String? = null
        val scheme = uri.scheme
        if (scheme == ContentResolver.SCHEME_CONTENT) {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = it.getString(nameIndex)
                    }
                }
            }
        }
        if (name == null) {
            name = uri.path?.let { File(it).name }
        }
        return name
    }

    fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val millis = (ms % 1000) / 100
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format("%02d:%02d:%02d.%d", hours, minutes, seconds, millis)
        } else {
            String.format("%02d:%02d.%d", minutes, seconds, millis)
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.2f MB", mb)
            kb >= 1.0 -> String.format("%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    suspend fun saveToGallery(context: Context, videoUri: Uri, title: String): Boolean =
        MediaExport.save(context, videoUri, title, downloads = false)

    suspend fun saveToPublicStorage(context: Context, videoUri: Uri, title: String): Boolean =
        MediaExport.save(context, videoUri, title, downloads = true)
}
