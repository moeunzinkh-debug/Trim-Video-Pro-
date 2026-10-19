package com.example.trimvideopro.processor

import java.io.BufferedOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

internal data class SpeechChunk(val file: File, val sourceStartMs: Long, val durationMs: Long)

/** Lightweight energy VAD for quiet split boundaries, not neural denoising or speaker detection. */
internal class EnergyVad(private val frameSamples: Int = 320) {
    private var sum = 0.0
    private var count = 0
    fun accept(sample: Float): Boolean? {
        sum += sample * sample
        if (++count < frameSamples) return null
        val voice = sqrt(sum / count) >= 0.0015
        sum = 0.0; count = 0
        return voice
    }
}

/** 16 kHz mono PCM16 WAV chunks, <= 8 minutes / 15.4 MB each, retaining original timeline. */
internal class SpeechChunkWriter(
    private val directory: File,
    private val softLimitSamples: Long = 7L * 60 * RATE,
    private val hardLimitSamples: Long = 8L * 60 * RATE
) : AutoCloseable {
    companion object { const val RATE = 16000 }
    private val vad = EnergyVad()
    private var sink: BufferedOutputStream? = null
    private var file: File? = null
    private var samples = 0L
    private var totalSamples = 0L
    private var start = 0L
    private var silence = 0L
    private var hasActivity = false
    private val completed = mutableListOf<SpeechChunk>()

    init { require(softLimitSamples > 0 && hardLimitSamples >= softLimitSamples); directory.mkdirs() }

    fun append(sample: Float) {
        if (sink == null) {
            file = File.createTempFile("speech_", ".wav", directory)
            sink = BufferedOutputStream(file!!.outputStream(), 64 * 1024).apply { write(ByteArray(44)) }
            start = totalSamples; samples = 0; silence = 0; hasActivity = false
        }
        val value = (if (sample.isFinite()) sample else 0f).coerceIn(-1f, 1f)
        // Only discard digital/near-digital silence, never low-volume speech merely below the VAD threshold.
        if (abs(value) > 0.00001f) hasActivity = true
        val pcm = (value * 32767).toInt()
        sink!!.write(pcm and 255); sink!!.write((pcm shr 8) and 255)
        samples++; totalSamples++
        vad.accept(value)?.let { voice -> silence = if (voice) 0 else silence + 320 }
        if (samples >= hardLimitSamples || (samples >= softLimitSamples && silence >= RATE / 2)) finishChunk()
    }

    private fun finishChunk() {
        sink?.close(); sink = null
        val current = file ?: return
        if (samples > 0 && hasActivity) {
            RandomAccessFile(current, "rw").use { it.write(header(samples * 2)) }
            completed.add(SpeechChunk(current, start * 1000 / RATE, samples * 1000 / RATE))
        } else current.delete()
        file = null
    }

    fun finish(): List<SpeechChunk> { finishChunk(); return completed.toList() }

    override fun close() {
        runCatching { sink?.close() }; sink = null
        file?.delete(); completed.forEach { it.file.delete() }
    }

    private fun header(bytes: Long): ByteArray = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray(Charsets.US_ASCII)); putInt((bytes + 36).toInt())
        put("WAVEfmt ".toByteArray(Charsets.US_ASCII)); putInt(16); putShort(1); putShort(1)
        putInt(RATE); putInt(RATE * 2); putShort(2); putShort(16)
        put("data".toByteArray(Charsets.US_ASCII)); putInt(bytes.toInt())
    }.array()
}
