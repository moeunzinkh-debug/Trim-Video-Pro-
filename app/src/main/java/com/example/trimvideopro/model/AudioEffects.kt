package com.example.trimvideopro.model

/** Exported effects are rendered into the audio, not just applied to playback. */
data class AudioEffects(
    val gainDb: Float = 0f,
    val bassDb: Float = 0f,
    val midDb: Float = 0f,
    val trebleDb: Float = 0f,
    val echoMix: Float = 0f,
    val echoDelayMs: Int = 250,
    val compressor: Boolean = false,
    val noiseGate: Boolean = false,
    val fadeMs: Int = 0
) {
    fun bounded() = copy(
        gainDb = gainDb.finite().coerceIn(-18f, 18f),
        bassDb = bassDb.finite().coerceIn(-12f, 12f),
        midDb = midDb.finite().coerceIn(-12f, 12f),
        trebleDb = trebleDb.finite().coerceIn(-12f, 12f),
        echoMix = echoMix.finite().coerceIn(0f, 0.6f),
        echoDelayMs = echoDelayMs.coerceIn(50, 800),
        fadeMs = fadeMs.coerceIn(0, 3000)
    )
}

private fun Float.finite() = if (isFinite()) this else 0f

enum class TranscriptProvider(val label: String) {
    GEMINI("Gemini"),
    GROQ_WHISPER("Groq · Whisper Large v3")
}

object AudioPresets {
    val speech = AudioEffects(bassDb = -3f, midDb = 3f, trebleDb = 2f, compressor = true, noiseGate = true, fadeMs = 100)
    val music = AudioEffects(bassDb = 3f, trebleDb = 2f, fadeMs = 500)
    val room = AudioEffects(echoMix = 0.2f, echoDelayMs = 180, fadeMs = 200)
}
