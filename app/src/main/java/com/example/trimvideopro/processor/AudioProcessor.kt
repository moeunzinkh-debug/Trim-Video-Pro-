package com.example.trimvideopro.processor

import android.content.Context
import android.media.AudioFormat
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
        var output: BufferedOutputStream? = null
        try {
            var rate = 0
            var channels = 0
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            var dsp: AudioDsp? = null
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
                        if (keep) rendered.putShort((dsp!!.process(value, written, totalFrames) * 32767).toInt().toShort())
                    }
                    if (keep) written++
                }
                bytes += rendered.position()
                require(bytes <= 0xffff_ffffL - 36) { "WAV exceeds 4 GB. Split into shorter parts." }
                sink.write(rendered.array(), 0, rendered.position())
                onProgress(((timeUs / 1000f - startMs) / (endMs - startMs)).coerceIn(0f, 0.99f))
            }
            PcmDecoder.decode(context, source, startMs, endMs,
                onFormat = { newRate, newChannels, newEncoding ->
                    require(bytes == 0L || (newRate == rate && newChannels == channels)) { "Audio format changed mid-stream." }
                    rate = newRate; channels = newChannels; encoding = newEncoding
                    dsp = AudioDsp(rate, channels, effects)
                },
                onBuffer = ::consume)
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
