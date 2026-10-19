package com.example.trimvideopro.processor

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.trimvideopro.model.AudioEffects
import com.example.trimvideopro.model.Transcript
import com.example.trimvideopro.model.TranscriptChunk
import com.example.trimvideopro.model.TranscriptLanguages
import com.example.trimvideopro.settings.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

object GroqProcessor {
    private const val MAX_UPLOAD_BYTES = 25L * 1024 * 1024
    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS).writeTimeout(180, TimeUnit.SECONDS)
        .callTimeout(240, TimeUnit.SECONDS).build()

    suspend fun transcribe(
        context: Context, uri: Uri, language: String, durationMs: Long,
        onProgress: (Float, String) -> Unit
    ): Transcript = withContext(Dispatchers.IO) {
        val key = apiKey(context)
        val name = displayName(context, uri)
        val extension = name.substringAfterLast('.', "").lowercase()
        require(extension in setOf("flac", "mp3", "mp4", "mpeg", "mpga", "m4a", "ogg", "wav", "webm")) {
            "Groq supports FLAC, MP3, MP4, MPEG, M4A, OGG, WAV and WebM. Extract to WAV for other formats."
        }
        val temp = File.createTempFile("groq_upload_", ".$extension", context.cacheDir)
        try {
            onProgress(0.1f, "Preparing media for Groq…")
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val size = input.read(buffer)
                        if (size < 0) break
                        total += size
                        require(total <= MAX_UPLOAD_BYTES) { "Groq upload limit is 25 MB. Extract audio or split into smaller parts first." }
                        output.write(buffer, 0, size)
                    }
                    require(total > 0) { "Selected media is empty." }
                }
            } ?: error("Cannot read selected media.")
            val form = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("model", "whisper-large-v3")
                .addFormDataPart("response_format", "verbose_json")
                .addFormDataPart("timestamp_granularities[]", "segment")
                .addFormDataPart("temperature", "0")
                .addFormDataPart("file", "media.$extension", temp.asRequestBody("application/octet-stream".toMediaType()))
            TranscriptLanguages.apiHint(language)?.let { form.addFormDataPart("language", it) }
            val request = Request.Builder().url("https://api.groq.com/openai/v1/audio/transcriptions")
                .header("Authorization", "Bearer $key").post(form.build()).build()
            onProgress(0.4f, "Transcribing with Groq Whisper Large v3…")
            val body = execute(request)
            onProgress(0.9f, "Reading segment timestamps…")
            parseTranscript(body, uri.toString(), language, durationMs).also { onProgress(1f, "Transcript ready") }
        } finally { temp.delete() }
    }

    internal fun parseTranscript(body: String, source: String, language: String, durationMs: Long): Transcript {
        val json = JSONObject(body)
        val segments = json.optJSONArray("segments") ?: JSONArray()
        val chunks = mutableListOf<TranscriptChunk>()
        for (index in 0 until segments.length()) {
            val segment = segments.getJSONObject(index)
            val text = segment.optString("text").trim()
            val start = timestamp(segment.optDouble("start", -1.0))
            val end = timestamp(segment.optDouble("end", -1.0))
            if (text.isNotBlank() && start >= 0 && end > start) {
                chunks.add(TranscriptChunk(chunks.size, start, end, text))
            }
        }
        if (chunks.isEmpty()) {
            val text = json.optString("text").trim()
            require(text.isNotBlank()) { "No speech was detected in this media." }
            val end = timestamp(json.optDouble("duration", durationMs / 1000.0)).coerceAtLeast(1)
            chunks.add(TranscriptChunk(0, 0, end, text))
        }
        val reportedLanguage = json.optString("language").trim()
        val normalized = TranscriptLanguages.normalize(reportedLanguage)
        val detected = if (normalized != TranscriptLanguages.AUTO) normalized
            else reportedLanguage.ifBlank { TranscriptLanguages.normalize(language) }
        return Transcript(source, chunks, detected)
    }

    private fun timestamp(seconds: Double): Long =
        if (seconds.isFinite() && seconds >= 0 && seconds < Long.MAX_VALUE / 1000.0)
            (seconds * 1000).roundToLong() else -1L

    /** Only the user's goal is sent. Recommendations do not claim to have listened to the media. */
    suspend fun recommend(context: Context, goal: String): Pair<AudioEffects, String> = withContext(Dispatchers.IO) {
        require(goal.isNotBlank()) { "Describe your desired sound first." }
        val system = """You are an audio engineer suggesting conservative settings for an Android audio editor.
            You have NOT heard the audio. Recommend settings based only on the user's goal.
            Return a JSON object only with keys: gainDb (-18..18), bassDb (-12..12), midDb (-12..12),
            trebleDb (-12..12), echoMix (0..0.6), echoDelayMs (50..800), compressor (boolean),
            noiseGate (boolean), fadeMs (0..3000), reason (one short sentence).
            Avoid echo for speech unless requested. Never claim to have analyzed the recording.""".trimIndent()
        val json = JSONObject().put("model", "llama-3.3-70b-versatile")
            .put("temperature", 0.2).put("max_tokens", 500)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", goal.take(500))))
        val request = Request.Builder().url("https://api.groq.com/openai/v1/chat/completions")
            .header("Authorization", "Bearer ${apiKey(context)}")
            .post(json.toString().toRequestBody("application/json".toMediaType())).build()
        val result = JSONObject(execute(request)).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")
        val settings = JSONObject(result)
        val effects = AudioEffects(
            gainDb = settings.optDouble("gainDb", 0.0).toFloat(),
            bassDb = settings.optDouble("bassDb", 0.0).toFloat(),
            midDb = settings.optDouble("midDb", 0.0).toFloat(),
            trebleDb = settings.optDouble("trebleDb", 0.0).toFloat(),
            echoMix = settings.optDouble("echoMix", 0.0).toFloat(),
            echoDelayMs = settings.optInt("echoDelayMs", 250),
            compressor = settings.optBoolean("compressor"), noiseGate = settings.optBoolean("noiseGate"),
            fadeMs = settings.optInt("fadeMs", 0)
        ).bounded()
        effects to settings.optString("reason", "Suggested settings are ready for review.").take(500)
    }

    private fun apiKey(context: Context): String = AppPreferences.getGroqApiKey(context).also {
        require(it.isNotBlank()) { "Add your Groq API key in Settings first." }
    }

    private fun execute(request: Request): String = client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            val detail = when (response.code) {
                401, 403 -> "Check your Groq API key in Settings."
                413 -> "Media is too large; extract or split audio first."
                429 -> "Rate limit reached. Try again later."
                else -> "Try again or use another media file."
            }
            error("Groq HTTP ${response.code}. $detail")
        }
        response.body?.string() ?: error("Groq returned an empty response.")
    }

    private fun displayName(context: Context, uri: Uri): String {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) return it.getString(0).orEmpty()
            }
        }
        return uri.lastPathSegment.orEmpty()
    }
}
