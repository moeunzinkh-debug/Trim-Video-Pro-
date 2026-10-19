package com.example.trimvideopro.processor

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import com.example.trimvideopro.model.ProcessedVideo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID

/** Prefer lossless concatenation; automatically transcode incompatible streams on-device. */
object MergeProcessor {
    private data class Source(val formats: List<MediaFormat>, val indices: List<Int>, val rotation: Int, val durationUs: Long)

    suspend fun merge(context: Context, uris: List<Uri>, onProgress: (Float) -> Unit): ProcessedVideo {
        require(uris.size in 2..20) { "Choose 2–20 videos." }
        return try { mergeLossless(context, uris, onProgress) } catch (e: CancellationException) { throw e } catch (_: Exception) {
            onProgress(0f)
            VideoCompositionProcessor.merge(context, uris, onProgress)
        }
    }

    private suspend fun mergeLossless(context: Context, uris: List<Uri>, onProgress: (Float) -> Unit): ProcessedVideo =
        withContext(Dispatchers.IO) {
            require(uris.size in 2..20) { "Choose 2–20 videos." }
            val sources = uris.map { uri ->
                val extractor = MediaExtractor()
                val retriever = MediaMetadataRetriever()
                try {
                    extractor.setDataSource(context, uri, null)
                    retriever.setDataSource(context, uri)
                    val indices = (0 until extractor.trackCount).filter {
                        val mime = extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty()
                        mime.startsWith("video/") || mime.startsWith("audio/")
                    }.sortedBy { if (extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("video/")) 0 else 1 }
                    val formats = indices.map { extractor.getTrackFormat(it) }
                    require(formats.count { it.getString(MediaFormat.KEY_MIME)!!.startsWith("video/") } == 1 &&
                        formats.count { it.getString(MediaFormat.KEY_MIME)!!.startsWith("audio/") } <= 1) {
                        "Use videos with one video track and at most one audio track."
                    }
                    Source(formats, indices,
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0,
                        (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) * 1000)
                } finally { extractor.release(); retriever.release() }
            }
            val first = sources.first()
            sources.drop(1).forEach { source ->
                require(source.rotation == first.rotation && source.formats.size == first.formats.size &&
                    source.formats.indices.all { compatible(first.formats[it], source.formats[it]) }) {
                    "Videos must have matching codecs, resolution, orientation, and audio settings (including audio presence). Re-encode mismatched clips first."
                }
            }
            val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "TrimmedVideos").apply { mkdirs() }
            val completedFile = File(dir, "Merged_${UUID.randomUUID()}.mp4")
            val file = File(dir, "${completedFile.name}.partial")
            var muxer: MediaMuxer? = null
            try {
                val writer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                muxer = writer
                writer.setOrientationHint(first.rotation)
                val outputTracks = first.formats.map { writer.addTrack(it) }
                writer.start()
                var offsetUs = 0L
                val lastPts = LongArray(outputTracks.size) { -1 }
                uris.forEachIndexed { clip, uri ->
                    currentCoroutineContext().ensureActive()
                    val source = sources[clip]
                    val extractor = MediaExtractor()
                    try {
                        extractor.setDataSource(context, uri, null)
                        source.indices.forEach { extractor.selectTrack(it) }
                        val origin = extractor.sampleTime.coerceAtLeast(0)
                        val bufferSize = source.formats.maxOf { format ->
                            if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 4 * 1024 * 1024
                        }.coerceAtLeast(4 * 1024 * 1024)
                        val buffer = ByteBuffer.allocateDirect(bufferSize)
                        val info = MediaCodec.BufferInfo()
                        var endUs = offsetUs
                        val previousLocal = LongArray(outputTracks.size) { -1 }
                        while (extractor.sampleTrackIndex >= 0) {
                            currentCoroutineContext().ensureActive()
                            val track = source.indices.indexOf(extractor.sampleTrackIndex)
                            buffer.clear()
                            val size = extractor.readSampleData(buffer, 0)
                            if (size < 0) break
                            val local = (extractor.sampleTime - origin).coerceAtLeast(0)
                            val pts = offsetUs + local
                            require(pts > lastPts[track]) { "Non-monotonic video timestamps; re-encode this clip before merging." }
                            require(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) { "Protected media cannot be merged." }
                            info.set(0, size, pts,
                                if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                            writer.writeSampleData(outputTracks[track], buffer, info)
                            lastPts[track] = pts
                            val step = if (previousLocal[track] >= 0) (local - previousLocal[track]).coerceAtLeast(1) else 1L
                            previousLocal[track] = local
                            endUs = maxOf(endUs, pts + step)
                            onProgress((clip + (local.toFloat() / source.durationUs.coerceAtLeast(1)).coerceIn(0f, 1f)) / uris.size)
                            extractor.advance()
                        }
                        offsetUs = maxOf(endUs, offsetUs + source.durationUs)
                    } finally { extractor.release() }
                }
                writer.stop()
                writer.release()
                muxer = null
                currentCoroutineContext().ensureActive()
                check(file.renameTo(completedFile)) { "Cannot finalize merged video." }
                onProgress(1f)
                ProcessedVideo(UUID.randomUUID().toString(), Uri.fromFile(completedFile), completedFile.name, offsetUs / 1000, completedFile.length())
            } catch (e: Throwable) {
                runCatching { muxer?.release() }
                muxer = null
                file.delete()
                completedFile.delete()
                throw e
            } finally { runCatching { muxer?.release() } }
        }

    private fun number(format: MediaFormat, key: String): Double =
        try { format.getInteger(key).toDouble() } catch (_: ClassCastException) { format.getFloat(key).toDouble() }

    private fun compatible(a: MediaFormat, b: MediaFormat): Boolean {
        val mime = a.getString(MediaFormat.KEY_MIME)
        if (mime != b.getString(MediaFormat.KEY_MIME)) return false
        val keys = if (mime?.startsWith("video/") == true)
            listOf(MediaFormat.KEY_WIDTH, MediaFormat.KEY_HEIGHT, MediaFormat.KEY_FRAME_RATE, MediaFormat.KEY_PROFILE, MediaFormat.KEY_LEVEL)
        else listOf(MediaFormat.KEY_SAMPLE_RATE, MediaFormat.KEY_CHANNEL_COUNT, MediaFormat.KEY_AAC_PROFILE)
        for (key in keys) {
            if (a.containsKey(key) != b.containsKey(key)) return false
            if (a.containsKey(key) && number(a, key) != number(b, key)) return false
        }
        for (key in listOf("csd-0", "csd-1", "csd-2")) {
            val x = a.getByteBuffer(key)?.duplicate()
            val y = b.getByteBuffer(key)?.duplicate()
            if (x != y) return false
        }
        return true
    }
}
