package com.example.trimvideopro.model

/**
 * Represents a single transcript segment with timestamp and text
 */
data class TranscriptChunk(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String
) {
    val durationMs: Long get() = endMs - startMs
    
    fun formatTimestamp(): String {
        return "${formatTime(startMs)} --> ${formatTime(endMs)}"
    }
    
    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val millis = (ms % 1000)
        return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }
}

/**
 * Represents the complete transcript of a video or audio file
 */
data class Transcript(
    val videoUri: String,
    val chunks: List<TranscriptChunk>,
    /** Source-language code (90+ languages), or auto for provider detection. */
    val language: String = TranscriptLanguages.AUTO,
    val generatedAt: Long = System.currentTimeMillis()
) {
    fun toSrtFormat(): String {
        val builder = StringBuilder()
        chunks.forEachIndexed { index, chunk ->
            builder.append("${index + 1}\n")
            builder.append("${chunk.formatTimestamp()}\n")
            builder.append("${chunk.text}\n\n")
        }
        return builder.toString()
    }
    
    fun getFullText(): String {
        return chunks.joinToString(" ") { it.text }
    }
}

/**
 * Status of transcript generation
 */
sealed class TranscriptStatus {
    data object Idle : TranscriptStatus()
    data object ExtractingAudio : TranscriptStatus()
    data object SendingToGemini : TranscriptStatus()
    data object ProcessingResponse : TranscriptStatus()
    data class Success(val transcript: Transcript) : TranscriptStatus()
    data class Error(val message: String) : TranscriptStatus()
}

/**
 * Configuration for transcript generation
 */
data class TranscriptConfig(
    /** Source-language code (90+ languages), or auto for provider detection. */
    val language: String = TranscriptLanguages.AUTO,
    val chunkDurationMs: Long = 5000L, // Default 5 second chunks
    val includeTimestamps: Boolean = true
)
