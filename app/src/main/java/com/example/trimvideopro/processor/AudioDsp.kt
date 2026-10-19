package com.example.trimvideopro.processor

import com.example.trimvideopro.model.AudioEffects
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/** Stateful, bounded-memory processing of interleaved PCM. Separate filter state per channel. */
class AudioDsp(sampleRate: Int, private val channels: Int, effects: AudioEffects) {
    private val fx = effects.bounded()
    private val gain = db(fx.gainDb)
    private val lowGain = db(fx.bassDb)
    private val midGain = db(fx.midDb)
    private val highGain = db(fx.trebleDb)
    private val lowAlpha = 1.0 - exp(-2.0 * PI * 250 / sampleRate)
    private val highAlpha = 1.0 - exp(-2.0 * PI * 4000 / sampleRate)
    private val low = DoubleArray(channels)
    private val high = DoubleArray(channels)
    private val delay = DoubleArray((sampleRate * fx.echoDelayMs / 1000).coerceAtLeast(1) * channels)
    private var position = 0
    private var sampleIndex = 0L
    private val fadeFrames = sampleRate.toLong() * fx.fadeMs / 1000

    fun process(input: Float, frame: Long, totalFrames: Long): Float {
        val channel = (sampleIndex++ % channels).toInt()
        val x = if (input.isFinite()) input.toDouble() else 0.0
        low[channel] += lowAlpha * (x - low[channel])
        high[channel] += highAlpha * (x - high[channel])
        var y = (low[channel] * lowGain + (high[channel] - low[channel]) * midGain +
            (x - high[channel]) * highGain) * gain
        if (fx.noiseGate && abs(y) < 0.008) y *= abs(y) / 0.008
        if (fx.compressor && abs(y) > 0.25) {
            val amplitude = 0.25 + (abs(y) - 0.25) / 4
            y = if (y < 0) -amplitude else amplitude
        }
        val delayed = delay[position]
        delay[position] = y
        position = (position + 1) % delay.size
        y = y * (1 - fx.echoMix) + delayed * fx.echoMix
        if (fadeFrames > 0) {
            val fade = minOf(1.0, frame.toDouble() / fadeFrames,
                (totalFrames - frame - 1).coerceAtLeast(0).toDouble() / fadeFrames)
            y *= fade
        }
        return y.coerceIn(-1.0, 1.0).toFloat()
    }

    private fun db(value: Float) = 10.0.pow(value / 20.0)
}

/** Shared split math; integer boundaries cover every millisecond exactly once. */
object AudioSegments {
    fun byParts(durationMs: Long, parts: Int): List<Pair<Long, Long>> {
        require(durationMs > 0) { "Media has no duration." }
        require(parts in 2..50 && parts <= durationMs) { "Choose 2–50 non-empty parts." }
        return (0 until parts).map { durationMs * it / parts to durationMs * (it + 1) / parts }
    }

    fun byDuration(durationMs: Long, seconds: Int): List<Pair<Long, Long>> {
        require(durationMs > 0 && seconds in 1..3600) { "Choose a duration from 1–3600 seconds." }
        val interval = seconds * 1000L
        require((durationMs + interval - 1) / interval <= 50) { "Maximum 50 parts; increase the interval." }
        return generateSequence(0L) { it + interval }.takeWhile { it < durationMs }
            .map { it to minOf(it + interval, durationMs) }.toList()
    }
}
