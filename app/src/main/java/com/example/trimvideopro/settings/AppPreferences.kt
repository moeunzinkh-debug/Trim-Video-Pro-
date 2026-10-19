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

    fun getGroqApiKey(context: Context): String {
        val store = preferences(context)
        val saved = store.getString("groq_api_key", "").orEmpty()
        val cleaned = GroqApiKeyInput.normalize(saved)
        // Repair previously saved wrapped keys without making the user re-enter them.
        // Invalid credentials remain editable in Settings and are never sent as headers.
        if (cleaned != saved && (cleaned.isEmpty() || GroqApiKeyInput.isValid(cleaned))) {
            store.edit().apply {
                if (cleaned.isEmpty()) remove("groq_api_key") else putString("groq_api_key", cleaned)
            }.apply()
        }
        return cleaned
    }

    fun saveGroqApiKey(context: Context, key: String) {
        val cleaned = GroqApiKeyInput.normalize(key)
        if (cleaned.isNotEmpty()) GroqApiKeyInput.requireValid(cleaned)
        preferences(context).edit().apply {
            if (cleaned.isEmpty()) remove("groq_api_key") else putString("groq_api_key", cleaned)
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
