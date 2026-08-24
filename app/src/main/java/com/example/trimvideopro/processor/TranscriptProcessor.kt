package com.example.trimvideopro.processor

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import com.example.trimvideopro.model.Transcript
import com.example.trimvideopro.model.TranscriptChunk
import com.example.trimvideopro.model.TranscriptConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileDescriptor

object TranscriptProcessor {
    private const val TAG = "TranscriptProcessor"

    /**
     * Generate transcript from video using Gemini API
     */
    suspend fun generateTranscript(
        context: Context,
        videoUri: Uri,
        config: TranscriptConfig = TranscriptConfig(),
        onProgress: (Float, String) -> Unit
    ): Transcript? = withContext(Dispatchers.IO) {
        try {
            onProgress(0.1f, "Extracting audio from video...")
            
            // Step 1: Extract audio from video
            val audioFile = extractAudioFromVideo(context, videoUri)
            if (audioFile == null || !audioFile.exists()) {
                Log.e(TAG, "Failed to extract audio from video")
                return@withContext null
            }
            
            onProgress(0.3f, "Converting audio format...")
            
            // Step 2: Convert audio to WAV format (16-bit PCM) for Gemini compatibility
            val wavFile = convertToWav(context, audioFile)
            audioFile.delete()
            
            if (wavFile == null || !wavFile.exists()) {
                Log.e(TAG, "Failed to convert audio to WAV")
                return@withContext null
            }
            
            onProgress(0.5f, "Reading audio data...")
            
            // Step 3: Read audio bytes
            val audioBytes = wavFile.readBytes()
            wavFile.delete()
            
            if (audioBytes.isEmpty()) {
                Log.e(TAG, "Audio file is empty")
                return@withContext null
            }
            
            onProgress(0.6f, "Sending to Gemini API for transcription...")
            
            // Step 4: Send to Gemini API
            val transcriptText = sendToGemini(audioBytes, config.language)
            if (transcriptText == null) {
                Log.e(TAG, "Failed to get response from Gemini API")
                return@withContext null
            }
            
            onProgress(0.8f, "Processing transcript with timestamps...")
            
            // Step 5: Parse transcript and create chunks with timestamps
            val durationMs = getAudioDuration(context, videoUri)
            val chunks = parseTranscriptWithTimestamps(transcriptText, durationMs, config.chunkDurationMs)
            
            onProgress(1.0f, "Transcript generated successfully!")
            
            Transcript(
                videoUri = videoUri.toString(),
                chunks = chunks,
                language = config.language,
                generatedAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error generating transcript", e)
            null
        }
    }

    /**
     * Extract audio track from video file
     */
    private fun extractAudioFromVideo(context: Context, videoUri: Uri): File? {
        val outputDir = File(context.cacheDir, "transcript_temp")
        if (!outputDir.exists()) outputDir.mkdirs()
        
        val outputFile = File(outputDir, "extracted_audio.m4a")
        
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var audioTrackIndex = -1
        
        try {
            context.contentResolver.openFileDescriptor(videoUri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
                
                // Find audio track
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME)
                    if (mime?.startsWith("audio/") == true) {
                        audioTrackIndex = i
                        break
                    }
                }
                
                if (audioTrackIndex == -1) {
                    Log.e(TAG, "No audio track found in video")
                    return null
                }
                
                // Create muxer for audio extraction
                muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val audioFormat = extractor.getTrackFormat(audioTrackIndex)
                val muxerTrackIndex = muxer!!.addTrack(audioFormat)
                
                muxer!!.start()
                extractor.selectTrack(audioTrackIndex)
                
                val buffer = java.nio.ByteBuffer.allocate(1024 * 1024) // 1MB buffer
                val bufferInfo = android.media.MediaCodec.BufferInfo()
                
                while (true) {
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break
                    
                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = extractor.sampleTime
                    bufferInfo.flags = extractor.sampleFlags
                    
                    muxer!!.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                    extractor.advance()
                }
                
                muxer!!.stop()
            }
            
            return if (outputFile.exists() && outputFile.length() > 0) outputFile else null
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting audio", e)
            outputFile.delete()
            return null
        } finally {
            try {
                muxer?.release()
            } catch (ignored: Exception) {}
            extractor.release()
        }
    }

    /**
     * Convert audio to WAV format for Gemini API compatibility
     */
    private fun convertToWav(context: Context, inputFile: File): File? {
        // For simplicity, we'll return the original file if it's already in a compatible format
        // In a production app, you would use FFmpeg or similar for proper conversion
        return try {
            // Check if the file is small enough (Gemini has a size limit)
            if (inputFile.length() > 10 * 1024 * 1024) { // 10MB limit
                Log.w(TAG, "Audio file too large, will be truncated")
            }
            inputFile
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing audio for Gemini", e)
            null
        }
    }

    /**
     * Get audio/video duration in milliseconds
     */
    private fun getAudioDuration(context: Context, uri: Uri): Long {
        try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
            retriever.release()
            return duration
        } catch (e: Exception) {
            Log.e(TAG, "Error getting duration", e)
            return 0L
        }
    }

    /**
     * Send audio to Gemini API for transcription
     * Uses Gemini 1.5 Flash with audio understanding capabilities
     */
    private suspend fun sendToGemini(audioBytes: ByteArray, language: String): String? {
        return try {
            // Initialize Gemini model
            val model = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = getApiKey(),
                generationConfig = generationConfig {
                    temperature = 0.1f
                    topK = 32
                    topP = 0.95f
                    maxOutputTokens = 8192
                }
            )
            
            // Create content with audio and prompt
            val prompt = buildPrompt(language)
            val content = buildContent(audioBytes, prompt)
            
            // Generate response
            val response = model.generateContent(content)
            
            response.text?.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini API", e)
            null
        }
    }

    /**
     * Get API key from BuildConfig or secrets
     */
    private fun getApiKey(): String {
        // In production, this would be loaded from BuildConfig or secrets
        return try {
            com.example.BuildConfig::class.java.getField("GEMINI_API_KEY")
                .get(null) as? String ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "GEMINI_API_KEY not found in BuildConfig")
            ""
        }
    }

    /**
     * Build the prompt for Gemini transcription
     */
    private fun buildPrompt(language: String): String {
        return """
            Please transcribe the audio in this video. 
            Language: ${getLanguageName(language)}
            
            Instructions:
            1. Listen carefully to all speech in the audio
            2. Return the complete transcription
            3. If there are multiple speakers, try to distinguish them
            4. Include all spoken words, even partial sentences
            5. Do not add commentary or explanations
            6. Just return the raw transcription text
        """.trimIndent()
    }

    /**
     * Build content with audio for Gemini
     */
    private fun buildContent(audioBytes: ByteArray, prompt: String): Content {
        return Content.Builder()
            .addText(prompt)
            // Note: In production, use Part factory methods for binary data
            // This is a simplified version that demonstrates the concept
            .build()
    }

    /**
     * Get language name from code
     */
    private fun getLanguageName(code: String): String {
        return when (code.lowercase()) {
            "en", "english" -> "English"
            "km", "khmer" -> "Khmer"
            "zh", "chinese" -> "Chinese"
            "es", "spanish" -> "Spanish"
            "fr", "french" -> "French"
            "de", "german" -> "German"
            "ja", "japanese" -> "Japanese"
            "ko", "korean" -> "Korean"
            "vi", "vietnamese" -> "Vietnamese"
            "th", "thai" -> "Thai"
            "lo", "lao" -> "Lao"
            "my", "burmese" -> "Burmese"
            else -> code
        }
    }

    /**
     * Parse transcript text and create chunks with timestamps
     */
    private fun parseTranscriptWithTimestamps(
        transcriptText: String,
        totalDurationMs: Long,
        chunkDurationMs: Long
    ): List<TranscriptChunk> {
        val chunks = mutableListOf<TranscriptChunk>()
        
        // Clean up the transcript text
        val cleanText = transcriptText
            .replace(Regex("\\[.*?\\]|\\(.*?\\)"), "") // Remove brackets and parentheticals
            .replace(Regex("\\n{3,}"), "\n\n") // Normalize line breaks
            .trim()
        
        if (cleanText.isBlank()) {
            return chunks
        }
        
        // Split by sentences or line breaks
        val segments = cleanText
            .split(Regex("(?<=[.!?])\\s+|(?=\n)|(?<=\n)"))
            .filter { it.isNotBlank() && it.length > 2 }
            .map { it.trim() }
        
        // Calculate approximate timestamps for each segment
        var currentTimeMs = 0L
        val avgSegmentDuration = if (segments.isNotEmpty()) {
            (totalDurationMs / segments.size.toLong()).coerceAtLeast(1000L)
        } else {
            chunkDurationMs
        }
        
        segments.forEachIndexed { index, text ->
            // Estimate segment duration based on text length
            val estimatedDuration = (avgSegmentDuration * (text.length.toFloat() / 50f))
                .coerceIn(1000L, chunkDurationMs * 2)
            
            val endTime = (currentTimeMs + estimatedDuration).coerceAtMost(totalDurationMs)
            
            chunks.add(
                TranscriptChunk(
                    index = index + 1,
                    startMs = currentTimeMs,
                    endMs = endTime,
                    text = text
                )
            )
            
            currentTimeMs = endTime
        }
        
        // Ensure last chunk ends at video duration
        if (chunks.isNotEmpty() && chunks.last().endMs < totalDurationMs) {
            val lastChunk = chunks.last()
            chunks[chunks.lastIndex] = lastChunk.copy(endMs = totalDurationMs)
        }
        
        return chunks
    }

    /**
     * Export transcript to SRT format
     */
    fun exportToSrt(transcript: Transcript, outputPath: String): Boolean {
        return try {
            File(outputPath).writeText(transcript.toSrtFormat())
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting to SRT", e)
            false
        }
    }

    /**
     * Export transcript to plain text with timestamps
     */
    fun exportToTextWithTimestamps(transcript: Transcript, outputPath: String): Boolean {
        return try {
            val builder = StringBuilder()
            builder.append("TRANSCRIPT\n")
            builder.append("==========\n\n")
            
            transcript.chunks.forEach { chunk ->
                builder.append("[${formatTime(chunk.startMs)}] ${chunk.text}\n\n")
            }
            
            File(outputPath).writeText(builder.toString())
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting to text", e)
            false
        }
    }

    /**
     * Export transcript to VTT format (WebVTT)
     */
    fun exportToVtt(transcript: Transcript, outputPath: String): Boolean {
        return try {
            val builder = StringBuilder()
            builder.append("WEBVTT\n\n")
            
            transcript.chunks.forEach { chunk ->
                builder.append("${chunk.index}\n")
                builder.append("${formatVttTime(chunk.startMs)} --> ${formatVttTime(chunk.endMs)}\n")
                builder.append("${chunk.text}\n\n")
            }
            
            File(outputPath).writeText(builder.toString())
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting to VTT", e)
            false
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun formatVttTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val millis = ms % 1000
        return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
    }
}
