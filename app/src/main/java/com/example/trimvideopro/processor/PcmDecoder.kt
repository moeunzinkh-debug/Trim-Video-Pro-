package com.example.trimvideopro.processor

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.ByteBuffer

/** Shared streaming decoder. Callback buffers are valid only until the callback returns. */
object PcmDecoder {
    suspend fun decode(
        context: Context, source: Uri, startMs: Long, endMs: Long,
        onFormat: (rate: Int, channels: Int, encoding: Int) -> Unit,
        onBuffer: (ByteBuffer, timeUs: Long) -> Unit
    ) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(context, source, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("This media has no audio track.")
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Unknown audio format.")
            extractor.selectTrack(track)
            extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            fun changed(format: MediaFormat) {
                val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                require(rate > 0 && channels in 1..32) { "Invalid audio format." }
                val encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    else AudioFormat.ENCODING_PCM_16BIT
                require(encoding == AudioFormat.ENCODING_PCM_16BIT || encoding == AudioFormat.ENCODING_PCM_FLOAT) {
                    "Unsupported PCM audio encoding: $encoding"
                }
                onFormat(rate, channels, encoding)
            }
            changed(format)
            if (mime == "audio/raw") {
                val buffer = ByteBuffer.allocateDirect(1024 * 1024)
                while (extractor.sampleTime >= 0 && extractor.sampleTime < endMs * 1000) {
                    currentCoroutineContext().ensureActive()
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    buffer.limit(size)
                    onBuffer(buffer, extractor.sampleTime)
                    extractor.advance()
                }
            } else {
                val codec = MediaCodec.createDecoderByType(mime)
                decoder = codec
                codec.configure(format, null, null, 0)
                codec.start()
                val info = MediaCodec.BufferInfo()
                var inputEnded = false
                var outputEnded = false
                var idleCount = 0
                while (!outputEnded) {
                    currentCoroutineContext().ensureActive()
                    var advanced = false
                    if (!inputEnded) {
                        val index = codec.dequeueInputBuffer(10_000)
                        if (index >= 0) {
                            val buffer = codec.getInputBuffer(index) ?: error("Decoder input unavailable.")
                            val time = extractor.sampleTime
                            val size = if (time < 0 || time >= endMs * 1000) -1 else extractor.readSampleData(buffer, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEnded = true
                            } else {
                                codec.queueInputBuffer(index, 0, size, time, 0)
                                extractor.advance()
                            }
                            advanced = true
                        }
                    }
                    val index = codec.dequeueOutputBuffer(info, 10_000)
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        changed(codec.outputFormat)
                        advanced = true
                    } else if (index >= 0) {
                        try {
                            if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                val data = codec.getOutputBuffer(index) ?: error("Decoder output unavailable.")
                                data.position(info.offset); data.limit(info.offset + info.size)
                                onBuffer(data.slice(), info.presentationTimeUs)
                            }
                            outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        } finally { codec.releaseOutputBuffer(index, false) }
                        advanced = true
                    }
                    idleCount = if (advanced) 0 else idleCount + 1
                    check(idleCount < 1500) { "Audio decoder timed out." }
                }
            }
        } finally {
            runCatching { decoder?.stop() }; runCatching { decoder?.release() }; extractor.release()
        }
    }
}
