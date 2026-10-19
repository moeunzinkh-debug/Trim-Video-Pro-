package com.example.trimvideopro.settings

import android.content.Context
import com.example.trimvideopro.localization.AppLanguage

/**
 * Small, local settings store for values the user chooses in the app.
 *
 * User-supplied API keys are never written to the project or build configuration. Each is
 * kept only in the app's private preferences and can be removed from Settings at
 * any time. Keys are intentionally not included in the public UI state; only the
 * settings sheet reads them when opened. These preferences are excluded from backups.
 */
object AppPreferences {
    private const val PREFERENCES_NAME = "trim_video_pro_preferences"
    private const val GEMINI_API_KEY = "gemini_api_key"
    private const val APP_LANGUAGE = "app_language"

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun getSavedApiKey(context: Context): String =
        preferences(context).getString(GEMINI_API_KEY, "").orEmpty()

    fun saveApiKey(context: Context, apiKey: String) {
        val cleanedKey = apiKey.trim()
        preferences(context).edit().apply {
            if (cleanedKey.isEmpty()) {
                remove(GEMINI_API_KEY)
            } else {
                putString(GEMINI_API_KEY, cleanedKey)
            }
        }.apply()
    }

    fun getGroqApiKey(context: Context): String =
        preferences(context).getString("groq_api_key", "").orEmpty()

    fun saveGroqApiKey(context: Context, key: String) {
        preferences(context).edit().apply {
            if (key.isBlank()) remove("groq_api_key") else putString("groq_api_key", key.trim())
        }.apply()
    }

    fun getTranscriptProvider(context: Context): com.example.trimvideopro.model.TranscriptProvider =
        runCatching { com.example.trimvideopro.model.TranscriptProvider.valueOf(
            preferences(context).getString("transcript_provider", "GEMINI")!!
        ) }.getOrDefault(com.example.trimvideopro.model.TranscriptProvider.GEMINI)

    fun saveTranscriptProvider(context: Context, provider: com.example.trimvideopro.model.TranscriptProvider) {
        preferences(context).edit().putString("transcript_provider", provider.name).apply()
    }

    fun getLanguage(context: Context): AppLanguage? {
        val code = preferences(context).getString(APP_LANGUAGE, null) ?: return null
        return AppLanguage.entries.firstOrNull { it.code == code }
    }

    fun saveLanguage(context: Context, language: AppLanguage) {
        preferences(context).edit().putString(APP_LANGUAGE, language.code).apply()
    }
}
