package com.example.trimvideopro.processor

import android.content.Context
import android.net.Uri
import com.example.trimvideopro.model.AudioEffects
import com.example.trimvideopro.model.Transcript
import com.example.trimvideopro.model.TranscriptChunk
import com.example.trimvideopro.model.TranscriptLanguages
import com.example.trimvideopro.settings.AppPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object GroqProcessor {
    private const val MAX_UPLOAD_BYTES = 25L * 1024 * 1024
    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS).writeTimeout(180, TimeUnit.SECONDS)
        .callTimeout(240, TimeUnit.SECONDS).build()

    suspend fun transcribe(
        context: Context, uri: Uri, language: String, durationMs: Long,
        onPartialTranscript: (Transcript) -> Unit = {},
        onProgress: (Float, String) -> Unit
    ): Transcript = withContext(Dispatchers.IO) {
        val key = apiKey(context)
        onProgress(0.02f, "Extracting audio track · 16 kHz mono · VAD split boundaries…")
        val chunks = SpeechAudioProcessor.prepare(context, uri, durationMs) { progress ->
            onProgress(0.03f + 0.37f * progress, "Extracting audio and preparing VAD chunks… ${(progress * 100).toInt()}%")
        }
        try {
            require(chunks.isNotEmpty()) { "No audio activity detected in this media." }
            val combined = mutableListOf<TranscriptChunk>()
            val languages = mutableSetOf<String>()
            chunks.forEachIndexed { index, chunk ->
                currentCoroutineContext().ensureActive()
                require(chunk.file.length() <= MAX_UPLOAD_BYTES) { "Prepared audio exceeds Groq's upload limit." }
                onProgress(0.4f + 0.58f * index / chunks.size, "Groq Whisper · part ${index + 1}/${chunks.size} · continuing automatically…")
                try {
                    val form = MultipartBody.Builder().setType(MultipartBody.FORM)
                        .addFormDataPart("model", "whisper-large-v3")
                        .addFormDataPart("response_format", "verbose_json")
                        .addFormDataPart("timestamp_granularities[]", "segment")
                        .addFormDataPart("temperature", "0")
                        .addFormDataPart("file", "audio.wav", chunk.file.asRequestBody("audio/wav".toMediaType()))
                    TranscriptLanguages.apiHint(language)?.let { form.addFormDataPart("language", it) }
                    val request = Request.Builder().url("https://api.groq.com/openai/v1/audio/transcriptions")
                        .header("Authorization", "Bearer $key").post(form.build()).build()
                    val transcript = parseTranscript(execute(request), uri.toString(), language, chunk.durationMs)
                    languages.add(transcript.language)
                    transcript.chunks.forEach { segment ->
                        val start = segment.startMs.coerceIn(0, chunk.durationMs)
                        val end = segment.endMs.coerceIn(start, chunk.durationMs)
                        if (end > start) combined.add(segment.copy(index = combined.size,
                            startMs = start + chunk.sourceStartMs, endMs = end + chunk.sourceStartMs))
                    }
                    if (combined.isNotEmpty()) onPartialTranscript(Transcript(uri.toString(), combined.toList(),
                        if (languages.size == 1) languages.first() else "multilingual"))
                } catch (_: NoSpeechDetectedException) {
                    // Silent parts must not stop transcription of the remaining recording.
                } catch (e: CancellationException) { throw e } catch (e: Exception) {
                    throw IllegalStateException("Groq part ${index + 1}/${chunks.size} failed. Completed text remains available for export. ${e.message}", e)
                } finally { chunk.file.delete() }
            }
            require(combined.isNotEmpty()) { "No speech was detected in this media." }
            onProgress(1f, "All ${chunks.size} audio parts transcribed")
            Transcript(uri.toString(), combined, if (languages.size == 1) languages.first() else "multilingual")
        } finally { chunks.forEach { it.file.delete() } }
    }

    private class NoSpeechDetectedException : IllegalArgumentException("No speech was detected in this media.")

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
            if (text.isBlank()) throw NoSpeechDetectedException()
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

    private class GroqHttpException(val code: Int, val retrySeconds: Long?, message: String) : IOException(message)

    /** Cancel the actual network call when the source changes; retry transient failures only. */
    private suspend fun execute(request: Request): String {
        repeat(3) { attempt ->
            try { return awaitResponse(request) } catch (e: CancellationException) { throw e } catch (e: IOException) {
                val retryable = e !is GroqHttpException || e.code == 429 || e.code in 500..599
                if (!retryable || attempt == 2) throw e
                val seconds = ((e as? GroqHttpException)?.retrySeconds ?: (2L shl attempt)).coerceIn(1, 60)
                delay(seconds * 1000)
            }
        }
        error("Groq request failed.")
    }

    private suspend fun awaitResponse(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        if (!response.isSuccessful) {
                            val detail = when (response.code) {
                                401, 403 -> "Check your Groq API key in Settings."
                                413 -> "Prepared audio was rejected as too large."
                                429 -> "Rate limit reached. Please try again later."
                                else -> "Provider temporarily unavailable or unsupported media."
                            }
                            throw GroqHttpException(response.code, response.header("Retry-After")?.toLongOrNull(), "Groq HTTP ${response.code}. $detail")
                        }
                        val body = response.body?.string() ?: error("Groq returned an empty response.")
                        if (continuation.isActive) continuation.resume(body)
                    } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
                }
            }
        })
    }
}
