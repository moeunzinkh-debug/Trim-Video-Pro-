package com.example.trimvideopro.processor

import android.content.Context
import android.media.AudioFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder

/** Read the first audio track only; downmix/resample while decoding, without caching the video. */
internal object SpeechAudioProcessor {
    suspend fun prepare(context: Context, source: Uri, durationMs: Long, onProgress: (Float) -> Unit): List<SpeechChunk> =
        withContext(Dispatchers.IO) {
            val writer = SpeechChunkWriter(File(context.cacheDir, "speech_uploads"))
            try {
                var rate = 0
                var channels = 0
                var encoding = 0
                var originTick: Long? = null
                var nextTick = 0L
                PcmDecoder.decode(context, source, 0, if (durationMs > 0) durationMs + 1000 else Long.MAX_VALUE / 1000,
                    onFormat = { sampleRate, count, pcm -> rate = sampleRate; channels = count; encoding = pcm },
                    onBuffer = { buffer, timeUs ->
                        buffer.order(ByteOrder.LITTLE_ENDIAN)
                        val sampleBytes = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                        val frames = buffer.remaining() / (channels * sampleBytes)
                        for (frame in 0 until frames) {
                            var mono = 0f
                            repeat(channels) {
                                val value = if (sampleBytes == 4) buffer.float else buffer.short / 32768f
                                mono += if (value.isFinite()) value / channels else 0f
                            }
                            val tick = ((timeUs + frame * 1_000_000L / rate).coerceAtLeast(0) * SpeechChunkWriter.RATE / 1_000_000)
                            if (originTick == null) { originTick = tick; nextTick = tick }
                            // Preserve discontinuities as silence so subtitle offsets remain in source time.
                            val expectedStep = (SpeechChunkWriter.RATE + rate - 1) / rate
                            if (tick - nextTick > expectedStep + 2) {
                                while (nextTick < tick) { writer.append(0f); nextTick++ }
                            }
                            while (nextTick <= tick) { writer.append(mono); nextTick++ }
                        }
                        onProgress(if (durationMs > 0) (timeUs / 1000f / durationMs).coerceIn(0f, 1f) else 0f)
                    })
                val offsetMs = (originTick ?: 0) * 1000 / SpeechChunkWriter.RATE
                writer.finish().map { it.copy(sourceStartMs = it.sourceStartMs + offsetMs) }
            } catch (e: Throwable) { writer.close(); throw e }
        }
}
