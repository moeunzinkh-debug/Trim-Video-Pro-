package com.example.trimvideopro.processor

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.example.trimvideopro.model.AudioEffects
import com.example.trimvideopro.model.ProcessedVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/** Native decoder -> streaming DSP -> PCM16 WAV. No full-media allocation or external binaries. */
object AudioProcessor {
    suspend fun render(
        context: Context,
        source: Uri,
        startMs: Long = 0,
        endMs: Long,
        effects: AudioEffects = AudioEffects(),
        prefix: String = "Audio",
        onProgress: (Float) -> Unit
    ): ProcessedVideo = withContext(Dispatchers.IO) {
        require(startMs >= 0 && endMs > startMs) { "Choose a non-empty audio range." }
        val directory = File(context.getExternalFilesDir(null) ?: context.filesDir, "TrimmedVideos").apply { mkdirs() }
        val completedFile = File(directory, "${prefix}_${UUID.randomUUID()}.wav")
        val file = File(directory, "${completedFile.name}.partial")
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var output: BufferedOutputStream? = null
        try {
            extractor.setDataSource(context, source, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("This media has no audio track.")
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Unknown audio format.")
            extractor.selectTrack(track)
            extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            var dsp = AudioDsp(rate, channels, effects)
            var written = 0L
            var bytes = 0L
            val sink = BufferedOutputStream(file.outputStream(), 64 * 1024)
            output = sink
            sink.write(ByteArray(44))
            fun consume(data: ByteBuffer, timeUs: Long) {
                val sampleBytes = when (encoding) {
                    AudioFormat.ENCODING_PCM_16BIT -> 2
                    AudioFormat.ENCODING_PCM_FLOAT -> 4
                    else -> error("Unsupported decoder PCM encoding: $encoding")
                }
                val frames = data.remaining() / (sampleBytes * channels)
                val rendered = ByteBuffer.allocate(frames * channels * 2).order(ByteOrder.LITTLE_ENDIAN)
                val totalFrames = (endMs - startMs) * rate / 1000
                data.order(ByteOrder.LITTLE_ENDIAN)
                for (frame in 0 until frames) {
                    val timestamp = timeUs + frame * 1_000_000L / rate
                    val keep = timestamp >= startMs * 1000 && timestamp < endMs * 1000
                    for (channel in 0 until channels) {
                        val value = if (sampleBytes == 4) data.float else data.short / 32768f
                        if (keep) rendered.putShort((dsp.process(value, written, totalFrames) * 32767).toInt().toShort())
                    }
                    if (keep) written++
                }
                bytes += rendered.position()
                require(bytes <= 0xffff_ffffL - 36) { "WAV exceeds 4 GB. Split into shorter parts." }
                sink.write(rendered.array(), 0, rendered.position())
                onProgress(((timeUs / 1000f - startMs) / (endMs - startMs)).coerceIn(0f, 0.99f))
            }
            if (mime == "audio/raw") {
                if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                val buffer = ByteBuffer.allocateDirect(1024 * 1024)
                while (extractor.sampleTime >= 0 && extractor.sampleTime < endMs * 1000) {
                    currentCoroutineContext().ensureActive()
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    buffer.limit(size)
                    consume(buffer, extractor.sampleTime)
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
                            if (time < 0 || time >= endMs * 1000) {
                                codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEnded = true
                            } else {
                                val size = extractor.readSampleData(buffer, 0)
                                if (size < 0) {
                                    codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    inputEnded = true
                                } else {
                                    codec.queueInputBuffer(index, 0, size, time, 0)
                                    extractor.advance()
                                }
                            }
                            advanced = true
                        }
                    }
                    val index = codec.dequeueOutputBuffer(info, 10_000)
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val decoded = codec.outputFormat
                        val newRate = decoded.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        val newChannels = decoded.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        require(bytes == 0L || (newRate == rate && newChannels == channels)) { "Audio format changed mid-stream." }
                        rate = newRate
                        channels = newChannels
                        encoding = if (decoded.containsKey(MediaFormat.KEY_PCM_ENCODING))
                            decoded.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                        dsp = AudioDsp(rate, channels, effects)
                        advanced = true
                    } else if (index >= 0) {
                        try {
                            if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                val data = codec.getOutputBuffer(index) ?: error("Decoder output unavailable.")
                                data.position(info.offset)
                                data.limit(info.offset + info.size)
                                consume(data.slice(), info.presentationTimeUs)
                            }
                            outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        } finally { codec.releaseOutputBuffer(index, false) }
                        advanced = true
                    }
                    idleCount = if (advanced) 0 else idleCount + 1
                    check(idleCount < 1500) { "Audio decoder timed out." }
                }
            }
            sink.close()
            output = null
            check(bytes > 0) { "No audio samples in this range." }
            RandomAccessFile(file, "rw").use { wav -> wav.write(wavHeader(bytes, rate, channels)) }
            currentCoroutineContext().ensureActive()
            check(file.renameTo(completedFile)) { "Cannot finalize exported audio." }
            onProgress(1f)
            ProcessedVideo(UUID.randomUUID().toString(), Uri.fromFile(completedFile), completedFile.name,
                written * 1000 / rate, completedFile.length())
        } catch (e: Throwable) {
            runCatching { output?.close() }
            file.delete()
            completedFile.delete()
            throw e
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            extractor.release()
        }
    }

    internal fun wavHeader(bytes: Long, rate: Int, channels: Int): ByteArray =
        ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII)); putInt((bytes + 36).toInt())
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII)); putInt(16); putShort(1.toShort())
            putShort(channels.toShort()); putInt(rate); putInt(rate * channels * 2)
            putShort((channels * 2).toShort()); putShort(16.toShort())
            put("data".toByteArray(Charsets.US_ASCII)); putInt(bytes.toInt())
        }.array()
}
