package com.example.trimvideopro.processor

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.trimvideopro.model.ProcessedVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

/** Fallback for mixed codecs, sizes, rotation, frame rates, or missing audio. Uses device codecs. */
@androidx.annotation.OptIn(UnstableApi::class)
object VideoCompositionProcessor {
    suspend fun merge(context: Context, uris: List<Uri>, onProgress: (Float) -> Unit): ProcessedVideo {
        val sources = uris.map { VideoProcessor.extractMetadata(context, it) ?: error("Cannot read a selected video.") }
        require(sources.all { it.durationMs > 0 && it.width > 0 && it.height > 0 }) { "Choose playable videos with a valid duration." }
        // Preserve the first video's aspect ratio, fitting other clips with letterboxing (never stretch).
        // Cap at 720p for broadly available Android encoders and lower memory use on phones.
        val (width, height) = outputSize(sources.first().width, sources.first().height)
        val directory = File(context.getExternalFilesDir(null) ?: context.filesDir, "TrimmedVideos").apply { mkdirs() }
        val output = File(directory, "Merged_${UUID.randomUUID()}.mp4")
        val partial = File(directory, "${output.name}.partial")
        try {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    val handler = Handler(Looper.getMainLooper())
                    val items = uris.map { uri ->
                        val channels = ChannelMixingAudioProcessor().apply {
                            for (count in 1..8) {
                                val matrix = FloatArray(count * 2)
                                if (count == 1) { matrix[0] = 1f; matrix[1] = 1f }
                                else {
                                    matrix[0] = 1f; matrix[3] = 1f
                                    // Keep center dialogue and surround content when downmixing multi-channel sources.
                                    if (count > 2) { matrix[4] = 0.707f; matrix[5] = 0.707f }
                                    for (channel in 3 until count) {
                                        val weight = if (channel == 3) 0.25f else 0.5f
                                        matrix[channel * 2] = weight
                                        matrix[channel * 2 + 1] = weight
                                    }
                                    val left = (0 until count).sumOf { matrix[it * 2].toDouble() }
                                    val right = (0 until count).sumOf { matrix[it * 2 + 1].toDouble() }
                                    val normalization = maxOf(left, right, 1.0).toFloat()
                                    for (i in matrix.indices) matrix[i] /= normalization
                                }
                                putChannelMixingMatrix(ChannelMixingMatrix(count, 2, matrix))
                            }
                        }
                        EditedMediaItem.Builder(MediaItem.fromUri(uri))
                            .setEffects(Effects(
                                listOf(channels, SonicAudioProcessor().apply { setOutputSampleRateHz(48000) }),
                                listOf(Presentation.createForWidthAndHeight(width, height, Presentation.LAYOUT_SCALE_TO_FIT))
                            )).build()
                    }
                    val sequence = EditedMediaItemSequence.Builder(items).experimentalSetForceAudioTrack(true).build()
                    val composition = Composition.Builder(sequence)
                        .setTransmuxAudio(false).setTransmuxVideo(false)
                        .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL).build()
                    var poll: Runnable? = null
                    val transformer = Transformer.Builder(context.applicationContext)
                        .setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                poll?.let { handler.removeCallbacks(it) }
                                if (continuation.isActive) continuation.resume(Unit)
                            }
                            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                                poll?.let { handler.removeCallbacks(it) }
                                if (continuation.isActive) continuation.resumeWithException(IllegalStateException(
                                    "Cannot convert this video on this device (${exportException.errorCodeName}). Try smaller SDR clips or free storage.", exportException))
                            }
                        }).build()
                    poll = object : Runnable {
                        override fun run() {
                            if (!continuation.isActive) return
                            val progress = ProgressHolder()
                            if (transformer.getProgress(progress) == Transformer.PROGRESS_STATE_AVAILABLE) {
                                onProgress(progress.progress / 100f)
                            }
                            handler.postDelayed(this, 300)
                        }
                    }
                    continuation.invokeOnCancellation {
                        handler.post { poll?.let { handler.removeCallbacks(it) }; transformer.cancel(); partial.delete() }
                    }
                    try {
                        transformer.start(composition, partial.absolutePath)
                        handler.post(poll!!)
                    } catch (e: Exception) {
                        transformer.cancel()
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                }
            }
            check(partial.length() > 0 && partial.renameTo(output)) { "Cannot finalize merged video." }
            onProgress(1f)
            return ProcessedVideo(UUID.randomUUID().toString(), Uri.fromFile(output), output.name,
                sources.sumOf { it.durationMs }, output.length())
        } catch (e: Throwable) {
            partial.delete(); output.delete(); throw e
        }
    }

    internal fun outputSize(width: Int, height: Int): Pair<Int, Int> {
        require(width > 0 && height > 0)
        val scale = minOf(1.0, 1280.0 / maxOf(width, height), 720.0 / minOf(width, height))
        fun even(value: Int) = ((value * scale).roundToInt() / 2 * 2).coerceAtLeast(2)
        return even(width) to even(height)
    }
}
