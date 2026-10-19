package com.example.trimvideopro

import com.example.trimvideopro.model.AudioEffects
import com.example.trimvideopro.processor.AudioDsp
import com.example.trimvideopro.processor.AudioSegments
import org.junit.Test
import kotlin.math.abs

/** Pure JVM checks: no Android device, API key, or network needed. */
class AudioEngineTest {
    @Test fun neutralEffectsPreserveSamples() {
        val dsp = AudioDsp(48000, 2, AudioEffects())
        val values = listOf(-1f, -0.5f, 0f, 0.25f, 0.8f, 1f)
        values.forEachIndexed { index, value ->
            check(abs(dsp.process(value, index / 2L, 100) - value) < 0.00001f)
        }
    }

    @Test fun echoIsDelayedAndDoesNotLeakBetweenChannels() {
        val dsp = AudioDsp(1000, 2, AudioEffects(echoMix = 0.25f, echoDelayMs = 50))
        for (frame in 0L..60L) {
            val left = dsp.process(if (frame == 0L) 1f else 0f, frame, 100)
            val right = dsp.process(0f, frame, 100)
            val expected = when (frame) { 0L -> 0.75f; 50L -> 0.25f; else -> 0f }
            check(abs(left - expected) < 0.00001f) { "Unexpected echo at frame $frame: $left" }
            check(right == 0f) { "Left-channel echo leaked into the right channel." }
        }
    }

    @Test fun gainClampsRatherThanOverflowing() {
        val dsp = AudioDsp(48000, 1, AudioEffects(gainDb = 18f))
        check(dsp.process(0.9f, 0, 100) == 1f)
        check(dsp.process(-0.9f, 1, 100) == -1f)
    }

    @Test fun compressorReducesPeaks() {
        val dsp = AudioDsp(48000, 1, AudioEffects(compressor = true))
        check(abs(dsp.process(1f, 0, 100) - 0.4375f) < 0.00001)
    }

    @Test fun noiseGateLeavesLouderAudioAlone() {
        val dsp = AudioDsp(48000, 1, AudioEffects(noiseGate = true))
        check(abs(dsp.process(0.1f, 0, 100) - 0.1f) < 0.00001)
        check(abs(dsp.process(0.001f, 1, 100)) < 0.001f)
    }

    @Test fun fadeStartsAndEndsAtZero() {
        val dsp = AudioDsp(1000, 1, AudioEffects(fadeMs = 10))
        check(dsp.process(1f, 0, 100) == 0f)
        check(dsp.process(1f, 50, 100) == 1f)
        check(dsp.process(1f, 99, 100) == 0f)
    }

    @Test fun invalidSettingsAreBounded() {
        val fx = AudioEffects(gainDb = Float.NaN, bassDb = Float.POSITIVE_INFINITY,
            echoMix = 5f, echoDelayMs = -1, fadeMs = 99999).bounded()
        check(fx.gainDb == 0f && fx.bassDb == 0f)
        check(fx.echoMix == 0.6f && fx.echoDelayMs == 50 && fx.fadeMs == 3000)
    }

    @Test fun nonFiniteSamplesDoNotPoisonFilterState() {
        val dsp = AudioDsp(48000, 1, AudioEffects())
        check(dsp.process(Float.NaN, 0, 100).isFinite())
        check(dsp.process(0.5f, 1, 100).isFinite())
    }

    @Test fun unequalPartsHaveNoGapsOrOverlaps() {
        val parts = AudioSegments.byParts(10001, 3)
        check(parts == listOf(0L to 3333L, 3333L to 6667L, 6667L to 10001L))
        check(parts.sumOf { it.second - it.first } == 10001L)
    }

    @Test fun durationSplitIncludesRemainder() {
        check(AudioSegments.byDuration(2500, 1) == listOf(0L to 1000L, 1000L to 2000L, 2000L to 2500L))
    }

    @Test fun invalidOrExcessiveSplitsFailBeforeExport() {
        check(runCatching { AudioSegments.byParts(0, 3) }.isFailure)
        check(runCatching { AudioSegments.byParts(1, 3) }.isFailure)
        check(runCatching { AudioSegments.byParts(10000, 51) }.isFailure)
        check(runCatching { AudioSegments.byDuration(10000, 0) }.isFailure)
        check(runCatching { AudioSegments.byDuration(51000, 1) }.isFailure)
    }
}
