package com.example.trimvideopro.processor

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.trimvideopro.model.Transcript
import com.example.trimvideopro.model.TranscriptChunk
import com.example.trimvideopro.model.TranscriptConfig
import com.example.trimvideopro.model.TranscriptLanguages
import com.example.trimvideopro.settings.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Handles media transcription and transcript export. */
object TranscriptProcessor {
    private const val TAG = "TranscriptProcessor"
    private const val GEMINI_MODEL = "gemini-2.0-flash"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * Transcribes either a video or an audio document selected by the user.
     *
     * The original implementation extracted an audio track and then sent only a
     * prompt to Gemini, so Gemini never received the media. Sending the selected
     * media as inline data preserves audio-only files and also lets Gemini inspect
     * the audio track of videos.
     */
    suspend fun generateTranscript(
        context: Context,
        videoUri: Uri,
        config: TranscriptConfig = TranscriptConfig(),
        onProgress: (Float, String) -> Unit
    ): Transcript? = withContext(Dispatchers.IO) {
        try {
            val apiKey = getApiKey(context)
            if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY_HERE") {
                Log.e(TAG, "Gemini API key is not configured")
                return@withContext null
            }

            val mimeType = resolveMediaMimeType(context, videoUri)
            if (!mimeType.startsWith("audio/") && !mimeType.startsWith("video/")) {
                Log.e(TAG, "Unsupported transcript media type: $mimeType")
                return@withContext null
            }

            onProgress(0.12f, "Reading media...")
            val mediaBytes = context.contentResolver.openInputStream(videoUri)?.use { it.readBytes() }
                ?: return@withContext null
            if (mediaBytes.isEmpty()) {
                Log.e(TAG, "Selected media is empty")
                return@withContext null
            }

            onProgress(0.35f, "Sending media to Gemini AI...")
            val transcriptText = sendToGemini(
                apiKey = apiKey,
                mediaBytes = mediaBytes,
                mimeType = mimeType,
                language = config.language
            ) ?: return@withContext null

            onProgress(0.82f, "Adding transcript timestamps...")
            val durationMs = getMediaDuration(context, videoUri)
            val chunks = parseTranscriptWithTimestamps(
                transcriptText = transcriptText,
                totalDurationMs = durationMs,
                chunkDurationMs = config.chunkDurationMs
            )
            if (chunks.isEmpty()) return@withContext null

            onProgress(1.0f, "Transcript generated successfully!")
            Transcript(
                videoUri = videoUri.toString(),
                chunks = chunks,
                language = normalizeLanguage(config.language),
                generatedAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error generating transcript", e)
            null
        }
    }

    private fun sendToGemini(
        apiKey: String,
        mediaBytes: ByteArray,
        mimeType: String,
        language: String
    ): String? {
        return try {
            val encodedMedia = Base64.encodeToString(mediaBytes, Base64.NO_WRAP)
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().put(
                    JSONObject().put("parts", JSONArray()
                        .put(JSONObject().put("text", buildPrompt(language)))
                        .put(
                            JSONObject().put(
                                "inline_data",
                                JSONObject()
                                    .put("mime_type", mimeType)
                                    .put("data", encodedMedia)
                            )
                        )
                    )
                ))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                    put("topK", 32)
                    put("topP", 0.95)
                    put("maxOutputTokens", 8192)
                })
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .writeTimeout(180, TimeUnit.SECONDS)
                .build()

            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:generateContent"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("key", apiKey)
                .build()
            val request = Request.Builder()
                .url(endpoint)
                .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    // Do not log the request URL: it contains the user's API key.
                    Log.e(TAG, "Gemini HTTP ${response.code}: $body")
                    return null
                }
                parseGeminiText(body)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini API", e)
            null
        }
    }

    private fun parseGeminiText(body: String): String? {
        return try {
            val parts = JSONObject(body)
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")

            val text = buildString {
                if (parts != null) {
                    for (index in 0 until parts.length()) {
                        val partText = parts.optJSONObject(index)?.optString("text").orEmpty()
                        if (partText.isNotBlank()) {
                            if (isNotEmpty()) append('\n')
                            append(partText)
                        }
                    }
                }
            }.trim()
            text.ifBlank { null }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Gemini response", e)
            null
        }
    }

    /** Saved keys take precedence over the optional build-time .env key. */
    private fun getApiKey(context: Context): String {
        val savedKey = AppPreferences.getSavedApiKey(context)
        if (savedKey.isNotBlank()) return savedKey

        return try {
            com.example.BuildConfig::class.java.getField("GEMINI_API_KEY")
                .get(null) as? String ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "GEMINI_API_KEY not found in BuildConfig")
            ""
        }
    }

    private fun resolveMediaMimeType(context: Context, uri: Uri): String {
        val resolverMime = context.contentResolver.getType(uri)?.lowercase(Locale.US)
        if (resolverMime?.startsWith("audio/") == true || resolverMime?.startsWith("video/") == true) {
            return resolverMime
        }

        val extension = uri.lastPathSegment
            ?.substringAfterLast('.', "")
            ?.lowercase(Locale.US)
        return when (extension) {
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "ogg", "oga" -> "audio/ogg"
            "opus" -> "audio/opus"
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "3gp" -> "video/3gpp"
            "mov" -> "video/quicktime"
            else -> "application/octet-stream"
        }
    }

    private fun getMediaDuration(context: Context, uri: Uri): Long {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "Could not read media duration", e)
            0L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun buildPrompt(language: String): String {
        val sourceLanguage = normalizeLanguage(language)
        val languageInstruction = if (sourceLanguage == TranscriptLanguages.AUTO)
            "Automatically detect the spoken source language from the audio across all supported languages."
        else "The source language is ${TranscriptLanguages.displayName(sourceLanguage)} ($sourceLanguage). Transcribe in that language."

        return """
            Transcribe all spoken words in the attached media.
            $languageInstruction
            Do not translate the speech. Keep the original wording and script.
            Include speech from every speaker and do not omit partial sentences.
            Ignore music and non-speech sounds unless they are necessary for context.
            Return only the transcript text, with no headings, commentary, markdown, or explanation.
        """.trimIndent()
    }

    private fun normalizeLanguage(language: String): String = TranscriptLanguages.normalize(language)

    /** Creates useful approximate timestamps because the REST response is plain text. */
    private fun parseTranscriptWithTimestamps(
        transcriptText: String,
        totalDurationMs: Long,
        chunkDurationMs: Long
    ): List<TranscriptChunk> {
        val cleanText = transcriptText
            .replace(Regex("(?s)```(?:text)?\\s*|```"), "")
            .trim()
        if (cleanText.isBlank()) return emptyList()

        val segments = cleanText
            .split(Regex("(?<=[.!?])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.length > 1 }
        if (segments.isEmpty()) return emptyList()

        val safeChunkDuration = chunkDurationMs.coerceAtLeast(1000L)
        val duration = totalDurationMs.takeIf { it > 0L }
            ?: (safeChunkDuration * segments.size)
        val totalCharacters = segments.sumOf { it.length }.coerceAtLeast(1)
        var currentTime = 0L

        return segments.mapIndexed { index, text ->
            val remainingChunks = segments.size - index
            val remainingTime = (duration - currentTime).coerceAtLeast(remainingChunks * 500L)
            val estimated = if (index == segments.lastIndex) {
                remainingTime
            } else {
                (duration.toDouble() * text.length / totalCharacters)
                    .toLong()
                    .coerceAtLeast(500L)
                    .coerceAtMost((remainingTime - (remainingChunks - 1) * 500L).coerceAtLeast(500L))
            }
            val endTime = (currentTime + estimated).coerceAtMost(duration)
            val chunk = TranscriptChunk(
                index = index + 1,
                startMs = currentTime,
                endMs = endTime.coerceAtLeast(currentTime + 1L),
                text = text
            )
            currentTime = endTime
            chunk
        }
    }

    fun exportToSrt(transcript: Transcript, outputPath: String): Boolean {
        return try {
            File(outputPath).writeText(transcript.toSrtFormat())
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting to SRT", e)
            false
        }
    }

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
        return String.format(
            Locale.US,
            "%02d:%02d:%02d",
            totalSeconds / 3600,
            (totalSeconds % 3600) / 60,
            totalSeconds % 60
        )
    }

    private fun formatVttTime(ms: Long): String {
        val totalSeconds = ms / 1000
        return String.format(
            Locale.US,
            "%02d:%02d:%02d.%03d",
            totalSeconds / 3600,
            (totalSeconds % 3600) / 60,
            totalSeconds % 60,
            ms % 1000
        )
    }
}
